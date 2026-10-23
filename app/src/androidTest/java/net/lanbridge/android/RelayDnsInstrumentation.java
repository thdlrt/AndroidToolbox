package net.lanbridge.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

/** Controlled NXDOMAIN -> fresh native resolution, never a physical device. */
public final class RelayDnsInstrumentation extends Instrumentation {
    private Bundle args;private int checks;private MainActivity activity;private Throwable problem;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private void main(Runnable r){runOnMainSync(()->{try{r.run();}catch(Throwable t){problem=t;}});if(problem!=null)throw new AssertionError(problem);}
    private String control(String route)throws Exception{try(okhttp3.Response r=new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder().url(args.getString("control")+route).build()).execute()){return r.body().string();}}
    private View find(View v,String text){if(v==null)return null;if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View result=find(((ViewGroup)v).getChildAt(i),text);if(result!=null)return result;}return null;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        check(args.getString("control").startsWith("http://10.0.2.2:"),"Emulator fixture required");
        String host="cache-"+SystemClock.elapsedRealtime()+".relay-fixture.test";control("/dns/off");
        try{InetAddress.getAllByName(host);throw new AssertionError("Expected initial NXDOMAIN");}catch(UnknownHostException expected){check(true,"Initial failure reproduced");}
        control("/dns/on");String origin="http://"+host+":"+UriPort();
        try(RelayDav dav=new RelayDav(origin,"shared","user","fixture-password")){List<RelayDav.Entry> entries=dav.list("");check(entries.size()==1&&entries.get(0).name.equals("recover.txt"),"Native DNS recovery failed");}
        check(control("/stats").contains(host),"DNS fixture was bypassed");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{try{WebDavSettings.save(activity,origin,"user","fixture-password","shared");}catch(Exception e){throw new AssertionError(e);}activity.show("relay");});
        long until=SystemClock.elapsedRealtime()+15000;View[] row={null};do{main(()->row[0]=find(activity.getFragmentManager().findFragmentByTag("relay").getView(),"recover.txt"));if(row[0]!=null)break;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<until);check(row[0]!=null,"Recovered file list did not render");
        // An unavailable migration source must not hide the current working folder.
        main(()->{try{new LoginStore(activity,"webdav-migration-offline","webdav-migration-offline").save("http://offline.relay-fixture.test:"+UriPort(),"user","fixture-password","");activity.getSharedPreferences("webdav-layout",0).edit().putString("sources","[{\"id\":\"offline\",\"prefix\":\"old\",\"path\":\"legacy\",\"generation\":\"test\"}]").commit();activity.show("home");}catch(Exception e){throw new AssertionError(e);}});
        // Unknown prefixes are explicitly unavailable even while the new hostname works.
        main(()->{activity.show("relay");find(activity.getFragmentManager().findFragmentByTag("relay").getView(),"刷新").performClick();});until=SystemClock.elapsedRealtime()+18000;View[] warning={null};do{main(()->warning[0]=find(activity.getFragmentManager().findFragmentByTag("relay").getView(),"当前目录仍可使用"));if(warning[0]!=null)break;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<until);check(warning[0]!=null,"Missing retained-file migration warning");main(()->check(find(activity.getFragmentManager().findFragmentByTag("relay").getView(),"recover.txt")!=null,"Migration failure hid current files"));
        main(()->activity.getSharedPreferences("webdav-layout",0).edit().putString("sources","[]").commit());
        control("/dns/off");String missing="missing-"+SystemClock.elapsedRealtime()+".relay-fixture.test";
        main(()->{try{WebDavSettings.save(activity,"http://"+missing+":"+UriPort(),"user","fixture-password","shared");}catch(Exception e){throw new AssertionError(e);}activity.show("home");activity.show("relay");});
        until=SystemClock.elapsedRealtime()+18000;View[] error={null};do{main(()->error[0]=find(activity.getFragmentManager().findFragmentByTag("relay").getView(),"无法解析 WebDAV"));if(error[0]!=null)break;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<until);check(error[0]!=null,"Actionable DNS error missing");
        main(()->{View page=activity.getFragmentManager().findFragmentByTag("relay").getView();View diagnosis=find(page,"连接诊断");check(diagnosis!=null,"Connection diagnosis action missing");diagnosis.performClick();View target=find(activity.getFragmentManager().findFragmentByTag("diagnostics").getView(),missing);check(target!=null,"Diagnosis did not use failed WebDAV host");});
        result.putString("stream","PASS: "+checks+" Android native DNS and relay checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private int UriPort(){return android.net.Uri.parse(args.getString("control")).getPort();}
}
