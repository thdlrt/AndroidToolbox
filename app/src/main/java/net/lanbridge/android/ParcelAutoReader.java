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
            ParcelScan scan;try(ParcelStore store=new ParcelStore(context)){scan=ParcelScan.read(context,ParcelAutoPolicy.start(now,days,java.time.ZoneId.systemDefault()),now,store,false);}
            int count=importItems(context,scan.pending,Collections.emptyList(),config,true);
            status=scan.pending.isEmpty()?"自动读取完成："+scan.emptyReason():count==0?"自动读取已分析 "+scan.pending.size()+" 条短信，AI 未生成新的取件记录":"自动读取完成，已更新 "+count+" 条取件信息";
            prefs(context).edit().putLong("last_success",now).apply();
        }catch(Exception e){RuntimeLog.failure(context,"自动读取",e);status="自动读取未完成："+(e.getMessage()==null?"请稍后重试":e.getMessage());}
        finally{prefs(context).edit().putString("status",status).apply();running=false;gate.end();revision++;}
    });}
    static int importItems(Context context,List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config config,boolean automatic)throws Exception{
        return importItems(context,sources,images,config,automatic,false);
    }
    static int importItems(Context context,List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config config,boolean automatic,boolean retryUnmatched)throws Exception{
        synchronized(IMPORT_LOCK){
            retryUnmatched=retryUnmatched&&!automatic;
            if(automatic&&!enabled(context))throw new IllegalStateException("自动读取已关闭");
            try(ParcelStore store=new ParcelStore(context.getApplicationContext())){
                List<ParcelSms.Message> pending=store.pendingSources(sources,retryUnmatched);if(pending.isEmpty()){RuntimeLog.record(context,"AI跳过","输入="+sources.size()+"；待处理=0；来源均已处理或重复");return 0;}
                RuntimeLog.record(context,"AI开始","来源="+pending.size()+"；图片="+images.size()+"；思考="+config.reasoning+"；自动="+automatic);
                try{List<ParcelAi.Item> items=ParcelAi.parse(config,pending,images);int waiting=0,delivering=0,collected=0,other=0;for(ParcelAi.Item item:items){if(item.status.equals(ParcelAi.PENDING))waiting++;else if(item.status.equals(ParcelAi.DELIVERY))delivering++;else if(item.status.equals(ParcelAi.COLLECTED))collected++;else other++;}RuntimeLog.record(context,"AI结果","待取="+waiting+"；派送="+delivering+"；已取="+collected+"；非快递="+other);int count=store.ingest(pending,items,retryUnmatched);RuntimeLog.record(context,"快递入库","更新="+count+"；来源="+pending.size());return count;}catch(Exception e){RuntimeLog.failure(context,"AI或入库",e);throw e;}
            }
        }
    }
}
