package net.lanbridge.android;

import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okio.ByteString;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Cipher;
import javax.net.SocketFactory;

public class SessionTest {
    @Test public void loginRefreshUsesNewCookiesAndHandshakeAndSocksStreams() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair keys=generator.generateKeyPair();
        String pub="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
        byte[] secret="test-session-signing-key".getBytes(StandardCharsets.UTF_8);
        AtomicInteger bootstraps=new AtomicInteger(),auths=new AtomicInteger(),tokenChecks=new AtomicInteger();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest req) {
                    String path=req.getPath();
                    if(path.equals("/")) return new MockResponse().setResponseCode(200).addHeader("Set-Cookie","mode=relay;Path=/");
                    if(path.equals("/app/ticket")) return new MockResponse().addHeader("Set-Cookie","ost=fresh;Path=/;HttpOnly");
                    if(path.equals("/app/token")) return new MockResponse().setResponseCode(tokenChecks.getAndIncrement()==0?401:200);
                    if(path.equals("/app/lanbridge/api/tunnel")) return new MockResponse().setBody("{\"protocol\":\"lanbridge-tcp-v1\",\"tcpHalfClose\":true,\"policy\":{\"enabled\":true,\"allowPublic\":true,\"networks\":[\"192.168.100.0/24\"]}}");
                    if(path.startsWith("/websocket")) {
                        String si="si-"+bootstraps.incrementAndGet();
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override public void onMessage(WebSocket ws,String raw) {
                                try {
                                    JSONObject body=new JSONObject(raw.startsWith("{")?raw:raw.substring(44));
                                    String command=body.getString("req");
                                    if(command.equals("encrypted")) {
                                        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,keys.getPrivate());
                                        byte[] key=rsa.doFinal(Base64.getDecoder().decode(body.getString("rsa"))),iv=Base64.getDecoder().decode(body.getString("iv"));
                                        body=new JSONObject(new String(NasSession.aes(Base64.getDecoder().decode(body.getString("aes")),key,iv,Cipher.DECRYPT_MODE),StandardCharsets.UTF_8));
                                        assertEquals("fixture-password",body.getString("password"));
                                        ws.send(new JSONObject().put("reqid",body.getString("reqid")).put("ticket","fixture-ticket")
                                            .put("secret",Base64.getEncoder().encodeToString(NasSession.aes(secret,key,iv,Cipher.ENCRYPT_MODE))).toString());return;
                                    }
                                    JSONObject response=new JSONObject().put("reqid",body.getString("reqid"));
                                    if(command.equals("util.crypto.getRSAPub")) response.put("si",si).put("pub",pub);
                                    else {
                                        assertEquals(NasSession.signed(raw.substring(44),secret),raw);
                                        if(command.equals("user.authToken")) { assertEquals(si,body.getString("si"));assertTrue(body.getBoolean("main"));assertTrue(req.getHeader("Cookie").contains("ost=fresh"));auths.incrementAndGet(); }
                                        if(command.equals("user.tokenLogin")) response.put("ticket","renewed-ticket");
                                    }
                                    for(int i=0;i<100;i++) ws.send("{\"event\":\"unrelated-system-notification\"}");
                                    ws.send(response.toString());
                                } catch(Throwable e) { failure.set(e);ws.close(1011,"fixture failed"); }
                            }
                        });
                    }
                    if(path.equals("/app/lanbridge/tunnel/tcp")) return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                        @Override public void onMessage(WebSocket ws,String raw) { if(raw.contains("\"eof\"")) ws.send("{\"eof\":true}");else ws.send("{\"ok\":true,\"v\":1,\"halfClose\":true}"); }
                        @Override public void onMessage(WebSocket ws,ByteString raw) { ws.send(raw); }
                        @Override public void onClosing(WebSocket ws,int code,String reason) { ws.close(code,""); }
                    });
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();
            try(NasSession session=new NasSession("http://192.168.50.2:"+server.getPort(),new SocketFactory() {
                public Socket createSocket() { return new Socket() { @Override public void connect(SocketAddress address,int timeout) throws IOException { super.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getPort()),timeout); } }; }
                public Socket createSocket(String h,int p) { throw new UnsupportedOperationException(); }
                public Socket createSocket(String h,int p,InetAddress l,int lp) { throw new UnsupportedOperationException(); }
                public Socket createSocket(InetAddress h,int p) { throw new UnsupportedOperationException(); }
                public Socket createSocket(InetAddress h,int p,InetAddress l,int lp) { throw new UnsupportedOperationException(); }
            },host->Collections.singletonList(InetAddress.getLoopbackAddress()))) {
                session.login("fixture","fixture-password","all");session.maintain();
                assertNull(failure.get());assertTrue(bootstraps.get()>=4);assertEquals(2,auths.get());
                try(SocksBridge bridge=new SocksBridge(session,"all");Socket socket=new Socket("127.0.0.1",bridge.port())) {
                    socket.setSoTimeout(10000);OutputStream out=socket.getOutputStream();InputStream in=socket.getInputStream();
                    out.write(new byte[]{5,1,0});assertArrayEquals(new byte[]{5,0},SocksBridge.exact(in,2));
                    out.write(new byte[]{5,1,0,1,(byte)192,(byte)168,100,1,0,80});assertEquals(0,SocksBridge.exact(in,10)[1]);
                    byte[] payload=NasSession.random(180000);out.write(payload);socket.shutdownOutput();assertArrayEquals(payload,SocksBridge.exact(in,payload.length));assertEquals(-1,in.read());
                }
            }
        }
    }
    @Test public void slowConsumerDoesNotLoseBurstDownload() throws Exception {
        byte[] block=new byte[65536];Arrays.fill(block,(byte)77);
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override public void onOpen(WebSocket ws,Response response) { for(int i=0;i<96;i++) ws.send(ByteString.of(block)); }
            }));
            server.start();OkHttpClient client=new OkHttpClient();
            try(WsChannel channel=new WsChannel(client,new Request.Builder().url(server.url("/")).build())) {
                Thread.sleep(500);
                int received=0;
                while(received<96*block.length) { Object message=channel.receive(10);assertTrue(message instanceof byte[]);received+=((byte[])message).length; }
                assertEquals(96*block.length,received);
            } finally { client.dispatcher().executorService().shutdown();client.connectionPool().evictAll(); }
        }
    }
}
