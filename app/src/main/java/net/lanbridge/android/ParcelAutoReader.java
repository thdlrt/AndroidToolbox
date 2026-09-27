package net.lanbridge.android;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional foreground-open import. Uses application context and the same import lock as manual actions. */
final class ParcelAutoReader {
    private static ParcelAutoReader instance;
    private static final Object IMPORT_LOCK=new Object();
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ParcelAutoPolicy gate=new ParcelAutoPolicy();
    volatile boolean running;
    volatile long revision;
    volatile String status="";
    private ParcelAutoReader(Context c){context=c.getApplicationContext();status=prefs(context).getString("status","");}
    static synchronized ParcelAutoReader get(Context c){if(instance==null)instance=new ParcelAutoReader(c);return instance;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("parcel-auto-read",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static int days(Context c){return Math.max(1,Math.min(93,prefs(c).getInt("days",3)));}
    static void configure(Context c,boolean enabled,int days){ParcelAutoPolicy.days(days);if(!prefs(c).edit().putBoolean("enabled",enabled).putInt("days",days).commit())throw new IllegalStateException("无法保存自动读取设置");}
    void opened(){request(false);}
    synchronized void request(boolean force){if(!gate.begin(enabled(context),force,SystemClock.elapsedRealtime()))return;running=true;status="正在自动读取短信…";revision++;worker.execute(()->{
        try{
            if(context.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)throw new IllegalStateException("自动读取需要短信权限，请在取件设置中授予权限");
            ParcelAi.Config config=new AiSettings(context).parcelConfig(true);int days=days(context);long now=System.currentTimeMillis();
            List<ParcelSms.Message> messages=ParcelSms.query(context,ParcelAutoPolicy.start(now,days,java.time.ZoneId.systemDefault()),now);
            int count=importItems(context,messages,Collections.emptyList(),config,true);
            status=count==0?"自动读取完成，没有新的取件信息":"自动读取完成，已更新 "+count+" 条取件信息";
            prefs(context).edit().putLong("last_success",now).apply();
        }catch(Exception e){status="自动读取未完成："+(e.getMessage()==null?"请稍后重试":e.getMessage());}
        finally{prefs(context).edit().putString("status",status).apply();running=false;gate.end();revision++;}
    });}
    static int importItems(Context context,List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config config,boolean automatic)throws Exception{
        synchronized(IMPORT_LOCK){
            if(automatic&&!enabled(context))throw new IllegalStateException("自动读取已关闭");
            try(ParcelStore store=new ParcelStore(context.getApplicationContext())){
                List<ParcelSms.Message> pending=store.pendingSources(sources);if(pending.isEmpty())return 0;
                List<ParcelAi.Item> items=ParcelAi.parse(config,pending,images);return store.ingest(pending,items);
            }
        }
    }
}
