package com.hb.lhttpclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncBasicHttpContext;

/**
 * code from AsyncHttpClient(https://github.com/loopj/android-async-http)
 * 
 * @author ligl01
 * 
 */
public class LHttpClient {
	public static boolean DEBUG = true;
	
	public static final String HEADER_CONTENT_TYPE = "Content-Type";
	public static final String HEADER_CONTENT_RANGE = "Content-Range";
	public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	public static final String ENCODING_GZIP = "gzip";

	public static final int DEFAULT_MAX_CONNECTIONS = 10;
	public static final int DEFAULT_TIMEOUT = 10 * 1000;
	public static final int DEFAULT_SOCKET_TIMEOUT = 10 * 1000;
	public static final int DEFAULT_MAX_RETRIES = 5;
	public static final int DEFAULT_RETRY_SLEEP_TIME_MILLIS = 1500;
	public static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;

	private int maxConnections = DEFAULT_MAX_CONNECTIONS;
	private int connectTimeout = DEFAULT_TIMEOUT;
	private int responseTimeout = DEFAULT_SOCKET_TIMEOUT;

	private final DefaultHttpClient httpClient;
	private final HttpContext httpContext;
	private final Map<String, String> clientHeaderMap;

	public LHttpClient() {
		this(80, 443);
	}

	public LHttpClient(int httpPort, int httpsPort) {
		BasicHttpParams httpParams = new BasicHttpParams();

		ConnManagerParams.setTimeout(httpParams, connectTimeout);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams,
				new ConnPerRouteBean(maxConnections));
		ConnManagerParams.setMaxTotalConnections(httpParams,
				DEFAULT_MAX_CONNECTIONS);

