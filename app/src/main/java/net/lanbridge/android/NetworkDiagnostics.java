package net.lanbridge.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import okhttp3.*;
import org.json.*;

/** Credential-free diagnostics with bounded DNS/TCP/TLS/HTTP stages. */
public final class NetworkDiagnostics {
    private NetworkDiagnostics() {}
    private static final String[] SELF_DNS={"www.baidu.com","www.qq.com"};
    private static final String[] SELF_IPS={"223.5.5.5:443","119.29.29.29:443"};
    private static final String SELF_HTTP="https://www.baidu.com/";
    public static HttpUrl target(String input) {
        String s=input.trim();if(!s.contains("://"))s="https://"+s;
        HttpUrl u=HttpUrl.parse(s);
        if(u==null||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null||u.fragment()!=null)throw new IllegalArgumentException("请输入主机名或 HTTP/HTTPS 地址，不包含账号、查询参数或片段");
        return u;
    }
    private static String title(String name){switch(name){case "network":return "设备联网";case "dns":return "DNS 解析";case "ip":return "IP 直连";case "http":return "网页访问";case "tcp":return "TCP 连接";case "tls":return "TLS 握手";default:return name.toUpperCase(Locale.ROOT);}}
    private static JSONObject stage(String name,long start,String status,String detail)throws JSONException {
        return new JSONObject().put("name",name).put("title",title(name)).put("status",status).put("duration_ms",(System.nanoTime()-start)/1000000).put("detail",detail);
    }
    private static JSONObject skipped(String name,String detail,String advice)throws JSONException {
        return stage(name,System.nanoTime(),"skipped",detail).put("advice",advice);
    }
    public static JSONObject run(String input)throws Exception{return run(input,0,5000,3);}
    public static JSONObject run(String input,int overridePort,int timeout,int attempts)throws Exception {
        if(timeout<1000||timeout>15000||attempts<1||attempts>5||overridePort<0||overridePort>65535)throw new IllegalArgumentException("诊断参数超出范围");
        HttpUrl u=target(input);if(overridePort>0)u=u.newBuilder().port(overridePort).build();final HttpUrl endpoint=u;
        JSONArray stages=new JSONArray();List<InetAddress> addresses=new ArrayList<>();String started=java.time.Instant.now().toString();long start=System.nanoTime();
        ExecutorService resolver=Executors.newSingleThreadExecutor();
        try{addresses.addAll(Arrays.asList(resolver.submit(()->InetAddress.getAllByName(endpoint.host())).get(timeout,TimeUnit.MILLISECONDS)));JSONArray ips=new JSONArray(),shown=new JSONArray();for(InetAddress a:addresses)ips.put(a.getHostAddress());for(int i=0;i<Math.min(3,ips.length());i++)shown.put(ips.get(i));stages.put(stage("dns",start,"ok",ips.length()+" 个地址 · "+join(shown)+(ips.length()>3?" …":"")).put("addresses",ips));}
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

    /** One-tap check: active network, DNS, direct IP routing and a real web request. */
    public static JSONObject selfCheck(Context context,int timeout)throws Exception{
        return selfCheck(context,timeout,SELF_DNS,SELF_IPS,SELF_HTTP);
    }
    static JSONObject selfCheck(Context context,int timeout,String[] dnsHosts,String[] ipEndpoints,String httpUrl)throws Exception{
        if(timeout<1000||timeout>15000)throw new IllegalArgumentException("自检超时超出范围");
        JSONObject network=networkStep(context);
        JSONArray steps=new JSONArray().put(network);
        boolean online=!"error".equals(network.getString("status"));
        JSONObject dns=online?dnsCheck(dnsHosts,timeout):skipped("dns","设备未联网，跳过 DNS 检查","连接网络后重试");
        steps.put(dns);
        JSONObject ip=online?ipCheck(ipEndpoints,timeout):skipped("ip","设备未联网，跳过 IP 直连检查","连接网络后重试");
        steps.put(ip);
        boolean canHttp=online&&!"error".equals(dns.getString("status"));
        JSONObject http=canHttp?httpCheck(httpUrl,timeout):skipped("http",online?"DNS 异常，跳过网页访问":"设备未联网，跳过网页访问",online?"先处理 DNS 或网络连接":"连接网络后重试");
        steps.put(http);
        int errors=count(steps,"error"),warns=count(steps,"warn");
        String summary;
        if(!online)summary="未联网：请先连接 Wi-Fi 或移动数据";
        else if(errors>0)summary="自检完成，发现 "+errors+" 项异常"+(warns>0?"，"+warns+" 项需注意":"");
        else if(warns>0)summary="自检完成，基本可用，"+warns+" 项需注意";
        else summary="自检完成，网络正常";
        return new JSONObject().put("id",LedgerStore.id()).put("mode","self-check").put("target","自动自检").put("started_at",java.time.Instant.now().toString()).put("network",network).put("steps",steps).put("summary",summary);
    }
    static JSONObject networkStep(Context context)throws JSONException{
        long start=System.nanoTime();
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=manager==null?null:manager.getActiveNetwork();
        if(active==null)return stage("network",start,"error","没有可用的网络连接").put("transport","none").put("advice","打开 Wi-Fi 或移动数据后重试");
        NetworkCapabilities caps=manager.getNetworkCapabilities(active);
        LinkProperties link=manager.getLinkProperties(active);
        boolean internet=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        String transport=transport(caps);
        String iface=link==null||link.getInterfaceName()==null?"":link.getInterfaceName();
        JSONArray dns=new JSONArray();
        if(link!=null)for(InetAddress address:link.getDnsServers())if(dns.length()<3)dns.put(address.getHostAddress());
        String detail=transport+(iface.isEmpty()?"":" · "+iface)+(dns.length()>0?" · DNS "+join(dns):"")+(validated?" · 已连接互联网":internet?" · 系统未验证互联网":" · 无互联网能力");
        String status=internet?(validated?"ok":"warn"):"error";
        JSONObject step=stage("network",start,status,detail).put("transport",transport).put("validated",validated).put("interface",iface).put("dns_servers",dns);
        if(!"ok".equals(status))step.put("advice","如有网页认证，请先在浏览器完成登录；否则检查路由器或运营商连接。");
        return step;
    }
    private static String transport(NetworkCapabilities caps){
        if(caps==null)return "未知网络";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "VPN";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "移动数据";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "以太网";
        return "其他网络";
    }
    static JSONObject dnsCheck(String[] hosts,int timeout)throws JSONException{
        long start=System.nanoTime();JSONArray results=new JSONArray(),failed=new JSONArray();int ok=0;String first="";
        for(String host:hosts){
            long one=System.nanoTime();
            try{
                InetAddress[] addresses=resolve(host,timeout);
                if(addresses.length==0)throw new UnknownHostException(host);
                JSONArray ips=new JSONArray();for(int i=0;i<Math.min(3,addresses.length);i++)ips.put(addresses[i].getHostAddress());
                long ms=(System.nanoTime()-one)/1000000;
                results.put(new JSONObject().put("host",host).put("status","ok").put("addresses",ips).put("duration_ms",ms));
                if(first.isEmpty())first=host+" → "+addresses[0].getHostAddress()+"（"+ms+" ms）";
                ok++;
            }catch(Exception e){failed.put(host);results.put(new JSONObject().put("host",host).put("status","error").put("detail",safe(e)).put("duration_ms",(System.nanoTime()-one)/1000000));}
        }
        String status=ok==hosts.length?"ok":ok>0?"warn":"error";
        String detail=ok+"/"+hosts.length+" 个域名解析成功"+(first.isEmpty()?"":" · "+first)+(failed.length()>0?" · 失败："+join(failed):"");
        JSONObject step=stage("dns",start,status,detail).put("results",results);
        if("error".equals(status))step.put("advice","检查 DNS 设置，或关闭 VPN/代理后重试。");
        else if("warn".equals(status))step.put("advice","部分域名解析失败，可能有 DNS 污染或拦截。");
        return step;
    }
    static JSONObject ipCheck(String[] endpoints,int timeout)throws JSONException{
        long start=System.nanoTime();JSONArray results=new JSONArray(),failed=new JSONArray();int ok=0;String first="";
        for(String endpoint:endpoints){
            String host=endpoint;int port=443,split=endpoint.lastIndexOf(':');
            if(split>0){host=endpoint.substring(0,split);try{port=Integer.parseInt(endpoint.substring(split+1));}catch(NumberFormatException e){failed.put(endpoint);continue;}}
            long one=System.nanoTime();
            try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(InetAddress.getByName(host),port),timeout);
                long ms=(System.nanoTime()-one)/1000000;
                results.put(new JSONObject().put("endpoint",endpoint).put("status","ok").put("duration_ms",ms));
                if(first.isEmpty())first=endpoint+"（"+ms+" ms）";
                ok++;
            }catch(Exception e){failed.put(endpoint);results.put(new JSONObject().put("endpoint",endpoint).put("status","error").put("detail",safe(e)).put("duration_ms",(System.nanoTime()-one)/1000000));}
        }
        String status=ok==endpoints.length?"ok":ok>0?"warn":"error";
        String detail=ok+"/"+endpoints.length+" 个 IP 可连接"+(first.isEmpty()?"":" · "+first)+(failed.length()>0?" · 失败："+join(failed):"");
        JSONObject step=stage("ip",start,status,detail).put("results",results);
        if("error".equals(status))step.put("advice","IP 直连失败：检查 Wi-Fi/移动数据，或路由器防火墙是否拦截。");
        else if("warn".equals(status))step.put("advice","部分 IP 不可达，可能有防火墙或运营商限制。");
        return step;
    }
    static JSONObject httpCheck(String url,int timeout)throws JSONException{
        long start=System.nanoTime();
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(timeout,TimeUnit.MILLISECONDS).readTimeout(timeout,TimeUnit.MILLISECONDS).callTimeout(timeout*2L,TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
        Response response=null;
        try{
            response=client.newCall(new Request.Builder().url(url).head().build()).execute();
            if(response.code()==405){response.close();response=client.newCall(new Request.Builder().url(url).get().build()).execute();}
            int code=response.code();long ms=(System.nanoTime()-start)/1000000;
            JSONObject step=stage("http",start,code<500?"ok":"error","HTTP "+code+" · "+ms+" ms").put("http_status",code);
            if(code>=500)step.put("advice","目标返回服务器错误，换一个地址再试。");
            return step;
        }catch(Exception e){return stage("http",start,"error",safe(e)).put("advice","能解析但网页打不开，可能是代理、强制门户或目标站点不可用。");}
        finally{if(response!=null)response.close();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    }
    private static InetAddress[] resolve(String host,int timeout)throws Exception{
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try{return executor.submit(()->InetAddress.getAllByName(host)).get(timeout,TimeUnit.MILLISECONDS);}
        catch(TimeoutException e){throw new SocketTimeoutException("解析超时");}
        finally{executor.shutdownNow();}
    }
    private static int count(JSONArray steps,String status)throws JSONException{
        int count=0;for(int i=0;i<steps.length();i++)if(status.equals(steps.getJSONObject(i).optString("status")))count++;return count;
    }
    private static String join(JSONArray values)throws JSONException{
        StringBuilder text=new StringBuilder();for(int i=0;i<values.length();i++){if(i>0)text.append("、");text.append(values.get(i));}return text.toString();
    }
    private static String safe(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();return cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());}
}
