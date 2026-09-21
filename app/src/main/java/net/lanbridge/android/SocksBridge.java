package net.lanbridge.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class SocksBridge implements Closeable {
    private final NasSession nas;
    private final String scope;
    private final ServerSocket listener;
    private final ExecutorService workers=Executors.newCachedThreadPool();
    private final Semaphore capacity=new Semaphore(64);
    private final Set<Closeable> connections=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public final AtomicLong tx=new AtomicLong(),rx=new AtomicLong();
    public SocksBridge(NasSession nas,String scope) throws IOException {
        this.nas=nas;this.scope=scope;
        listener=new ServerSocket();listener.bind(new InetSocketAddress("127.0.0.1",0),32);
        workers.execute(()-> {
            while(!closed) {
                try {
                    Socket socket=listener.accept();
                    if(!capacity.tryAcquire()) { socket.close();continue; }
                    connections.add(socket);workers.execute(()->handle(socket));
                } catch(IOException|RejectedExecutionException e) { if(!closed) close(); }
            }
        });
    }
    public int port() { return listener.getLocalPort(); }
    public int active() { return 64-capacity.availablePermits(); }
    static byte[] exact(InputStream in,int size) throws IOException {
        byte[] bytes=new byte[size];int offset=0;
        while(offset<size) { int n=in.read(bytes,offset,size-offset);if(n<0) throw new EOFException();offset+=n; }return bytes;
    }
    private void handle(Socket socket) {
        WsChannel tunnel=null;boolean established=false,complete=false;
        try {
            socket.setSoTimeout(20000);socket.setTcpNoDelay(true);
            InputStream input=socket.getInputStream();OutputStream output=socket.getOutputStream();
            byte[] hello=exact(input,2);byte[] methods=exact(input,hello[1]&255);boolean plain=false;
            for(byte b:methods) if(b==0) plain=true;
            if(hello[0]!=5 || !plain) { output.write(new byte[]{5,(byte)255});return; }
            output.write(new byte[]{5,0});
            byte[] request=exact(input,4);
            if(request[0]!=5 || request[1]!=1) { output.write(new byte[]{5,7,0,1,0,0,0,0,0,0});return; }
            String host;
            if(request[3]==1) host=InetAddress.getByAddress(exact(input,4)).getHostAddress();
            else if(request[3]==3) host=new String(exact(input,exact(input,1)[0]&255),StandardCharsets.US_ASCII);
            else { output.write(new byte[]{5,8,0,1,0,0,0,0,0,0});return; }
            byte[] p=exact(input,2);int port=((p[0]&255)<<8)|(p[1]&255);
            tunnel=nas.openTunnel(host,port,scope);connections.add(tunnel);
            output.write(new byte[]{5,0,0,1,0,0,0,0,0,0});established=true;socket.setSoTimeout(300000);
            final WsChannel peer=tunnel;
            CountDownLatch uploadDone=new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean uploadEof=new java.util.concurrent.atomic.AtomicBoolean();
            workers.execute(()-> {
                try {
                    byte[] bytes=new byte[65536];int n;
                    while((n=input.read(bytes))!=-1) { peer.send(bytes,n);tx.addAndGet(n); }
                    if(peer.halfClose) { peer.send("{\"eof\":true}");uploadEof.set(true); }
                } catch(IOException ignored) {} finally {
                    if(!uploadEof.get()) { peer.close();try { socket.close(); } catch(IOException ignored) {} }
                    uploadDone.countDown();
                }
            });
            while(!closed) {
                Object message=tunnel.receive(300);
                if(message instanceof byte[]) { byte[] bytes=(byte[])message;output.write(bytes);rx.addAndGet(bytes.length); }
                else if(peer.halfClose && message instanceof String && new org.json.JSONObject((String)message).optBoolean("eof")) {
                    socket.shutdownOutput();
                    if(!uploadDone.await(300,TimeUnit.SECONDS)) throw new IOException("等待连接完成超时");
                    complete=uploadEof.get();break;
                }
            }
        } catch(Exception ignored) {
            if(!established) try { socket.getOutputStream().write(new byte[]{5,1,0,1,0,0,0,0,0,0}); } catch(IOException ignoredAgain) {}
        } finally {
            if(tunnel!=null) { if(complete) tunnel.finish();else tunnel.close();connections.remove(tunnel); }
            try { socket.close(); } catch(IOException ignored) {} connections.remove(socket);capacity.release();
        }
    }
    @Override public void close() {
        closed=true;try { listener.close(); } catch(IOException ignored) {}
        for(Closeable connection:connections) try { connection.close(); } catch(IOException ignored) {}
        connections.clear();workers.shutdownNow();
    }
}