		HttpConnectionParams.setSoTimeout(httpParams, responseTimeout);
		HttpConnectionParams.setConnectionTimeout(httpParams, connectTimeout);
		HttpConnectionParams.setTcpNoDelay(httpParams, true);
		HttpConnectionParams.setSocketBufferSize(httpParams,
				DEFAULT_SOCKET_BUFFER_SIZE);

		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), httpPort));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory
				.getSocketFactory(), httpsPort));
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
				httpParams, schemeRegistry);

		httpContext = new SyncBasicHttpContext(new BasicHttpContext());
		httpClient = new DefaultHttpClient(cm, httpParams);
		httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
			public void process(HttpRequest request, HttpContext context) {
				if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
					request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
				}
				for (String header : clientHeaderMap.keySet()) {
					request.addHeader(header, clientHeaderMap.get(header));
				}
			}
		});

		httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(HttpResponse response, HttpContext context) {
				final HttpEntity entity = response.getEntity();
				final Header encoding = entity.getContentEncoding();
				if (encoding != null) {
					for (HeaderElement element : encoding.getElements()) {
						if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
							response.setEntity(new InflatingEntity(response
									.getEntity()));
							break;
						}
					}
				}
			}
		});

		httpClient.setHttpRequestRetryHandler(new RetryHandler(
				DEFAULT_MAX_RETRIES, DEFAULT_RETRY_SLEEP_TIME_MILLIS));

		clientHeaderMap = new HashMap<String, String>();
	}

	/**
	 * Get the underlying HttpClient instance. This is useful for setting
	 * additional fine-grained settings for requests by accessing the client's
	 * ConnectionManager, HttpParams and SchemeRegistry.
	 */
	public HttpClient getHttpClient() {
		return this.httpClient;
	}

	/**
	 * Get the underlying HttpContext instance. This is useful for getting and
	 * setting fine-grained settings for requests by accessing the context's
	 * attributes such as the CookieStore.
	 * 
	 * @return underlying HttpContext instance
	 */
	public HttpContext getHttpContext() {
		return this.httpContext;
	}

	/**
	 * Sets an optional CookieStore to use when making requests
	 * 
	 * @param cookieStore
	 *            The CookieStore implementation to use, usually an instance of
	 *            PersistentCookieStore
	 */
	public void setCookieStore(CookieStore cookieStore) {
		httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
	}

	/**
	 * Returns current connection timeout limit (milliseconds). By default, this
	 * is set to 10 seconds.
	 * 
	 * @return Connection timeout limit in milliseconds
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * Set connection timeout limit (milliseconds). By default, this is set to
	 * 10 seconds.
	 * 
	 * @param value
	 *            Connection timeout in milliseconds, minimal value is 1000 (1
	 *            second).
	 */
	public void setConnectTimeout(int value) {
		connectTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
		final HttpParams httpParams = httpClient.getParams();
		ConnManagerParams.setTimeout(httpParams, connectTimeout);
		HttpConnectionParams.setConnectionTimeout(httpParams, connectTimeout);
	}

	/**
	 * Returns current response timeout limit (milliseconds). By default, this
	 * is set to 10 seconds.
	 * 
	 * @return Response timeout limit in milliseconds
	 */
	public int getResponseTimeout() {
		return responseTimeout;
	}

	/**
	 * Set response timeout limit (milliseconds). By default, this is set to 10
	 * seconds.
	 * 
	 * @param value
	 *            Response timeout in milliseconds, minimal value is 1000 (1
	 *            second).
	 */
	public void setResponseTimeout(int value) {
		responseTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
		final HttpParams httpParams = httpClient.getParams();
		HttpConnectionParams.setSoTimeout(httpParams, responseTimeout);
	}

	/**
	 * Sets the Proxy by it's hostname and port
	 * 
	 * @param hostname
	 *            the hostname (IP or DNS name)
	 * @param port
	 *            the port number. -1 indicates the scheme default port.
	 */
	public void setProxy(String hostname, int port) {
		final HttpHost proxy = new HttpHost(hostname, port);
		final HttpParams httpParams = this.httpClient.getParams();
		httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}

	/**
	 * Sets the Proxy by it's hostname,port,username and password
	 * 
	 * @param hostname
	 *            the hostname (IP or DNS name)
	 * @param port
	 *            the port number. -1 indicates the scheme default port.
	 * @param username
	 *            the username
	 * @param password
	 *            the password
	 */
	public void setProxy(String hostname, int port, String username,
			String password) {
		httpClient.getCredentialsProvider().setCredentials(
				new AuthScope(hostname, port),
				new UsernamePasswordCredentials(username, password));
		final HttpHost proxy = new HttpHost(hostname, port);
		final HttpParams httpParams = this.httpClient.getParams();
		httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}

	/**
	 * Sets the SSLSocketFactory to user when making requests. By default, a
	 * new, default SSLSocketFactory is used.
	 * 
	 * @param sslSocketFactory
	 *            the socket factory to use for https requests.
	 */
	public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
		this.httpClient.getConnectionManager().getSchemeRegistry()
				.register(new Scheme("https", sslSocketFactory, 443));
	}

	/**
	 * Sets the maximum number of retries and timeout for a particular Request.
	 * 
	 * @param retries
	 *            maximum number of retries per request
	 * @param timeout
	 *            sleep between retries in milliseconds
	 */
	public void setMaxRetriesAndTimeout(int retries, int timeout) {
		this.httpClient.setHttpRequestRetryHandler(new RetryHandler(retries,
				timeout));
	}

	/**
	 * Will, before sending, remove all headers currently present in
	 * AsyncHttpClient instance, which applies on all requests this client makes
	 */
	public void removeAllHeaders() {
		clientHeaderMap.clear();
	}

	/**
	 * Sets headers that will be added to all requests this client makes (before
	 * sending).
	 * 
	 * @param header
	 *            the name of the header
	 * @param value
	 *            the contents of the header
	 */
	public void addHeader(String header, String value) {
		clientHeaderMap.put(header, value);
	}

	/**
	 * Remove header from all requests this client makes (before sending).
	 * 
	 * @param header
	 *            the name of the header
	 */
	public void removeHeader(String header) {
		clientHeaderMap.remove(header);
	}

