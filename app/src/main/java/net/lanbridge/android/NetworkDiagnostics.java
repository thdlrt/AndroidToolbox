package net.lanbridge.android;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import okhttp3.*;
import org.json.*;

/** Credential-free diagnostics with bounded DNS/TCP/TLS/HTTP stages. */
public final class NetworkDiagnostics {
    private NetworkDiagnostics() {}
    public static HttpUrl target(String input) {
        String s=input.trim();if(!s.contains("://"))s="https://"+s;
        HttpUrl u=HttpUrl.parse(s);
        if(u==null||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null||u.fragment()!=null)throw new IllegalArgumentException("请输入主机名或 HTTP/HTTPS 地址，不包含账号、查询参数或片段");
        return u;
    }
    private static JSONObject stage(String name,long start,String status,String detail)throws JSONException {
        return new JSONObject().put("name",name).put("status",status).put("duration_ms",(System.nanoTime()-start)/1000000).put("detail",detail);
    }
    public static JSONObject run(String input)throws Exception{return run(input,0,5000,3);}
    public static JSONObject run(String input,int overridePort,int timeout,int attempts)throws Exception {
        if(timeout<1000||timeout>15000||attempts<1||attempts>5||overridePort<0||overridePort>65535)throw new IllegalArgumentException("诊断参数超出范围");
        HttpUrl u=target(input);if(overridePort>0)u=u.newBuilder().port(overridePort).build();final HttpUrl endpoint=u;
        JSONArray stages=new JSONArray();List<InetAddress> addresses=new ArrayList<>();String started=java.time.Instant.now().toString();long start=System.nanoTime();
        ExecutorService resolver=Executors.newSingleThreadExecutor();
        try{addresses.addAll(Arrays.asList(resolver.submit(()->InetAddress.getAllByName(endpoint.host())).get(timeout,TimeUnit.MILLISECONDS)));JSONArray ips=new JSONArray();for(InetAddress a:addresses)ips.put(a.getHostAddress());stages.put(stage("dns",start,"ok",ips.toString()).put("addresses",ips));}
        catch(Exception e){stages.put(stage("dns",start,"error",e instanceof TimeoutException?"解析超时":safe(e)));}finally{resolver.shutdownNow();}
        if(addresses.isEmpty()){
            for(String name:new String[]{"tcp","tls","http"})stages.put(stage(name,System.nanoTime(),"skipped","DNS 未解析，跳过后续检查"));
        }else{
            start=System.nanoTime();JSONArray samples=new JSONArray();int succeeded=0;String error="";InetAddress connectedAddress=null;
            for(int i=0;i<attempts;i++){long sample=System.nanoTime();boolean connected=false;for(InetAddress a:addresses){try(Socket socket=new Socket()){int remaining=timeout-(int)((System.nanoTime()-sample)/1000000);if(remaining<=0)break;socket.connect(new InetSocketAddress(a,u.port()),remaining);connected=true;connectedAddress=a;break;}catch(Exception e){error=safe(e);}}if(connected){succeeded++;samples.put((System.nanoTime()-sample)/1000000.0);}}
            stages.put(stage("tcp",start,succeeded>0?"ok":"error",succeeded+"/"+attempts+" 次连接成功"+(succeeded<attempts?" · "+error:"")).put("samples_ms",samples).put("success_count",succeeded).put("attempt_count",attempts));
            if(succeeded==0){for(String name:new String[]{"tls","http"})stages.put(stage(name,System.nanoTime(),"skipped","TCP 不可连接"));}
            else{
                start=System.nanoTime();boolean tls=true;
                if(u.isHttps()){
                    try(Socket raw=new Socket()){raw.connect(new InetSocketAddress(connectedAddress,u.port()),timeout);raw.setSoTimeout(timeout);try(SSLSocket secure=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,u.host(),u.port(),true)){SSLParameters params=secure.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");secure.setSSLParameters(params);secure.startHandshake();stages.put(stage("tls",start,"ok",secure.getSession().getProtocol()+" · "+secure.getSession().getCipherSuite()));}}
                    catch(Exception e){tls=false;stages.put(stage("tls",start,"error",safe(e)));}
                }else stages.put(stage("tls",start,"skipped","HTTP 地址未使用 TLS"));
                if(!tls)stages.put(stage("http",System.nanoTime(),"skipped","TLS 验证失败"));
                else{
                    OkHttpClient client=new OkHttpClient.Builder().dns(host->addresses).connectTimeout(timeout,TimeUnit.MILLISECONDS).readTimeout(timeout,TimeUnit.MILLISECONDS).callTimeout(timeout*2L,TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();start=System.nanoTime();
                    try{Response response=client.newCall(new Request.Builder().url(u).head().build()).execute();if(response.code()==405){response.close();response=client.newCall(new Request.Builder().url(u).get().build()).execute();}try(Response r=response){stages.put(stage("http",start,r.code()<400?"ok":"error","HTTP "+r.code()+(r.isRedirect()?"（重定向未跟随）":"")).put("http_status",r.code()));}}
                    catch(Exception e){stages.put(stage("http",start,"error",safe(e)));}finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
                }
            }
        }
        int errors=0;for(int i=0;i<stages.length();i++)if(stages.getJSONObject(i).getString("status").equals("error"))errors++;
        return new JSONObject().put("id",LedgerStore.id()).put("target",u.toString()).put("host",u.host()).put("port",u.port()).put("started_at",started).put("steps",stages).put("summary",errors==0?"检查完成，未发现连接错误":"发现 "+errors+" 个异常阶段");
    }
    private static String safe(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();return cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());}
}
