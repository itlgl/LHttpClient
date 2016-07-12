DEPRECATED
-------------
由于android6.0已经不包含HttpClient，需要另外自己将兼容的HttpClient包导入，故不再推荐使用HttpClient进行网络访问。可以改用[OKHttp](https://github.com/square/okhttp)。


# LHttpClient
修改[AsyncHttpClient](https://github.com/loopj/android-async-http)的代码，修改一个同步的网络请求包装出来，设置和请求的参数方法和原来相同，在连续多个请求的时候会比原本回调的方式好用。
增加了一个统一打印的LogUtil类，可以通过LHttpClient.DEBUG设置。


简单调用方法
-----------
```java
// LHttpClient可以声明成单例保持服务器的session连接
final LHttpClient lHttpClient = new LHttpClient();

new Thread() {
	
	@Override
	public void run() {
		LHttpResponse response = lHttpClient.post("http://blog.csdn.net/lgl1170860350/article/details/40304523");
		if(response.isSuccess()) {
			Log.i("LHttpClient", "访问网络成功");
			Log.i("LHttpClient", "statusCode=" + response.getStatusCode());
			Log.i("LHttpClient", "response text=" + response.getTextResponse("utf-8"));
		} else {
			Log.i("LHttpClient", "访问网络失败");
			Log.i("LHttpClient", "statusCode=" + response.getStatusCode());
			Log.i("LHttpClient", "response text=" + response.getTextResponse("utf-8"));
			Log.i("LHttpClient", "throwable=" + response.getTextResponse("utf-8"));
		}
	}
}.start();
```

post上送流的方式请求
-----------
```java
// LHttpClient可以声明成单例保持服务器的session连接
final LHttpClient lHttpClient = new LHttpClient();

new Thread() {
	
	@Override
	public void run() {
		StringEntity entity = null;
        String data = "hello,world!";
        try {
        	entity = new StringEntity(data, "UTF-8");
        } catch (Exception e) {
        	e.printStackTrace();
        }
        entity.setContentEncoding("UTF-8");
        entity.setContentType("application/json");//设置为 json数据
        // lHttpClient.post(url, entity, contentType);
        // 现在contentType在代码中并没有起作用，可以不填写
        LHttpResponse response = lHttpClient.post("http://blog.csdn.net/lgl1170860350/article/details/40304523", entity, null);
		if(response.isSuccess()) {
			Log.i("LHttpClient", "访问网络成功");
			Log.i("LHttpClient", "statusCode=" + response.getStatusCode());
			Log.i("LHttpClient", "response text=" + response.getTextResponse("utf-8"));
		} else {
			Log.i("LHttpClient", "访问网络失败");
			Log.i("LHttpClient", "statusCode=" + response.getStatusCode());
			Log.i("LHttpClient", "response text=" + response.getTextResponse("utf-8"));
			Log.i("LHttpClient", "throwable=" + response.getTextResponse("utf-8"));
		}
	}
}.start();
```

最后
-----------
欢迎共同交流，我的[blog](http://blog.csdn.net/lgl1170860350)，谢谢。
