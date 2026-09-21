package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.system.OsConstants;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.SocketFactory;
import hev.htproxy.TProxyService;

public final class BridgeVpnService extends VpnService {
    public static final String CONNECT="connect",DISCONNECT="disconnect",PROBE="probe";
    public static volatile Credentials pending;
    public static volatile String phase="idle",message="尚未连接",probeResult="";
    public static volatile long tx,rx;
    public static volatile int active;
    public static final class Credentials {
        public final String origin,user,password,scope;public final boolean remember;
        public Credentials(String o,String u,String p,String s,boolean r) { origin=o;user=u;password=p;scope=s;remember=r; }
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean stopping=new AtomicBoolean();
    private volatile NasSession nas;
    private volatile SocksBridge socks;
    private ParcelFileDescriptor tun;
    private String scope;
    private int commandId;
    private volatile boolean cleanupCompleted;
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel=new NotificationChannel("vpn","VPN 连接",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private Notification notification(String text) {
        PendingIntent show=PendingIntent.getActivity(this,0,new Intent(this,VpnActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BridgeVpnService.class).setAction(DISCONNECT),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"vpn").setSmallIcon(R.drawable.ic_bridge).setContentTitle("回家 VPN").setContentText(text)
            .setOngoing(true).setContentIntent(show).addAction(new Notification.Action.Builder(null,"断开",stop).build()).build();
    }
    private void update(String next,String text) {
        phase=next;message=text;
        if(!stopping.get()) getSystemService(NotificationManager.class).notify(1,notification(text));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        commandId=startId;
        if(intent==null) { stopSelf(startId);return START_NOT_STICKY; }
        String action=intent.getAction();
        if(DISCONNECT.equals(action)) { shutdown(null);return START_NOT_STICKY; }
        if(PROBE.equals(action)) { if("connected".equals(phase)) worker.execute(this::probe);return START_NOT_STICKY; }
        if(!CONNECT.equals(action)) return START_NOT_STICKY;
        startForeground(1,notification("正在连接飞牛"));
        if("connecting".equals(phase)||"connected".equals(phase)) return START_NOT_STICKY;
        Credentials credentials=pending;pending=null;
        if(credentials==null) { shutdown("请打开应用后重新连接");return START_NOT_STICKY; }
        stopping.set(false);phase="connecting";message="正在连接飞牛";probeResult="";
        worker.execute(()->connect(credentials));return START_NOT_STICKY;
    }
    private SocketFactory protectedSockets() {
        return new SocketFactory() {
            private Socket bound(InetAddress local,int port) throws IOException { Socket s=new Socket();s.bind(new InetSocketAddress(local,port));if(!protect(s)) { s.close();throw new IOException("无法保护隧道出口连接"); }return s; }
            @Override public Socket createSocket() throws IOException { return bound(null,0); }
            @Override public Socket createSocket(String h,int p) throws IOException { Socket s=createSocket();s.connect(new InetSocketAddress(h,p));return s; }
            @Override public Socket createSocket(InetAddress h,int p) throws IOException { Socket s=createSocket();s.connect(new InetSocketAddress(h,p));return s; }
            @Override public Socket createSocket(String h,int p,InetAddress l,int lp) throws IOException { Socket s=bound(l,lp);s.connect(new InetSocketAddress(h,p));return s; }
            @Override public Socket createSocket(InetAddress h,int p,InetAddress l,int lp) throws IOException { Socket s=bound(l,lp);s.connect(new InetSocketAddress(h,p));return s; }
        };
    }
    private void connect(Credentials credentials) {
        try {
            scope=credentials.scope;
            nas=new NasSession(credentials.origin,protectedSockets(),okhttp3.Dns.SYSTEM);
            if(stopping.get()) return;
            nas.login(credentials.user,credentials.password,scope);
            if(stopping.get()) return;
            LoginStore store=new LoginStore(this);
            if(credentials.remember) store.save(credentials.origin,credentials.user,credentials.password,scope);
            else { store.forget();store.config(credentials.origin,credentials.user,scope); }
            socks=new SocksBridge(nas,scope);
            Builder b=new Builder().setSession("回家 VPN").setMtu(1400).addAddress("198.18.0.1",30).setMetered(false)
                .setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,VpnActivity.class),PendingIntent.FLAG_IMMUTABLE));
            b.addDisallowedApplication(getPackageName());
            if(scope.equals("all")) {
                b.addAddress("fdfe:dcba:9876::1",126).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("198.18.0.2");
            } else {
                for(String network:nas.networks) { String[] parts=network.split("/");b.addRoute(parts[0],Integer.parseInt(parts[1])); }
                b.allowFamily(OsConstants.AF_INET6);
            }
            if(stopping.get()) return;
            tun=b.establish();if(tun==null) throw new IOException("VPN 授权已失效，请重新连接并确认系统授权");
            File config=new File(getCacheDir(),"tunnel.yml");Files.write(config.toPath(),VpnConfig.nativeConfig(socks.port(),scope).getBytes(StandardCharsets.UTF_8));
            if(!TProxyService.TProxyStartService(config.getAbsolutePath(),tun.getFd())) throw new IOException("VPN 内核启动失败");
            Thread.sleep(400);
            if(!TProxyService.TProxyIsRunning()) throw new IOException("VPN 内核已退出");
            if(stopping.get()) return;
            update("connected",scope.equals("all")?"全局 TCP · 使用家中网络":"仅回家 · "+String.join("，",nas.networks));
            timer.scheduleAtFixedRate(()-> {
                if(stopping.get()) return;
                if(!TProxyService.TProxyIsRunning()) { shutdown("VPN 内核已停止，请重新连接");return; }
                SocksBridge current=socks;if(current!=null) { tx=current.tx.get();rx=current.rx.get();active=current.active(); }
            },1,1,TimeUnit.SECONDS);
            timer.scheduleAtFixedRate(()-> {
                if(stopping.get()) return;
                try {
                    try { nas.maintain(); } catch(Exception first) { Thread.sleep(2000);if(stopping.get()) return;nas.bootstrap();nas.maintain(); }
                } catch(Exception e) { shutdown("飞牛会话或网络已断开，请重新连接"); }
            },60,60,TimeUnit.SECONDS);
        } catch(Exception|LinkageError e) { if(!stopping.get()) shutdown(e.getMessage()==null?"连接失败，请重试":e.getMessage()); }
    }
    private void probe() {
        probeResult="正在测试…";long start=System.nanoTime();
        try {
            nas.maintain();
            probeResult="会话正常 · "+((System.nanoTime()-start)/1000000)+" ms\n此检查验证 NAS 会话；不代表所有目标均可访问。";
        } catch(Exception e) { probeResult="测试失败："+e.getMessage(); }
    }
    private void shutdown(String error) {
        if(!stopping.compareAndSet(false,true)) return;
        phase="stopping";message="正在断开";
        // Conscrypt may send TLS close_notify even when a socket is being closed.
        // Cancellation and cleanup therefore must never run on the Android main thread.
        NasSession current=nas;
        if(current!=null) new Thread(current::close,"vpn-cancel").start();
        timer.shutdownNow();
        worker.execute(()-> {
            cleanup();cleanupCompleted=true;phase=error==null?"idle":"error";message=error==null?"已断开":error;
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(commandId);
        });
    }
    private void cleanup() {
        try { TProxyService.TProxyStopService(); } catch(LinkageError ignored) {}
        if(tun!=null) { try { tun.close(); } catch(IOException ignored) {} tun=null; }
        if(socks!=null) { socks.close();socks=null; }
        if(nas!=null) { nas.close();nas=null; }
        active=0;
    }
    @Override public void onRevoke() { shutdown("VPN 授权已被撤销，或另一 VPN 已接管连接"); }
    @Override public void onDestroy() {
        stopping.set(true);timer.shutdownNow();
        if(!cleanupCompleted) {
            NasSession current=nas;
            if(current!=null) new Thread(current::close,"vpn-destroy-cancel").start();
            // Queue behind any connection work, which observes stopping before starting TUN.
            worker.execute(()-> { cleanup();cleanupCompleted=true;phase="idle";message="已断开"; });
        }
        worker.shutdown();super.onDestroy();
    }
}
