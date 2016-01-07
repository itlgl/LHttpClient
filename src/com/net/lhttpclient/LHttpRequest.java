package com.net.lhttpclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;

public class LHttpRequest {
	protected static final int BUFFER_SIZE = 4096;
	
	private final AbstractHttpClient client;
    private final HttpContext context;
    private final HttpUriRequest request;
    private int executionCount;
    
    public LHttpRequest(AbstractHttpClient client, HttpContext context, HttpUriRequest request) {
    	this.client = client;
        this.context = context;
        this.request = request;
    }
    
    public LHttpResponse request() {
    	LHttpResponse lHttpResponse = new LHttpResponse();
    	HttpResponse httpResponse = null;
    	try {
    		httpResponse = makeRequestWithRetries();
		} catch (IOException e) {
			LogUtil.w("makeRequestWithRetries exception", e);
			lHttpResponse.setSuccess(false);
			lHttpResponse.setThrowable(e);
		}
    	if(httpResponse != null) {
    		byte[] responseBody;
        	StatusLine status = httpResponse.getStatusLine();
        	try {
				responseBody = getResponseData(httpResponse.getEntity());
				if (status.getStatusCode() >= 300) {
					lHttpResponse.setSuccess(false);
					lHttpResponse.setStatusCode(status.getStatusCode());
					lHttpResponse.setResponse(responseBody);
					lHttpResponse.setThrowable(new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()));
				} else {
					lHttpResponse.setSuccess(true);
					lHttpResponse.setStatusCode(status.getStatusCode());
					lHttpResponse.setResponse(responseBody);
				}
			} catch (IOException e) {
				LogUtil.w("getResponseData exception", e);
				lHttpResponse.setSuccess(false);
				lHttpResponse.setThrowable(e);
			}
    	}
    	
    	return lHttpResponse;
    }
    
    /**
     * Returns byte array of response HttpEntity contents
     *
     * @param entity can be null
     * @return response entity body or null
     * @throws java.io.IOException if reading entity or creating byte array failed
     */
    byte[] getResponseData(HttpEntity entity) throws IOException {
        byte[] responseBody = null;
        if (entity != null) {
            InputStream instream = entity.getContent();
            if (instream != null) {
                long contentLength = entity.getContentLength();
                if (contentLength > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("HTTP entity too large to be buffered in memory");
                }
                int buffersize = (contentLength <= 0) ? BUFFER_SIZE : (int) contentLength;
                try {
                    ByteArrayBuffer buffer = new ByteArrayBuffer(buffersize);
                    try {
                        byte[] tmp = new byte[BUFFER_SIZE];
                        int l, count = 0;
                        // do not send messages if request has been cancelled
                        while ((l = instream.read(tmp)) != -1 && !Thread.currentThread().isInterrupted()) {
                            count += l;
                            buffer.append(tmp, 0, l);
                            // sendProgressMessage(count, (int) (contentLength <= 0 ? 1 : contentLength));
                        }
                    } finally {
                        LHttpClient.silentCloseInputStream(instream);
                        LHttpClient.endEntityViaReflection(entity);
                    }
                    responseBody = buffer.toByteArray();
                } catch (OutOfMemoryError e) {
                    System.gc();
                    throw new IOException("File too large to fit into available memory");
                }
            }
        }
        return responseBody;
    }
    
    private HttpResponse makeRequest() throws IOException {
    	return client.execute(request, context);
    }
    
    private HttpResponse makeRequestWithRetries() throws IOException {
        boolean retry = true;
        IOException cause = null;
        HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();
        try {
            while (retry) {
                try {
                    return makeRequest();
                } catch (UnknownHostException e) {
                    // switching between WI-FI and mobile data networks can cause a retry which then results in an UnknownHostException
                    // while the WI-FI is initialising. The retry logic will be invoked here, if this is NOT the first retry
                    // (to assist in genuine cases of unknown host) which seems better than outright failure
                    cause = new IOException("UnknownHostException exception: " + e.getMessage());
                    retry = (executionCount > 0) && retryHandler.retryRequest(cause, ++executionCount, context);
                } catch (NullPointerException e) {
                    // there's a bug in HttpClient 4.0.x that on some occasions causes
                    // DefaultRequestExecutor to throw an NPE, see
                    // http://code.google.com/p/android/issues/detail?id=5255
                    cause = new IOException("NPE in HttpClient: " + e.getMessage());
                    retry = retryHandler.retryRequest(cause, ++executionCount, context);
                } catch (IOException e) {
                    cause = e;
                    retry = retryHandler.retryRequest(cause, ++executionCount, context);
                }
            }
        } catch (Exception e) {
            // catch anything else to ensure failure message is propagated
            // Log.e("AsyncHttpRequest", "Unhandled exception origin cause", e);
            cause = new IOException("Unhandled exception: " + e.getMessage());
        }

        // cleaned up to throw IOException
        throw (cause);
    }
}
