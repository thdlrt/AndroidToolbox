package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.util.*;

/** Exercises the exported text share entry using an explicit emulator and synthetic content. */
public final class ParcelShareInstrumentation extends Instrumentation {
    private Bundle args;private int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private boolean visible(String text){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void await(String text){for(int i=0;i<200;i++){if(visible(text))return;SystemClock.sleep(100);}throw new AssertionError("Missing state "+text);}
    private void newest(MainActivity activity){runOnMainSync(()->{try{ParcelFragment fragment=(ParcelFragment)activity.getFragmentManager().findFragmentByTag("parcel");java.lang.reflect.Field sort=ParcelFragment.class.getDeclaredField("sort");sort.setAccessible(true);((android.widget.Spinner)sort.get(fragment)).setSelection(3);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
    private void share(Context c,CharSequence text){Intent intent=new Intent(Intent.ACTION_SEND).setClass(c,ParcelActivity.class).setType("text/plain").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(text!=null)intent.putExtra(Intent.EXTRA_TEXT,text);c.startActivity(intent);}
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();AiSettings ai=new AiSettings(c);JSONObject oldAi=null;android.content.SharedPreferences auto=ParcelAutoReader.prefs(c);boolean enabled=auto.getBoolean("enabled",false);MainActivity activity=null;boolean passed=false;String code=args.getString("code"),body="【丰巢】凭取件码"+code+"至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/ShareFixture";
        try{
            check(c.getPackageName().endsWith(".debug"),"Debug emulator only");
            check(c.checkSelfPermission(android.Manifest.permission.READ_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"Test must have no SMS permission");
            auto.edit().putBoolean("enabled",false).commit();oldAi=ai.exportSnapshot();
            String provider=ai.saveProvider("share-fixture","Share fixture","openai",args.getString("endpoint"),"fixture-key");ai.saveMultimodal(provider,"share-fixture");
            Intent implicit=new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(c.getPackageName());boolean found=false;for(android.content.pm.ResolveInfo info:c.getPackageManager().queryIntentActivities(implicit,0))if(info.activityInfo.name.equals(ParcelActivity.class.getName())&&info.activityInfo.exported)found=true;check(found,"Share chooser cannot find parcel entry");
            ActivityMonitor launch=addMonitor(MainActivity.class.getName(),null,false);share(c,body);activity=(MainActivity)waitForMonitorWithTimeout(launch,10000);removeMonitor(launch);check(activity!=null,"Cold share did not open main window");newest(activity);await("取件码："+code);check(visible("取件助手"),"Share routed to wrong page");
            try(ParcelStore store=new ParcelStore(c)){List<ParcelStore.Parcel> own=new ArrayList<>();for(ParcelStore.Parcel p:store.list(false,"address"))if(p.code.equals(code))own.add(p);check(own.size()==1,"Shared 5G text not ingested");check(own.get(0).sources.size()==1&&own.get(0).sources.get(0).body.equals(body)&&own.get(0).sources.get(0).sender.equals("短信分享"),"Shared source changed");}
            share(c," \n ");await("分享内容没有文字");
            MainActivity current=activity;ActivityMonitor recreated=addMonitor(MainActivity.class.getName(),null,false);runOnMainSync(current::recreate);activity=(MainActivity)waitForMonitorWithTimeout(recreated,10000);removeMonitor(recreated);check(activity!=null,"Recreation did not return window");waitForIdleSync();newest(activity);await("取件码："+code);SystemClock.sleep(500);
            String logs=new RuntimeLog(new java.io.File(c.getFilesDir(),"runtime-logs"),128*1024).read();check(logs.contains("分享导入")&&!logs.contains(code)&&!logs.contains(body)&&!logs.contains("fixture-key"),"Share logs leaked content");
            android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"parcel-share.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
            result.putString("stream","PASS: "+checks+" share checks: exported chooser, invalid text, actual forwarding, no SMS permission, exact source, strict fallback, recreation\n");passed=true;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{try{if(code!=null)try(ParcelStore store=new ParcelStore(c)){android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{for(ParcelStore.Parcel p:store.list(false,"address"))if(p.code.equals(code)&&p.sources.size()==1&&p.sources.get(0).sender.equals("短信分享")){String fingerprint=p.sources.get(0).fingerprint;db.delete("parcels","id=?",new String[]{p.id});db.delete("sources","fingerprint=?",new String[]{fingerprint});db.delete("processed","fingerprint=?",new String[]{fingerprint});}db.setTransactionSuccessful();}finally{db.endTransaction();}}if(oldAi!=null)ai.importSnapshot(oldAi,ai.revision());auto.edit().putBoolean("enabled",enabled).commit();}catch(Exception e){passed=false;result.putString("stream","Cleanup failed: "+e.getClass().getSimpleName());}}
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
