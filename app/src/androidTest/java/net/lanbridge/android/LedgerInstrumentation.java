package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Emulator-only integration bridge. Never included in release APK. */
public class LedgerInstrumentation extends Instrumentation {
    private Bundle args;private Throwable failure;private int assertions;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args==null?new Bundle():args;start();}
    private void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private void main(Runnable runnable){runOnMainSync(()->{try{runnable.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);waitForIdleSync();}
    private Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private void invoke(Object object,String name,Class<?>[] types,Object... arguments)throws Exception{Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(object,arguments);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();String action=args.getString("action","ui");
        if(action.equals("seed")){
            LedgerStore local=new LedgerStore(new File(context.getFilesDir(),"ledger-v1"));String id=LedgerStore.id();JSONObject values=new JSONObject().put("title",args.getString("title","Android camera fixture")).put("date","2026-09-26").put("amount","123.45").put("amount_minor",12345).put("currency","CNY").put("entry_type","expense").put("status","waiting").put("category","测试").put("notes","Android seed").put("project_id",args.getString("project_id",LedgerStore.AI_PROJECT)).put("created_at",System.currentTimeMillis()/1000.0);
            File photo=File.createTempFile("receipt-",".png",context.getCacheDir());try(FileOutputStream output=new FileOutputStream(photo)){Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}JSONObject attachment=local.attach(id,photo,"android-receipt.png","receipt");photo.delete();values.put("attachment:"+attachment.getString("id"),attachment);local.patch("entry",id,values,false);
            if(args.containsKey("edit_id")){JSONObject change=new JSONObject().put("notes",args.getString("note","Android changed notes"));if(args.containsKey("edit_title"))change.put("title",args.getString("edit_title"));local.patch("entry",args.getString("edit_id"),change,false);}
        }
        if(args.containsKey("endpoint")){WebDavSettings.store(context).save(args.getString("endpoint"),args.getString("username","test"),args.getString("password","test"),"");context.getSharedPreferences("ledger",0).edit().putString("remote_path",args.getString("remote_path","WinToolbox")).commit();}
        LedgerSync sync=LedgerSync.get(context);
        if(action.equals("ui"))ui(context);if(action.equals("external"))external(context);
        if(args.containsKey("endpoint")){sync.sync();CountDownLatch done=new CountDownLatch(1);sync.submit(done::countDown);check(done.await(120,TimeUnit.SECONDS),"WebDAV sync timed out");check(!sync.status.contains("失败"),sync.status);}
        LedgerStore local=sync.store();JSONObject output=new JSONObject().put("status",sync.status).put("operations",new JSONArray(local.operations())).put("entries",local.list("entry")).put("projects",local.list("project")).put("network_profiles",local.list("network_profile"));LedgerStore.write(new File(context.getFilesDir(),"ledger-test-result.json"),output.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        result.putString("stream","PASS: "+assertions+" ledger assertions; "+sync.status+"; files/ledger-test-result.json\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
    private void external(Context context)throws Exception{
        MainActivity activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));final LedgerFragment[] holder=new LedgerFragment[1];
        main(()->{activity.show("ledger");holder[0]=(LedgerFragment)activity.getFragmentManager().findFragmentByTag("ledger");try{invoke(holder[0],"edit",new Class<?>[]{JSONObject.class},new Object[]{null});((EditText)field(holder[0],"title")).setText("External camera and picker fixture");((EditText)field(holder[0],"amount")).setText("35.00");invoke(holder[0],"camera",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
        awaitAttachments(holder[0],1);check(true,"Actual camera photo returned");
        ContentValues metadata=new ContentValues();metadata.put(MediaStore.MediaColumns.DISPLAY_NAME,"ledger-external-fixture.png");metadata.put(MediaStore.MediaColumns.MIME_TYPE,"image/png");metadata.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download");Uri download=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,metadata);check(download!=null,"Create picker fixture");try(OutputStream out=context.getContentResolver().openOutputStream(download)){Bitmap bitmap=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}
        main(()->{try{invoke(holder[0],"pick",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});awaitAttachments(holder[0],2);check(true,"Actual document picker result returned");context.getContentResolver().delete(download,null,null);
    }
    private void awaitAttachments(LedgerFragment fragment,int expected)throws Exception{long end=SystemClock.elapsedRealtime()+120000;while(SystemClock.elapsedRealtime()<end){final int[] count={0};main(()->{try{JSONObject draft=(JSONObject)field(fragment,"draft");if(draft!=null)for(String key:LedgerStore.keys(draft))if(key.startsWith("attachment:")&&!draft.isNull(key))count[0]++;}catch(Exception e){throw new RuntimeException(e);}});if(count[0]>=expected)return;SystemClock.sleep(300);}throw new AssertionError("External app did not return attachment "+expected);}
    private void ui(Context context)throws Exception{
        MainActivity activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));final LedgerFragment[] holder=new LedgerFragment[1];
        main(()->{activity.show("ledger");holder[0]=(LedgerFragment)activity.getFragmentManager().findFragmentByTag("ledger");check(holder[0].getView()!=null,"Ledger pane missing");try{invoke(holder[0],"edit",new Class<?>[]{JSONObject.class},new Object[]{null});((EditText)field(holder[0],"title")).setText("Camera UI fixture");((EditText)field(holder[0],"amount")).setText("25.50");}catch(Exception e){throw new RuntimeException(e);}});
        LedgerFragment fragment=holder[0];final Intent[] captured=new Intent[1];
        ActivityMonitor camera=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(MediaStore.ACTION_IMAGE_CAPTURE.equals(intent.getAction())){captured[0]=intent;try{Uri uri=intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);try(OutputStream out=context.getContentResolver().openOutputStream(uri)){Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED);bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);bitmap.recycle();}return new ActivityResult(Activity.RESULT_OK,new Intent());}catch(Exception e){throw new RuntimeException(e);}}return null;}};
        addMonitor(camera);main(()->{try{invoke(fragment,"camera",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});removeMonitor(camera);
        check(captured[0]!=null,"System camera intent missing");check((captured[0].getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0,"Camera write grant missing");check(captured[0].getClipData()!=null,"Camera ClipData missing");
        CountDownLatch imported=new CountDownLatch(1);LedgerSync.get(context).submit(imported::countDown);check(imported.await(10,TimeUnit.SECONDS),"Camera attachment import timeout");waitForIdleSync();
        main(()->{try{JSONObject draft=(JSONObject)field(fragment,"draft");int count=0;for(String key:LedgerStore.keys(draft))if(key.startsWith("attachment:")){count++;JSONObject a=draft.getJSONObject(key);check(a.getLong("size")>0,"Empty camera photo");check(new File(LedgerSync.get(context).store().blobs,a.getString("sha256")).isFile(),"Camera blob not durable");}check(count==1,"Camera result did not attach image");}catch(Exception e){throw new RuntimeException(e);}});
        final Intent[] picked=new Intent[1];ActivityMonitor picker=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){picked[0]=intent;return new ActivityResult(Activity.RESULT_OK,new Intent().setData(captured[0].getParcelableExtra(MediaStore.EXTRA_OUTPUT)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}return null;}};addMonitor(picker);main(()->{try{invoke(fragment,"pick",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});removeMonitor(picker);check(picked[0]!=null,"System document picker missing");CountDownLatch imported2=new CountDownLatch(1);LedgerSync.get(context).submit(imported2::countDown);check(imported2.await(10,TimeUnit.SECONDS),"Picker import timeout");waitForIdleSync();
        main(()->{try{JSONObject draft=(JSONObject)field(fragment,"draft");int count=0;for(String key:LedgerStore.keys(draft))if(key.startsWith("attachment:"))count++;check(count==2,"Picker did not import attachment");invoke(fragment,"capture",new Class<?>[]{});draft.put("amount","25.50").put("amount_minor",2550).put("created_at",System.currentTimeMillis()/1000.0);LedgerSync.get(context).store().patch("entry",(String)field(fragment,"editingId"),draft,false);activity.show("diagnostics");check(activity.getFragmentManager().findFragmentByTag("diagnostics").getView()!=null,"Diagnostics pane missing");}catch(Exception e){throw new RuntimeException(e);}});
    }
}
