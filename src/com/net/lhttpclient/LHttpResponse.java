package com.net.lhttpclient;

public class LHttpResponse {
	private boolean success;
	private byte[] response;
	private int statusCode;
	private Throwable throwable;

	/**
	 * 将byte数组转换为字符串，如果发生异常返回null
	 * @param charsetName 比如utf-8、gbk等等
	 * @return 字符串，如果发生异常返回null
	 */
	public String getTextResponse(String charsetName) {
		if(response == null) {
			return null;
		}
		try {
			return new String(response, charsetName);
		} catch(Exception e) {
			LogUtil.e("getTextResponse", e);
		}
		return null;
	}
	
	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public byte[] getResponse() {
		return response;
	}

	public void setResponse(byte[] response) {
		this.response = response;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public void setThrowable(Throwable throwable) {
		this.throwable = throwable;
	}

}
