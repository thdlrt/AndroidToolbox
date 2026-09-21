package net.lanbridge.android;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.net.SocketFactory;
import org.json.*;
import okhttp3.*;

public final class NasSession implements Closeable {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    public final String origin;
    public final String host;
    public final List<InetAddress> addresses;
    private final OkHttpClient http;
    private final MemoryCookies cookies=new MemoryCookies();
    private final byte[] key=Base64.getEncoder().encode(random(24));
    private final byte[] iv=random(16);
    private byte[] secret;
    private WsChannel auth;
    private String si,pub;
    private volatile boolean closed;
    public List<String> networks=Collections.emptyList();
    public boolean allowPublic,halfClose;

    public static String origin(String value) {
        HttpUrl u=HttpUrl.parse(value.trim());
        if(u==null || !u.username().isEmpty() || !u.password().isEmpty() || !u.encodedPath().equals("/") || u.query()!=null || u.fragment()!=null)
            throw new IllegalArgumentException("请填写 NAS 根地址，不要包含登录或应用路径");
        if(!(u.isHttps() && u.host().matches("[A-Za-z0-9_-]+\\.fnos\\.net")) && !privateIp(u.host()))
            throw new IllegalArgumentException("请使用设备专属 FN Connect HTTPS 域名，或局域网 NAS IP");
        return u.toString().replaceAll("/$","");
    }
    public static boolean privateIp(String ip) {
        if(!ip.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        try { String[] p=ip.split("\\."); int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);
            for(String s:p) if(Integer.parseInt(s)>255 || Integer.parseInt(s)<0) return false;
            return a==10 || (a==172 && b>=16 && b<=31) || (a==192 && b==168);
        } catch(NumberFormatException e) { return false; }
    }
    public NasSession(String value) throws IOException { this(value,SocketFactory.getDefault(),Dns.SYSTEM); }
    public NasSession(String value,SocketFactory sockets,Dns dns) throws IOException {
        origin=origin(value); host=Objects.requireNonNull(HttpUrl.parse(origin)).host();
        addresses=new ArrayList<>();
        for(InetAddress ip:dns.lookup(host)) if(ip instanceof Inet4Address) addresses.add(ip);
        if(addresses.isEmpty()) throw new IOException("无法解析 FN Connect 的 IPv4 地址");
        http=new OkHttpClient.Builder().socketFactory(sockets).dns(name->name.equals(host)?addresses:dns.lookup(name))
            .proxy(Proxy.NO_PROXY).cookieJar(cookies).followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS)
            .pingInterval(20,TimeUnit.SECONDS).build();
    }
    public static byte[] random(int count) { byte[] result=new byte[count]; RANDOM.nextBytes(result); return result; }
    public static String hex(int count) { StringBuilder s=new StringBuilder(); for(byte b:random(count)) s.append(String.format(Locale.ROOT,"%02x",b&255)); return s.toString(); }
    private Request.Builder request(String path) throws IOException { if(closed) throw new IOException("连接已取消"); return new Request.Builder().url(origin+path).header("Origin",origin); }
    private WsChannel websocket(String path) throws IOException { return new WsChannel(http,request(path).build(),path.startsWith("/websocket")); }
    public synchronized void bootstrap() throws Exception {
        for(int i=0;i<4;i++) {
            try(Response r=http.newCall(request("/").header("Referer","https://fnos.net/").build()).execute()) {
                if(!r.isRedirect()) break;
                HttpUrl next=r.request().url().resolve(r.header("Location",""));
                if(next==null || !next.scheme().equals(HttpUrl.get(origin).scheme()) || !next.host().equals(host) || next.port()!=HttpUrl.get(origin).port())
                    throw new IOException("飞牛入口跳转异常，请使用设备专属 fnos.net 域名");
            }
        }
        if(auth!=null) auth.close();
        auth=websocket("/websocket?type=main");
        JSONObject r=call(new JSONObject().put("req","util.crypto.getRSAPub"),false);
        si=r.getString("si");pub=r.getString("pub");
    }
    static byte[] aes(byte[] data,byte[] key,byte[] iv,int mode) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv)); return cipher.doFinal(data);
    }
    static String signed(String body,byte[] secret) throws GeneralSecurityException {
        Mac h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(secret,"HmacSHA256"));
        return Base64.getEncoder().encodeToString(h.doFinal(body.getBytes(StandardCharsets.UTF_8)))+body;
    }
    private synchronized JSONObject call(JSONObject body,boolean encrypted) throws Exception {
        String id=hex(12);body.put("reqid",id);String wire=body.toString();
        if(encrypted) {
            String pem=pub.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
            PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,publicKey);
            wire=new JSONObject().put("req","encrypted").put("iv",Base64.getEncoder().encodeToString(iv))
                .put("rsa",Base64.getEncoder().encodeToString(rsa.doFinal(key)))
                .put("aes",Base64.getEncoder().encodeToString(aes(wire.getBytes(StandardCharsets.UTF_8),key,iv,Cipher.ENCRYPT_MODE))).toString();
        } else if(secret!=null && !body.getString("req").startsWith("util.crypto.")) wire=signed(wire,secret);
        auth.expectReply(id);auth.send(wire); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline) {
            Object message=auth.receive(20);
            if(!(message instanceof String)) continue;
            JSONObject r=new JSONObject((String)message);
            if(!id.equals(r.optString("reqid"))) continue;
            if("fail".equals(r.optString("result"))) throw new IOException("飞牛认证失败，错误码 "+r.opt("errno"));
            auth.expectReply(null);return r;
        }
        throw new IOException("飞牛认证超时");
    }
    private void ticket(String ticket) throws Exception {
        Request r=request("/app/ticket").post(RequestBody.create(new JSONObject().put("ticket",ticket).toString(),JSON)).build();
        try(Response response=http.newCall(r).execute()) { if(response.code()!=200) throw new IOException("飞牛登录票据交换失败"); }
    }
    public synchronized void login(String username,String password,String scope) throws Exception {
        if(!scope.equals("lan") && !scope.equals("all")) throw new IllegalArgumentException("代理范围无效");
        bootstrap();
        JSONObject r=call(new JSONObject().put("req","user.login").put("user",username).put("password",password).put("stay",true)
            .put("deviceType","PC").put("deviceName","LanBridge Android").put("did",hex(16)).put("ver",2).put("si",si),true);
        if(!r.has("ticket")) throw new IOException("此版本暂不支持双重验证，请使用普通飞牛管理员登录");
        if(r.has("secret")) secret=aes(Base64.getDecoder().decode(r.getString("secret")),key,iv,Cipher.DECRYPT_MODE);
        ticket(r.getString("ticket"));
        try(Response response=http.newCall(request("/app/lanbridge/api/tunnel").build()).execute()) {
            if(response.code()!=200 || response.body()==null) throw new IOException("NAS 局域网桥未升级或没有管理员权限");
            JSONObject info=new JSONObject(response.body().string());
            if(!"lanbridge-tcp-v1".equals(info.optString("protocol"))) throw new IOException("NAS 隧道版本不匹配");
            halfClose=info.optBoolean("tcpHalfClose",false);
            JSONObject policy=info.getJSONObject("policy");
            if(!policy.optBoolean("enabled")) throw new IOException("请先在 NAS 局域网桥中开启客户端隧道");
            allowPublic=policy.optBoolean("allowPublic");
            if(scope.equals("all") && !allowPublic) throw new IOException("NAS 未开启全局出口，请先在飞牛插件中启用");
            JSONArray cidrs=policy.getJSONArray("networks");ArrayList<String> result=new ArrayList<>();
            for(int i=0;i<cidrs.length();i++) result.add(VpnConfig.network(cidrs.getString(i)));
            if(result.isEmpty() || result.size()>16) throw new IOException("NAS 返回了无效的局域网网段");
            networks=Collections.unmodifiableList(result);
        }
        bootstrap(); call(new JSONObject().put("req","user.authToken").put("main",true).put("si",si),false);
    }
    private int tokenStatus() throws IOException { try(Response r=http.newCall(request("/app/token").build()).execute()) { return r.code(); } }
    public synchronized void maintain() throws Exception {
        if(closed) return;
        if(auth==null || !auth.isOpen()) bootstrap();
        int status=tokenStatus();
        if(status==401) {
            bootstrap();JSONObject token=call(new JSONObject().put("req","user.tokenLogin").put("ver",2).put("si",si).put("deviceType","PC").put("deviceName","LanBridge Android"),false);
            if(!token.has("ticket")) throw new IOException("飞牛登录已失效，请重新连接");
            ticket(token.getString("ticket")); bootstrap();
        } else if(status!=200) throw new IOException("无法检查飞牛登录状态");
        call(new JSONObject().put("req","user.authToken").put("main",true).put("si",si),false);
        call(new JSONObject().put("req","user.active"),false);
    }
    public WsChannel openTunnel(String target,int port,String scope) throws Exception {
        WsChannel ws=websocket("/app/lanbridge/tunnel/tcp");
        try {
            ws.send(new JSONObject().put("v",1).put("host",target).put("port",port).put("scope",scope).put("halfClose",halfClose).toString());
            Object ack=ws.receive(20);
            if(!(ack instanceof String) || !new JSONObject((String)ack).optBoolean("ok")) throw new IOException("NAS 拒绝了目标连接");
            ws.halfClose=new JSONObject((String)ack).optBoolean("halfClose",false);
            return ws;
        } catch(Exception e) { ws.close(); throw e; }
    }
    @Override public void close() {
        closed=true;if(auth!=null) auth.close();http.dispatcher().cancelAll();http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();cookies.clear();Arrays.fill(key,(byte)0);if(secret!=null) Arrays.fill(secret,(byte)0);
    }
    static final class MemoryCookies implements CookieJar {
        private final List<Cookie> values=new ArrayList<>();
        public synchronized void clear() { values.clear(); }
        @Override public synchronized void saveFromResponse(HttpUrl url,List<Cookie> incoming) {
            for(Cookie c:incoming) { values.removeIf(old->old.name().equals(c.name()) && old.domain().equals(c.domain()) && old.path().equals(c.path())); if(c.expiresAt()>System.currentTimeMillis()) values.add(c); }
        }
        @Override public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            values.removeIf(c->c.expiresAt()<=System.currentTimeMillis());List<Cookie> result=new ArrayList<>();for(Cookie c:values) if(c.matches(url)) result.add(c);return result;
        }
    }
}
