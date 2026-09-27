package net.lanbridge.android;

import android.content.Context;
import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Deliberate encrypted configuration replacement using the existing shared root/password. */
final class AiConfigSync {
    private static final String PATH="ai-config/config-v1.json";
    private static final Object LOCK=new Object();
    private static OkHttpClient client(){return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build();}
    private static Request.Builder request(WebDavSettings.Connection c)throws Exception{
        HttpUrl.Builder u=RelayDav.endpoint(c.url).newBuilder();for(String p:(c.root+"/"+PATH).split("/"))u.addPathSegment(p);return new Request.Builder().url(u.build()).header("Authorization",Credentials.basic(c.user,c.password,StandardCharsets.UTF_8));
    }
    private static byte[] read(Response r)throws Exception{if(r.body()==null)throw new IOException("AI 配置响应为空");try(InputStream in=r.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>AiConfigCodec.LIMIT)throw new IOException("AI 配置过大");}return out.toByteArray();}}
    static String upload(Context context)throws Exception{synchronized(LOCK){WebDavSettings.Connection c=WebDavSettings.connection(context);c.ensure();try(RelayDav dav=c.client()){dav.ensure("ai-config");}JSONObject envelope=AiConfigCodec.encrypt(new AiSettings(context).exportSnapshot(),c.password);OkHttpClient http=client();try{
        String etag=null;boolean exists;try(Response r=http.newCall(request(c).get().build()).execute()){exists=r.code()!=404;if(exists){if(r.code()!=200)throw new IOException("读取远端配置失败（HTTP "+r.code()+"）");etag=r.header("ETag");if(!RelayDav.strong(etag))throw new IOException("服务器未提供可靠版本标识，无法安全覆盖 AI 配置");}}
        Request.Builder put=request(c).put(RequestBody.create(envelope.toString(),MediaType.get("application/json; charset=utf-8"))).header(exists?"If-Match":"If-None-Match",exists?etag:"*");
        try(Response r=http.newCall(put.build()).execute()){if(r.code()==412)throw new IOException("远端 AI 配置刚被另一设备修改，请重新检查后上传");if(r.code()!=200&&r.code()!=201&&r.code()!=204)throw new IOException("上传 AI 配置失败（HTTP "+r.code()+"）");}return "AI 配置和密钥已加密上传";
    }finally{http.connectionPool().evictAll();http.dispatcher().executorService().shutdown();}}}
    static JSONObject download(Context context)throws Exception{synchronized(LOCK){WebDavSettings.Connection c=WebDavSettings.connection(context);OkHttpClient http=client();try(Response r=http.newCall(request(c).get().build()).execute()){if(r.code()!=200)throw new IOException("下载 AI 配置失败（HTTP "+r.code()+"）");return AiConfigCodec.decrypt(new JSONObject(new String(read(r),StandardCharsets.UTF_8)),c.password);}finally{http.connectionPool().evictAll();http.dispatcher().executorService().shutdown();}}}
}
