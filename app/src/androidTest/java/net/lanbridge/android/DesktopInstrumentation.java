package net.lanbridge.android;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.util.concurrent.*;

/** Isolated bridge fixture, selected emulator only. No real desktop writes. */
public final class DesktopInstrumentation extends Instrumentation {
    private MainActivity activity;private int checks;private Throwable problem;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}private Bundle args;
    private void main(Runnable r){runOnMainSync(()->{try{r.run();}catch(Throwable t){problem=t;}});if(problem!=null)throw new AssertionError(problem);}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=web(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private String js(WebView w,String code)throws Exception{CountDownLatch l=new CountDownLatch(1);String[] value={""};main(()->w.evaluateJavascript(code,x->{value[0]=x;l.countDown();}));if(!l.await(10,TimeUnit.SECONDS))throw new AssertionError("JavaScript timeout");return value[0];}
    private String await(WebView w,String text)throws Exception{long until=SystemClock.elapsedRealtime()+30000;String seen="";do{seen=js(w,"document.body.innerText");if(seen.contains(text))return seen;SystemClock.sleep(250);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("Missing "+text+": "+seen);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String origin=args.getString("origin"),token=args.getString("token");check(origin.startsWith("http://10.0.2.2:"),"Emulator fixture only");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{try{HapiSettings.save(activity,origin,token);}catch(Exception e){throw new AssertionError(e);}activity.show("hapi");});
        WebView[] w={null};main(()->w[0]=web(activity.getFragmentManager().findFragmentByTag("hapi").getView()));check(w[0]!=null,"Desktop WebView missing");
        String text=await(w[0],"正在处理的电脑任务");check(text.contains("等待电脑确认")&&text.contains("已完成的会话"),"Default list needs no search");
        check(js(w[0],"location.port").contains(String.valueOf(Integer.parseInt(android.net.Uri.parse(origin).getPort()+"")+1)),"Adjacent desktop port missing");check(!js(w[0],"location.href").contains(token),"Token leaked in URL");
        check(js(w[0],"document.getElementById('query').value").equals("\"\""),"Search unexpectedly required");
        js(w[0],"document.querySelector('[data-filter=active]').click()");check(!js(w[0],"document.getElementById('list').innerText").contains("已完成的会话"),"Running filter failed");
        js(w[0],"document.querySelector('#list .row').click()");await(w[0],"已读取实时输出");check(js(w[0],"document.body.classList.contains('chat')").equals("true"),"Mobile chat navigation missing");
        check(js(w[0],"document.querySelectorAll('#messages img').length").equals("0"),"Transcript HTML executed");check(!js(w[0],"document.getElementById('messages').innerText").contains("DO_NOT_EXPOSE"),"Reasoning exposed");
        js(w[0],"document.getElementById('prompt').value='手机继续同一条会话';document.getElementById('messageForm').requestSubmit()");await(w[0],"手机继续同一条会话");check(js(w[0],"document.getElementById('prompt').value").equals("\"\""),"Successful send did not clear composer");
        js(w[0],"document.getElementById('older').click()");await(w[0],"更早的电脑消息");check(!js(w[0],"document.body.innerText").contains("发送结果未确认"),"Send unexpectedly failed");
        String before=js(w[0],"document.getElementById('messages').innerText");SystemClock.sleep(3400);check(!before.equals(js(w[0],"document.getElementById('messages').innerText")),"Output did not refresh automatically");
        main(()->{activity.show("home");activity.show("hapi");check(web(activity.getFragmentManager().findFragmentByTag("hapi").getView())==w[0],"Switching tools recreated live page");activity.setPocketFullscreen(true);check(activity.pocketFullscreen(),"Fullscreen failed");activity.onBackPressed();check(!activity.pocketFullscreen(),"Back failed to exit fullscreen");});
        js(w[0],"document.getElementById('back').click()");check(js(w[0],"document.body.classList.contains('chat')").equals("false"),"Back to list failed");
        android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),"desktop-live.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);}image.recycle();
        result.putString("stream","PASS: "+checks+" live Desktop checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
