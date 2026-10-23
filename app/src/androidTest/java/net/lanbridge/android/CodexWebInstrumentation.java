package net.lanbridge.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;
import java.util.concurrent.*;
import org.json.*;

/** Real shared-engine WebUI in the explicitly selected emulator, isolated homes. */
public final class CodexWebInstrumentation extends Instrumentation {
    private MainActivity activity;private int checks;private Throwable problem;private Bundle args;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private void main(Runnable r){runOnMainSync(()->{try{r.run();}catch(Throwable t){problem=t;}});if(problem!=null)throw new AssertionError(problem);}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||(v instanceof TextView&&label.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View child=find(((ViewGroup)v).getChildAt(i),label);if(child!=null)return child;}return null;}
    private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=web(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
    private String js(WebView w,String code)throws Exception{CountDownLatch l=new CountDownLatch(1);String[] value={""};main(()->w.evaluateJavascript(code,x->{value[0]=x;l.countDown();}));if(!l.await(10,TimeUnit.SECONDS))throw new AssertionError("JavaScript timeout");return value[0];}
    private String await(WebView w,String code,String contains)throws Exception{long until=SystemClock.elapsedRealtime()+30000;String seen="";do{seen=js(w,code);if(seen.contains(contains))return seen;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("Missing "+contains+": "+seen);}
    private String control(String route)throws Exception{try(okhttp3.Response r=new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder().url(args.getString("control")+route).build()).execute()){if(!r.isSuccessful())throw new AssertionError("Fixture control failed");return r.body().string();}}
    private String callsSince(int baseline)throws Exception{JSONArray all=new JSONArray(control("/requests")),recent=new JSONArray();for(int i=baseline;i<all.length();i++)recent.put(all.get(i));return recent.toString();}
    private void send(WebView w,String text)throws Exception{js(w,"document.querySelector('#promptInput').value="+JSONObject.quote(text)+";document.querySelector('#promptInput').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('#composer').requestSubmit()");}
    private void choose(WebView w,String mode)throws Exception{js(w,"document.querySelector('[data-follow-up-mode="+mode+"]').click()");}
    private void capture(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);}image.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String origin=args.getString("origin"),token=args.getString("token");check(origin.startsWith("http://10.0.2.2:"),"Emulator fixture only");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{try{CodexWebSettings.save(activity,origin+"/#token="+token,"");HapiSettings.save(activity,"http://10.0.2.2:39999","legacy-saved-token");}catch(Exception e){throw new AssertionError(e);}activity.show("hapi");});
        WebView[] w={null};main(()->w[0]=web(activity.getFragmentManager().findFragmentByTag("hapi").getView()));check(w[0]!=null,"WebUI browser missing");
        await(w[0],"document.body.innerText","REAL_STREAM_OK");
        check(js(w[0],"location.origin").contains(origin),"Explicit WebUI port changed");check(!js(w[0],"location.href").contains(token),"Token leaked in navigation URL");
        check(js(w[0],"localStorage.getItem('codex-webui-access-token')").contains(token),"Upstream auth store missing");
        check(js(w[0],"document.querySelector('#searchThreads').value").equals("\"\""),"Search required for desktop thread");
        check(js(w[0],"document.querySelector('#messages h2').textContent").contains("安卓渲染"),"Markdown heading missing");
        check(Integer.parseInt(js(w[0],"document.querySelectorAll('#messages table').length"))>0,"Markdown table missing");
        check(js(w[0],"document.querySelector('#messages pre code').textContent").contains("console.log"),"Code block missing");
        check(js(w[0],"!!window.fixtureXss").equals("false"),"Transcript script executed");
        check(js(w[0],"document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Mobile page overflows horizontally");capture("webui-mobile.png");
        main(()->{activity.show("home");activity.show("hapi");check(web(activity.getFragmentManager().findFragmentByTag("hapi").getView())==w[0],"Tool switch recreated live page");activity.setPocketFullscreen(true);check(activity.pocketFullscreen(),"Fullscreen failed");activity.onBackPressed();check(!activity.pocketFullscreen(),"Back failed to exit fullscreen");});
        String chosen=(String)new JSONTokener(js(w[0],"document.querySelector('#modelSelect').options[0].value")).nextValue();
        js(w[0],"document.querySelector('.settings-toggle').click();document.querySelector('#modelSelect').value="+JSONObject.quote(chosen)+";document.querySelector('#modelSelect').dispatchEvent(new Event('change',{bubbles:true}))");
        await(w[0],"document.querySelector('#modelSelect').disabled","false");capture("webui-settings.png");
        js(w[0],"document.querySelector('#reasoningSelect').value='high';document.querySelector('#reasoningSelect').dispatchEvent(new Event('change',{bubbles:true}))");SystemClock.sleep(500);
        js(w[0],"document.querySelector('.settings-toggle').click()");
        await(w[0],"document.querySelector('#reasoningSelect').value","high");
        int baseline=new JSONArray(control("/requests")).length();control("/hold");send(w[0],"ANDROID_LIVE_STREAM");await(w[0],"document.querySelector('#sendButton').getAttribute('aria-haspopup')","menu");
        String before=js(w[0],"document.querySelector('#messages').innerText");
        await(w[0],"document.querySelector('.streaming-reply .bubble')?.innerText","REAL_STREAM_");
        check(!js(w[0],"document.querySelector('.streaming-reply .bubble')?.innerText").contains("REAL_STREAM_OK"),"Response only rendered after completion");
        send(w[0],"ANDROID_STEER");choose(w[0],"steer");await(w[0],"document.querySelector('#promptInput').value","\"\"");
        send(w[0],"ANDROID_QUEUE");choose(w[0],"queue");await(w[0],"document.querySelector('#promptInput').value","\"\"");
        control("/release");String requests="";long end=SystemClock.elapsedRealtime()+30000;do{requests=callsSince(baseline);if(requests.contains("ANDROID_QUEUE"))break;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<end);
        check(requests.contains("ANDROID_LIVE_STREAM"),"Android send missing from model input");check(requests.contains("ANDROID_STEER"),"Android steer not applied");check(requests.contains("ANDROID_QUEUE"),"Android queue did not execute");
        JSONArray calls=new JSONArray(requests);boolean effort=false,model=false;for(int i=0;i<calls.length();i++){JSONObject call=calls.getJSONObject(i);if(call.toString().contains("ANDROID_LIVE_STREAM")){if("high".equals(call.optJSONObject("reasoning")==null?"":call.getJSONObject("reasoning").optString("effort")))effort=true;if(chosen.equals(call.optString("model")))model=true;}}check(effort,"Reasoning selection did not reach engine");check(model,"Model selection did not reach engine");
        await(w[0],"document.querySelector('#sendButton').getAttribute('aria-haspopup')","false");check(!before.equals(js(w[0],"document.querySelector('#messages').innerText")),"Streaming response missing");
        control("/hold");send(w[0],"ANDROID_STOP");await(w[0],"document.querySelector('#sendButton').getAttribute('aria-haspopup')","menu");js(w[0],"document.querySelector('#sendButton').click()");choose(w[0],"stop");await(w[0],"document.querySelector('#sendButton').getAttribute('aria-haspopup')","false");control("/release");check(true,"Android stop delivered");
        main(()->w[0].reload());await(w[0],"document.querySelector('#messages').innerText","ANDROID_QUEUE");check(!js(w[0],"location.href").contains(token),"Reload put token in URL");
        main(()->{check(HapiSettings.token(activity).equals("legacy-saved-token"),"Legacy HAPI credential changed");activity.show("tools");check(find(activity.getFragmentManager().findFragmentByTag("tools").getView(),"打开手机访问")!=null,"Unified remote catalog missing");activity.show("hapi");});
        String raw=js(w[0],"JSON.stringify((()=>{const r=document.querySelector('#promptInput').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})())");JSONObject point=new JSONObject((String)new JSONTokener(raw).nextValue());
        main(()->{try{float scale=(float)(w[0].getWidth()/point.getDouble("w"));float x=(float)point.getDouble("x")*scale,y=(float)point.getDouble("y")*scale;long now=SystemClock.uptimeMillis();w[0].dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));w[0].dispatchTouchEvent(MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0));}catch(Exception e){throw new AssertionError(e);}});
        SystemClock.sleep(800);check(js(w[0],"document.querySelector('#composer').getBoundingClientRect().bottom<=visualViewport.height+visualViewport.offsetTop+2").equals("true"),"Keyboard covers composer");capture("webui-keyboard.png");
        result.putString("stream","PASS: "+checks+" real Codex WebUI Android checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){try{control("/release");}catch(Exception ignored){}result.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