//	/**
//	 * Sets basic authentication for the request. Uses AuthScope.ANY. This is
//	 * the same as setBasicAuth('username','password',AuthScope.ANY)
//	 * 
//	 * @param username
//	 *            Basic Auth username
//	 * @param password
//	 *            Basic Auth password
//	 */
//	public void setBasicAuth(String username, String password) {
//		setBasicAuth(username, password, false);
//	}
//
//	/**
//	 * Sets basic authentication for the request. Uses AuthScope.ANY. This is
//	 * the same as setBasicAuth('username','password',AuthScope.ANY)
//	 * 
//	 * @param username
//	 *            Basic Auth username
//	 * @param password
//	 *            Basic Auth password
//	 * @param preemtive
//	 *            sets authorization in preemtive manner
//	 */
//	public void setBasicAuth(String username, String password, boolean preemtive) {
//		setBasicAuth(username, password, null, preemtive);
//	}
//
//	/**
//	 * Sets basic authentication for the request. You should pass in your
//	 * AuthScope for security. It should be like this
//	 * setBasicAuth("username","password", new
//	 * AuthScope("host",port,AuthScope.ANY_REALM))
//	 * 
//	 * @param username
//	 *            Basic Auth username
//	 * @param password
//	 *            Basic Auth password
//	 * @param scope
//	 *            - an AuthScope object
//	 */
//	public void setBasicAuth(String username, String password, AuthScope scope) {
//		setBasicAuth(username, password, scope, false);
//	}
//
//	/**
//	 * Sets basic authentication for the request. You should pass in your
//	 * AuthScope for security. It should be like this
//	 * setBasicAuth("username","password", new
//	 * AuthScope("host",port,AuthScope.ANY_REALM))
//	 * 
//	 * @param username
//	 *            Basic Auth username
//	 * @param password
//	 *            Basic Auth password
//	 * @param scope
//	 *            an AuthScope object
//	 * @param preemtive
//	 *            sets authorization in preemtive manner
//	 */
//	public void setBasicAuth(String username, String password, AuthScope scope,
//			boolean preemtive) {
//		UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
//				username, password);
//		setCredentials(scope, credentials);
//		setAuthenticationPreemptive(preemtive);
//	}
//
//	public void setCredentials(AuthScope authScope, Credentials credentials) {
//		if (credentials == null) {
//			LogUtil.d("Provided credentials are null, not setting");
//			return;
//		}
//		this.httpClient.getCredentialsProvider().setCredentials(
//				authScope == null ? AuthScope.ANY : authScope, credentials);
//	}
//
//	/**
//	 * Sets HttpRequestInterceptor which handles authorization in preemtive way,
//	 * as workaround you can use call
//	 * `AsyncHttpClient.addHeader("Authorization",
//	 * "Basic base64OfUsernameAndPassword==")`
//	 * 
//	 * @param isPreemtive
//	 *            whether the authorization is processed in preemtive way
//	 */
//	public void setAuthenticationPreemptive(boolean isPreemtive) {
//		if (isPreemtive) {
//			httpClient.addRequestInterceptor(
//					new PreemtiveAuthorizationHttpRequestInterceptor(), 0);
//		} else {
//			httpClient
//					.removeRequestInterceptorByClass(PreemtiveAuthorizationHttpRequestInterceptor.class);
//		}
//	}

	public LHttpResponse get(String url) {
		return get(url, null);
	}

	public LHttpResponse get(String url, RequestParams params) {
		return sendRequest(httpClient, httpContext, new HttpGet(
				getUrlWithQueryString(url, params)), null);
	}

	public LHttpResponse post(String url) {
		return post(url, null);
	}

	public LHttpResponse post(String url, RequestParams params) {
		return post(url, paramsToEntity(params), null);
	}

	public LHttpResponse post(String url, HttpEntity entity, String contentType) {
		return sendRequest(httpClient, httpContext,
				addEntityToRequestBase(new HttpPost(url), entity), contentType);
	}

	private LHttpResponse sendRequest(DefaultHttpClient client,
			HttpContext httpContext, HttpUriRequest uriRequest,
			String contentType) {
		LHttpRequest request = new LHttpRequest(httpClient, httpContext, uriRequest);
		return request.request();
	}

	private HttpEntityEnclosingRequestBase addEntityToRequestBase(
			HttpEntityEnclosingRequestBase requestBase, HttpEntity entity) {
		if (entity != null) {
			requestBase.setEntity(entity);
		}

		return requestBase;
	}

	private HttpEntity paramsToEntity(RequestParams params) {
		HttpEntity entity = null;

		if (params != null) {
			entity = params.getEntity();
		}

		return entity;
	}

	private String getUrlWithQueryString(String url, RequestParams params) {
		if (params != null) {
			String paramString = params.getParamString();
			url += "?" + paramString;
		}

		return url;
	}

	/**
     * This horrible hack is required on Android, due to implementation of BasicManagedEntity, which
     * doesn't chain call consumeContent on underlying wrapped HttpEntity
     *
     * @param entity HttpEntity, may be null
     */
    public static void endEntityViaReflection(HttpEntity entity) {
        if (entity instanceof HttpEntityWrapper) {
            try {
                Field f = null;
                Field[] fields = HttpEntityWrapper.class.getDeclaredFields();
                for (Field ff : fields) {
                    if (ff.getName().equals("wrappedEntity")) {
                        f = ff;
                        break;
                    }
                }
                if (f != null) {
                    f.setAccessible(true);
                    HttpEntity wrapped = (HttpEntity) f.get(entity);
                    if (wrapped != null) {
                        wrapped.consumeContent();
                    }
                }
            } catch (Throwable t) {
                LogUtil.e("wrappedEntity consume", t);
            }
        }
    }
	
	/**
	 * Checks the InputStream if it contains GZIP compressed data
	 * 
	 * @param inputStream
	 *            InputStream to be checked
	 * @return true or false if the stream contains GZIP compressed data
	 * @throws java.io.IOException
	 */
	public static boolean isInputStreamGZIPCompressed(
			final PushbackInputStream inputStream) throws IOException {
		if (inputStream == null)
			return false;

		byte[] signature = new byte[2];
		int readStatus = inputStream.read(signature);
		inputStream.unread(signature);
		int streamHeader = ((int) signature[0] & 0xff)
				| ((signature[1] << 8) & 0xff00);
		return readStatus == 2 && GZIPInputStream.GZIP_MAGIC == streamHeader;
	}

	/**
	 * A utility function to close an input stream without raising an exception.
	 * 
	 * @param is
	 *            input stream to close safely
	 */
	public static void silentCloseInputStream(InputStream is) {
		try {
			if (is != null) {
				is.close();
			}
		} catch (IOException e) {
			LogUtil.w("Cannot close input stream", e);
		}
	}

	/**
	 * Enclosing entity to hold stream of gzip decoded data for accessing
	 * HttpEntity contents
	 */
	private static class InflatingEntity extends HttpEntityWrapper {

		public InflatingEntity(HttpEntity wrapped) {
			super(wrapped);
		}

		InputStream wrappedStream;
		PushbackInputStream pushbackStream;
		GZIPInputStream gzippedStream;

		@Override
		public InputStream getContent() throws IOException {
			wrappedStream = wrappedEntity.getContent();
			pushbackStream = new PushbackInputStream(wrappedStream, 2);
			if (isInputStreamGZIPCompressed(pushbackStream)) {
				gzippedStream = new GZIPInputStream(pushbackStream);
				return gzippedStream;
			} else {
				return pushbackStream;
			}
		}

		@Override
		public long getContentLength() {
			return wrappedEntity == null ? 0 : wrappedEntity.getContentLength();
		}

		@Override
		public void consumeContent() throws IOException {
			LHttpClient.silentCloseInputStream(wrappedStream);
			LHttpClient.silentCloseInputStream(pushbackStream);
			LHttpClient.silentCloseInputStream(gzippedStream);
			super.consumeContent();
		}
	}
}
