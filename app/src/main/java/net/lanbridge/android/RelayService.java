package net.lanbridge.android;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.IOException;
import java.util.*;

/** A bounded foreground FIFO; the service holds incoming read grants. */
public final class RelayService extends Service {
    public interface Work { void run() throws Exception; }
    public static volatile boolean busy;
    public static volatile String message="";
    public static volatile long revision;
    private static final ArrayDeque<Work> queue=new ArrayDeque<>();
    private static volatile RelayDav active;
    private static volatile boolean cancelled;
    private boolean working;
    private int latestStart;
    private final Handler handler=new Handler(Looper.getMainLooper());
    public static void submit(Context context,Work work)throws IOException {submit(context,work,Collections.emptyList());}
    public static void submit(Context context,Work work,List<Uri> grants)throws IOException {submit(context,work,grants,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    public static synchronized String status(){return message+(queue.isEmpty()?"":" · 待传 "+queue.size()+" 项任务");}
    public static synchronized void submit(Context context,Work work,List<Uri> grants,int flags)throws IOException {
        if(queue.size()>=16)throw new IOException("待传队列已满，请稍后重试");
        if(cancelled&&busy)throw new IOException("正在取消传输，请稍候");
        if(!busy)cancelled=false;
        queue.add(work);busy=true;
        Intent intent=new Intent(context,RelayService.class);
        if(!grants.isEmpty()){
            ClipData clip=ClipData.newRawUri("待上传文件",grants.get(0));
            for(int i=1;i<grants.size();i++)clip.addItem(new ClipData.Item(grants.get(i)));
            intent.setClipData(clip);intent.addFlags(flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        }
        try{context.startForegroundService(intent);}catch(RuntimeException e){queue.remove(work);busy=active!=null||!queue.isEmpty();throw e;}
    }
    public static void bind(RelayDav dav)throws IOException {active=dav;check();}
    public static void check()throws IOException {if(cancelled)throw new IOException("操作已取消");}
    public static synchronized void cancel(){cancelled=true;queue.clear();if(active!=null)active.cancel();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        latestStart=id;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("relay","文件中转",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,41,new Intent(this,RelayActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(41,new Notification.Builder(this,"relay").setSmallIcon(R.drawable.ic_toolbox).setContentTitle("文件中转站").setContentText("正在传输文件，点此查看进度或取消").setContentIntent(open).setOngoing(true).build());
        synchronized(RelayService.class){if(working)return START_NOT_STICKY;working=true;}
        new Thread(()->{
            while(true){
                Work task;
                synchronized(RelayService.class){task=queue.poll();if(task==null){working=false;busy=false;break;}}
                try{check();task.run();}catch(Exception e){message="操作未完成："+e.getMessage();}
                finally{active=null;revision++;}
            }
            handler.post(()->{synchronized(RelayService.class){if(!working&&queue.isEmpty()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(latestStart);}}});
        },"relay-transfer").start();
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int fgsType){cancel();message="系统限制了后台传输时长，请重新操作";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){synchronized(RelayService.class){if(working){cancel();busy=false;}else busy=!queue.isEmpty();}super.onDestroy();}
}
