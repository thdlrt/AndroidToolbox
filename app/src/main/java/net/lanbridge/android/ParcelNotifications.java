package net.lanbridge.android;

import android.content.*;
import android.app.NotificationManager;
import android.os.SystemClock;
import android.provider.Telephony;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-open processing is independent of ordinary SMS permission and auto-scan settings. */
final class ParcelNotifications {
    private static ParcelNotifications instance;
    private final Context context;private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private long lastStart=-1;volatile boolean running;volatile long revision;volatile String status="";
    private ParcelNotifications(Context c){context=c.getApplicationContext();status=prefs(c).getString("status","");}
    static synchronized ParcelNotifications get(Context c){if(instance==null)instance=new ParcelNotifications(c);return instance;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("parcel-notifications",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static String sourcePackage(Context c){String value=prefs(c).getString("source","");if(value.isEmpty())value=Telephony.Sms.getDefaultSmsPackage(c);return value==null?"":value;}
    static ComponentName component(Context c){return new ComponentName(c,ParcelNotificationService.class);}
    static boolean access(Context c){return ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationListenerAccessGranted(component(c));}
    static void configure(Context c,boolean enabled,String source){if(enabled&&(source==null||!source.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")))throw new IllegalArgumentException("请选择短信来源应用");if(!prefs(c).edit().putBoolean("enabled",enabled).putString("source",source==null?"":source).commit())throw new IllegalStateException("无法保存通知导入设置");get(c).changed();}
    void changed(){revision++;}
    synchronized void captured(){lastStart=-1;changed();}
    void failed(Exception e){status="通知导入未完成："+(e.getMessage()==null?"请查看运行日志":e.getMessage());prefs(context).edit().putString("status",status).apply();changed();}
    void opened(){request(false);}
    synchronized void request(boolean force){long elapsed=SystemClock.elapsedRealtime();if(!enabled(context)||running||!force&&lastStart>=0&&elapsed-lastStart<60000)return;
        if(!access(context)){status="通知导入尚未获得通知访问权限";changed();return;}lastStart=elapsed;running=true;changed();
        worker.execute(()->{try{
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(context)){List<ParcelSms.Message> sources=inbox.pending(System.currentTimeMillis());if(sources.isEmpty()){status="";return;}if(!enabled(context)||!access(context))return;
                status="正在分析 "+sources.size()+" 条短信通知…";changed();int count=ParcelAutoReader.importNotificationItems(context,sources,new AiSettings(context).parcelConfig(true));inbox.done(sources);status="通知导入已更新 "+count+" 条取件信息";RuntimeLog.record(context,"通知入库","来源="+sources.size()+"；更新="+count);}
        }catch(Exception e){RuntimeLog.failure(context,"通知导入",e);failed(e);}finally{prefs(context).edit().putString("status",status).apply();running=false;changed();}});
    }
}
