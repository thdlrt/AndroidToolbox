package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** Actual system listener callbacks, local queue persistence and foreground-only AI. */
public final class ParcelNotificationInstrumentation extends Instrumentation {
    private Bundle args;private int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private void waitFor(java.util.function.BooleanSupplier ready,String state){for(int i=0;i<300;i++){if(ready.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError("Missing state "+state);}
    private void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){Object value=entry.getValue();if(value instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)value);else if(value instanceof String)edit.putString(entry.getKey(),(String)value);else if(value instanceof Integer)edit.putInt(entry.getKey(),(Integer)value);else if(value instanceof Long)edit.putLong(entry.getKey(),(Long)value);}edit.commit();}
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();boolean passed=false;AiSettings ai=new AiSettings(c);JSONObject oldAi=null;SharedPreferences notifications=ParcelNotifications.prefs(c),auto=ParcelAutoReader.prefs(c),filter=c.getSharedPreferences("parcel-sms-filter",0);Map<String,?> oldNotifications=notifications.getAll(),oldAuto=auto.getAll(),oldFilter=filter.getAll();String code=args.getString("code"),body="【丰巢】凭取件码"+code+"至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/NotificationFixture";NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);MainActivity activity=null;
        try{
            check(c.getPackageName().endsWith(".debug"),"Explicit debug emulator only");waitFor(()->ParcelNotifications.access(c),"listener permission granted by emulator host");check(ParcelNotifications.access(c),"Listener permission not granted by emulator host");
            check(c.checkSelfPermission(android.Manifest.permission.READ_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"SMS permission must remain denied");
            auto.edit().putBoolean("enabled",false).commit();oldAi=ai.exportSnapshot();String provider=ai.saveProvider("notification-fixture","Notification fixture","openai",args.getString("endpoint"),"fixture-key");ai.saveMultimodal(provider,"notification-fixture");ParcelSmsFilter.save(c,true,code);
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Existing pending fixtures must be preserved");}
            ParcelNotifications.configure(c,true,c.getPackageName());android.service.notification.NotificationListenerService.requestRebind(ParcelNotifications.component(c));waitFor(()->ParcelNotificationService.connected,"listener connected");
            manager.createNotificationChannel(new NotificationChannel("parcel-listener-fixture","Listener fixture",NotificationManager.IMPORTANCE_DEFAULT));
            Notification.Builder notification=new Notification.Builder(c,"parcel-listener-fixture").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("丰巢").setContentText("简略通知").setStyle(new Notification.BigTextStyle().bigText(body));
            check(ParcelNotificationService.extract(notification.build(),System.currentTimeMillis()).get(0).body.equals(body),"BigText precedence lost full content");
            Notification.MessagingStyle style=new Notification.MessagingStyle(new Person.Builder().setName("测试收件人").build()).addMessage(body,System.currentTimeMillis(),new Person.Builder().setName("丰巢").build());Notification messaging=new Notification.Builder(c,"parcel-listener-fixture").setSmallIcon(android.R.drawable.ic_dialog_email).setStyle(style).build();check(ParcelNotificationService.extract(messaging,System.currentTimeMillis()).get(0).body.equals(body),"MessagingStyle body lost");
            ParcelNotifications.configure(c,false,c.getPackageName());manager.notify(7301,notification.build());SystemClock.sleep(350);try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Disabled listener captured content");}
            ParcelNotifications.configure(c,true,"com.example.unselected");manager.notify(7301,notification.build());SystemClock.sleep(350);try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Wrong source captured content");}
            ParcelNotifications.configure(c,true,c.getPackageName());manager.notify(7301,new Notification.Builder(c,"parcel-listener-fixture").setSmallIcon(android.R.drawable.ic_dialog_email).setContentText("验证码 889900").build());SystemClock.sleep(350);try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Nonparcel notification stored");}
            manager.notify(7301,notification.build());waitFor(()->{try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){return inbox.count(System.currentTimeMillis())==1;}},"system notification persisted");manager.notify(7301,notification.build());SystemClock.sleep(300);
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){List<ParcelSms.Message> pending=inbox.pending(System.currentTimeMillis());check(pending.size()==1&&pending.get(0).body.equals(body),"Repeated update changed or duplicated source");}
            try(ParcelStore store=new ParcelStore(c)){check(store.list(false,"newest").stream().noneMatch(p->p.code.equals(code)),"Background listener unexpectedly generated a parcel");}
            java.net.HttpURLConnection probe=(java.net.HttpURLConnection)new java.net.URL(args.getString("endpoint")+"/before-open").openConnection();try{check(probe.getResponseCode()==200,"Background listener called AI");}finally{probe.disconnect();}
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("page","parcel"));
            waitFor(()->!ParcelNotifications.get(c).running,"failed request completed");
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==1,"Network failure discarded cached notification");}
            try(ParcelStore store=new ParcelStore(c)){check(store.list(false,"newest").stream().noneMatch(p->p.code.equals(code)),"Network failure reported success");}
            ParcelNotifications.get(c).request(true);
            waitFor(()->{try(ParcelStore store=new ParcelStore(c)){return store.list(false,"newest").stream().anyMatch(p->p.code.equals(code));}},"foreground notification import");waitFor(()->!ParcelNotifications.get(c).running,"foreground import finished");
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Successful queue item not marked done");}
            manager.notify(7301,notification.build());SystemClock.sleep(350);try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){check(inbox.count(System.currentTimeMillis())==0,"Processed notification update requeued");}
            String logs=new RuntimeLog(new java.io.File(c.getFilesDir(),"runtime-logs"),128*1024).read();check(logs.contains("短信通知")&&logs.contains("通知入库")&&!logs.contains(code)&&!logs.contains(body)&&!logs.contains("fixture-key"),"Notification logs leaked private data");
            MainActivity host=activity;runOnMainSync(()->ParcelNotificationSettings.show(host,()->{}));waitForIdleSync();android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"parcel-notifications.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
            result.putString("stream","PASS: "+checks+" notification checks: system callback, disabled/source/keyword gates, BigText/MessagingStyle, persistent queue, update dedupe, no background AI, failed request retained for retry, foreground-only import without SMS permission, privacy\n");passed=true;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{try{manager.cancel(7301);try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(c)){inbox.getWritableDatabase().delete("incoming","body=?",new String[]{body});}try(ParcelStore store=new ParcelStore(c)){android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{for(ParcelStore.Parcel p:store.list(false,"newest"))if(p.code.equals(code)&&p.sources.size()==1&&p.sources.get(0).body.equals(body)){String fingerprint=p.sources.get(0).fingerprint;db.delete("parcels","id=?",new String[]{p.id});db.delete("sources","fingerprint=?",new String[]{fingerprint});db.delete("processed","fingerprint=?",new String[]{fingerprint});}db.setTransactionSuccessful();}finally{db.endTransaction();}}if(oldAi!=null)ai.importSnapshot(oldAi,ai.revision());restore(notifications,oldNotifications);restore(auto,oldAuto);restore(filter,oldFilter);}catch(Exception e){passed=false;result.putString("stream","Cleanup failed: "+e.getClass().getSimpleName());}}
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
