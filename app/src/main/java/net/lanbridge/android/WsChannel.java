package net.lanbridge.android;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okio.ByteString;

/** Bounded stream adapter: never let a slow consumer grow a WebSocket queue forever. */
public final class WsChannel extends WebSocketListener implements Closeable {
    private final ArrayBlockingQueue<Object> inbox = new ArrayBlockingQueue<>(32);
    private final CountDownLatch opened = new CountDownLatch(1);
    public boolean halfClose;
    private final boolean rpcMode;
    private volatile String replyId;
    private volatile IOException failure;
    private volatile boolean closed;
    private volatile WebSocket socket;
    public WsChannel(OkHttpClient client, Request request) throws IOException { this(client,request,false); }
    public WsChannel(OkHttpClient client, Request request,boolean rpcMode) throws IOException {
        this.rpcMode=rpcMode;
        socket = client.newWebSocket(request, this);
        try { if (!opened.await(20, TimeUnit.SECONDS)) throw new IOException("连接飞牛超时"); check(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); close(); throw new IOException("连接已取消"); }
        catch (IOException e) { close(); throw e; }
    }
    public boolean isOpen() { return !closed && failure == null; }
    private void check() throws IOException { if (failure != null) throw failure; if (closed) throw new EOFException("隧道已关闭"); }
    private void offer(Object value) {
        // Apply TCP backpressure on this WebSocket reader instead of dropping the connection.
        try { while(!closed) if(inbox.offer(value,200,TimeUnit.MILLISECONDS)) return; }
        catch(InterruptedException e) { Thread.currentThread().interrupt();failure=new IOException("隧道接收已取消");close(); }
    }
    @Override public void onOpen(WebSocket ws, Response response) { opened.countDown(); }
    public void expectReply(String id) { replyId=id; }
    @Override public void onMessage(WebSocket ws, String value) {
        if(rpcMode) {
            try { String id=replyId;if(id==null || !id.equals(new org.json.JSONObject(value).optString("reqid"))) return; }
            catch(org.json.JSONException ignored) { return; }
        }
        offer(value);
    }
    @Override public void onMessage(WebSocket ws, ByteString value) { if(rpcMode) return;if (value.size()>262144) { failure=new IOException("隧道数据过大"); close(); } else offer(value.toByteArray()); }
    @Override public void onFailure(WebSocket ws, Throwable t, Response response) { failure=new IOException(response == null ? "网络连接中断" : "飞牛拒绝连接（HTTP " + response.code() + "）",t); closed=true; opened.countDown(); }
    @Override public void onClosing(WebSocket ws, int code, String reason) { closed=true; ws.close(code, ""); opened.countDown(); }
    @Override public void onClosed(WebSocket ws, int code, String reason) { closed=true; opened.countDown(); }
    public Object receive(int timeoutSeconds) throws IOException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            while (System.nanoTime()<deadline) {
                Object next=inbox.poll(200,TimeUnit.MILLISECONDS);
                if(next!=null) return next;
                check();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("连接已取消"); }
        throw new IOException("等待隧道响应超时");
    }
    public void send(String value) throws IOException { check(); if (!socket.send(value)) throw new IOException("隧道发送失败"); }
    public void send(byte[] value, int count) throws IOException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(socket.queueSize()>524288) {
            check(); if(System.nanoTime()>deadline) throw new IOException("隧道发送超时");
            try { Thread.sleep(5); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("连接已取消"); }
        }
        check(); if (!socket.send(ByteString.of(value,0,count))) throw new IOException("隧道发送失败");
    }
    public void finish() { closed=true;WebSocket ws=socket;if(ws!=null && !ws.close(1000,"")) ws.cancel(); }
    @Override public void close() { closed=true; WebSocket ws=socket; if(ws!=null) ws.cancel(); opened.countDown(); }
}
