package net.lanbridge.android;
import android.app.*;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ContentValues;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.util.concurrent.*;

/** Real HAPI page, isolated credentials; explicitly selected emulator only. */
public final class HapiInstrumentation extends Instrumentation {
    private Bundle args;private MainActivity activity;private Throwable problem;private int checks;
    @Override public void onCreate(Bundle a){args=a;start();}
    private void main(Runnable action){runOnMainSync(()->{try{action.run();}catch(Throwable e){problem=e;}});if(problem!=null)throw new AssertionError(problem);}
    private void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||(v instanceof TextView&&label.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View child=find(((ViewGroup)v).getChildAt(i),label);if(child!=null)return child;}return null;}
    private WebView browser(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=browser(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private String describe(View v){String text=v instanceof TextView?((TextView)v).getText().toString()+" ":"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)text+=describe(((ViewGroup)v).getChildAt(i));return text;}
    private ListView listing(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ListView list=listing(((ViewGroup)v).getChildAt(i));if(list!=null)return list;}return null;}
    private void capture(String name)throws Exception{android.graphics.Bitmap screen=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),name))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}screen.recycle();}
    private String js(WebView w,String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result={""};main(()->w.evaluateJavascript(script,v->{result[0]=v;latch.countDown();}));if(!latch.await(8,TimeUnit.SECONDS))throw new AssertionError("WebView callback timeout");return result[0];}
    private void tapAttachment(WebView w)throws Exception{
        String value=js(w,"JSON.stringify((function(){var b=document.querySelector('button[aria-label=\"添加文件\"]');var r=b.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth};})())");
        org.json.JSONObject point=new org.json.JSONObject((String)new org.json.JSONTokener(value).nextValue());
        main(()->{try{float scale=(float)(w.getWidth()/point.getDouble("width"));float x=(float)point.getDouble("x")*scale,y=(float)point.getDouble("y")*scale;long now=SystemClock.uptimeMillis();w.requestFocus();w.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));w.dispatchTouchEvent(MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0));}catch(Exception e){throw new AssertionError(e);}});
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        String origin=args.getString("origin"),token=args.getString("token"),session=args.getString("session");
        check(origin!=null&&origin.startsWith("http://10.0.2.2:"),"Only the emulator fixture is allowed");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("page","hapi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{HapiSettings.clear(activity);activity.show("home");activity.getFragmentManager().beginTransaction().remove(activity.getFragmentManager().findFragmentByTag("hapi")).commitNow();activity.show("hapi");});
        main(()->{View page=activity.getFragmentManager().findFragmentByTag("hapi").getView();((EditText)find(page,"电脑地址")).setText(origin);((EditText)find(page,"访问令牌")).setText(token);find(page,"连接").performClick();check(HapiSettings.origin(activity).equals(origin),"Saved origin differs");check(HapiSettings.token(activity).equals(token),"Encrypted credential failed");});
        WebView[] web={null};main(()->web[0]=browser(activity.getFragmentManager().findFragmentByTag("hapi").getView()));check(web[0]!=null,"No WebView");
        long end=SystemClock.elapsedRealtime()+30000;String text="";
        while(SystemClock.elapsedRealtime()<end){text=js(web[0],"document.body.innerText");if(text.contains("workspace"))break;SystemClock.sleep(300);}
        check(text.contains("workspace"),"HAPI authenticated session list missing: "+text.substring(0,Math.min(text.length(),350)));
        main(()->check(!web[0].getUrl().contains("token"),"Credential leaked into URL"));
        main(()->web[0].loadUrl(origin+"/sessions/"+session));end=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<end){text=js(web[0],"document.body.innerText");if(text.contains("REMOTE_STREAM_OK"))break;SystemClock.sleep(300);}
        check(text.contains("REMOTE_STREAM_OK"),"Real HAPI history missing: "+text.substring(0,Math.min(text.length(),350)));check(text.contains("INPUT_CONFIRMED"),"Remote answer history missing");
        // Use Android's actual content provider through the system-picker callback.
        ContentValues values=new ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"hapi-fixture.txt");values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"text/plain");
        Uri attachment=activity.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        try{
            try(java.io.OutputStream output=activity.getContentResolver().openOutputStream(attachment)){output.write("REMOTE_ATTACHMENT_FIXTURE".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            IntentFilter pickerFilter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);pickerFilter.addCategory(Intent.CATEGORY_OPENABLE);pickerFilter.addDataType("*/*");
            ActivityMonitor picker=addMonitor(pickerFilter,new ActivityResult(Activity.RESULT_OK,new Intent().setData(attachment)),true);
            js(web[0],"Array.from(document.querySelectorAll('button')).filter(b=>b.textContent.trim()==='知道了').forEach(b=>b.click())");
            tapAttachment(web[0]);
            end=SystemClock.elapsedRealtime()+15000;
            while(SystemClock.elapsedRealtime()<end){text=js(web[0],"document.body.innerText");if(text.contains("hapi-fixture.txt"))break;SystemClock.sleep(250);}
            removeMonitor(picker);check(text.contains("hapi-fixture.txt"),"System file picker did not attach URI; picker hits="+picker.getHits()+" buttons="+js(web[0],"JSON.stringify(Array.from(document.querySelectorAll('button')).slice(-18).map(b=>({text:b.textContent,aria:b.getAttribute('aria-label'),disabled:b.disabled})))"));
        }finally{activity.getContentResolver().delete(attachment,null,null);}
        main(()->{activity.show("home");activity.show("hapi");check(browser(activity.getFragmentManager().findFragmentByTag("hapi").getView())==web[0],"Switching tools recreated WebView");activity.setPocketFullscreen(true);check(activity.pocketFullscreen(),"Fullscreen not applied");check(find(activity.getFragmentManager().findFragmentByTag("hapi").getView(),"退出全屏").isShown(),"Exit missing");});
        try(android.os.ParcelFileDescriptor ignored=getUiAutomation().executeShellCommand("wm size 1800x1800")){SystemClock.sleep(800);}
        main(()->{check(activity.pocketFullscreen(),"Fold resize lost fullscreen");check(browser(activity.getFragmentManager().findFragmentByTag("hapi").getView())==web[0],"Fold resize reloaded the page");});
        try(android.os.ParcelFileDescriptor ignored=getUiAutomation().executeShellCommand("wm size reset")){SystemClock.sleep(500);}
        main(()->{activity.onBackPressed();check(!activity.pocketFullscreen(),"Back did not exit fullscreen");});
        main(()->{try{HapiSettings.save(activity,"http://10.0.2.2:1","");throw new AssertionError("Token crossed origins");}catch(java.io.IOException expected){}catch(Exception e){throw new AssertionError(e);}check(HapiSettings.origin(activity).equals(origin),"Failed save changed connection");});
        // One public entry, independent saved connections and retained browsers.
        main(()->{activity.show("tools");View catalog=activity.getFragmentManager().findFragmentByTag("tools").getView();check(find(catalog,"打开手机访问")!=null,"Unified catalog entry missing");check(find(catalog,"打开Codex 远程")==null,"Duplicate Codex entry remains");find(catalog,"打开手机访问").performClick();check(!activity.getFragmentManager().findFragmentByTag("hapi").isHidden(),"Entry forgot last provider");
            try{PocketSettings.save(activity,args.getString("dshOrigin"),"12345678");}catch(Exception e){throw new AssertionError(e);}
            find(activity.getFragmentManager().findFragmentByTag("hapi").getView(),"切换到DSH").performClick();});
        WebView[] dshWeb={null};main(()->dshWeb[0]=browser(activity.getFragmentManager().findFragmentByTag("pocket").getView()));check(dshWeb[0]!=null,"DSH browser missing");
        end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){text=js(dshWeb[0],"document.body.innerText");if(text.contains("DSH_FIXTURE_SESSION"))break;SystemClock.sleep(250);}check(text.contains("DSH_FIXTURE_SESSION"),"DSH login failed");
        main(()->{activity.setPocketFullscreen(true);check(find(activity.getFragmentManager().findFragmentByTag("pocket").getView(),"退出全屏").isShown(),"DSH fullscreen exit missing");activity.onBackPressed();find(activity.getFragmentManager().findFragmentByTag("pocket").getView(),"切换到Codex").performClick();check(browser(activity.getFragmentManager().findFragmentByTag("hapi").getView())==web[0],"Provider switch recreated Codex browser");check(HapiSettings.token(activity).equals(token)&&PocketSettings.pin(activity).equals("12345678"),"Provider credentials changed");
            find(activity.getFragmentManager().findFragmentByTag("hapi").getView(),"切换到DSH").performClick();check(browser(activity.getFragmentManager().findFragmentByTag("pocket").getView())==dshWeb[0],"Provider switch recreated DSH browser");activity.show("home");activity.show("remote");check(!activity.getFragmentManager().findFragmentByTag("pocket").isHidden(),"Remote entry did not retain DSH choice");activity.show("hapi");find(activity.getFragmentManager().findFragmentByTag("hapi").getView(),"电脑会话").performClick();});
        // Read the real native-history dialog, search across directories, select a
        // CLI-created thread, and verify its transcript in the retained HAPI view.
        androidx.appcompat.app.AlertDialog[] history={null};
        main(()->{try{java.lang.reflect.Field field=HapiFragment.class.getDeclaredField("sessionsDialog");field.setAccessible(true);Object owner=field.get(activity.getFragmentManager().findFragmentByTag("hapi"));field=HapiSessionsDialog.class.getDeclaredField("dialog");field.setAccessible(true);history[0]=(androidx.appcompat.app.AlertDialog)field.get(owner);}catch(Exception e){throw new AssertionError(e);}});
        main(()->((EditText)find(history[0].getWindow().getDecorView(),"搜索标题或目录")).setText("ANDROID_DESKTOP_FIXTURE"));
        end=SystemClock.elapsedRealtime()+30000;View[] desktopRow={null};while(SystemClock.elapsedRealtime()<end){main(()->desktopRow[0]=find(listing(history[0].getWindow().getDecorView()),"ANDROID_DESKTOP_FIXTURE"));if(desktopRow[0]!=null)break;SystemClock.sleep(250);}capture("remote-history.png");String[] diagnostic={""};main(()->diagnostic[0]=describe(history[0].getWindow().getDecorView()));check(desktopRow[0]!=null,"Native desktop history missing from dialog: "+diagnostic[0]);
        main(()->{View dialog=history[0].getWindow().getDecorView();((EditText)find(dialog,"搜索标题或目录")).setText("second-workspace");});SystemClock.sleep(300);
        main(()->{View dialog=history[0].getWindow().getDecorView();check(find(listing(dialog),"RESUME_DESKTOP_FIXTURE")!=null,"Directory search hid matching thread");((EditText)find(dialog,"搜索标题或目录")).setText("ANDROID_DESKTOP_FIXTURE");});SystemClock.sleep(300);
        main(()->{ListView list=listing(history[0].getWindow().getDecorView());check(list.getAdapter().getCount()==1,"Title search did not narrow to one thread");list.performItemClick(list.getChildAt(0),0,list.getAdapter().getItemId(0));});
        end=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<end){text=js(web[0],"document.body.innerText");if(text.contains("ANDROID_DESKTOP_FIXTURE")&&text.contains("HELLO_FROM_WINDOWS"))break;SystemClock.sleep(300);}check(text.contains("ANDROID_DESKTOP_FIXTURE")&&text.contains("HELLO_FROM_WINDOWS"),"Loaded desktop transcript missing");
        capture("remote-unified.png");result.putString("stream","PASS: "+checks+" Android checks; unified entry, DSH login, retained providers, local desktop history, private URL, attachment picker and fullscreen\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
