package com.net.lhttpclient;

import android.util.Log;

/** 
 * 供LHttpClient打印log使用的工具类，不对外公开
 * @author lgl
 * 
 */
class LogUtil {
	public static final String LOG_TAG = "LHttpClient";
	
	public static final void d(String msg) {
		if(LHttpClient.DEBUG) {
			Log.d(LOG_TAG, msg);
		}
	}
	
	public static final void e(String msg, Throwable thr) {
		if(LHttpClient.DEBUG) {
			Log.e(LOG_TAG, msg, thr);
		}
	}
	
	public static final void w(String msg, Throwable thr) {
		if(LHttpClient.DEBUG) {
			Log.w(LOG_TAG, msg, thr);
		}
	}
}
