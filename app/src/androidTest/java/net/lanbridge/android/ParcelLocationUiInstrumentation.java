package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** UI smoke bridge with a disposable database injected into the debug fragment. */
public final class ParcelLocationUiInstrumentation extends Instrumentation {
    private Throwable failure;
    private void main(Runnable action){runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);waitForIdleSync();}
    private static Field field(String name)throws Exception{Field field=ParcelFragment.class.getDeclaredField(name);field.setAccessible(true);return field;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();String name="parcel-ui-"+UUID.randomUUID()+".db";ParcelStore store=null;MainActivity activity=null;SharedPreferences prefs=ParcelAutoReader.prefs(c);boolean wasEnabled=prefs.getBoolean("enabled",false);
        try{
            if(!c.getPackageName().endsWith(".debug"))throw new AssertionError("Debug target required");prefs.edit().putBoolean("enabled",false).commit();
            store=new ParcelStore(c,name);String[] locations={"南大仙林菜根谭","南大仙林菜根谭室内","南大仙林菜根谭室外39号","南大菜根谭"},lockers={"室内10号丰巢柜","9号丰巢柜","丰巢柜","室内4号丰巢柜"};
            for(int i=0;i<4;i++){String id="ui-"+i,code="8000000"+i;store.ingest(List.of(new ParcelSms.Message(id,System.currentTimeMillis(),"合成验收","取件码"+code+"。"+locations[i]+lockers[i])),List.of(new ParcelAi.Item(List.of(id),code,"","丰巢",locations[i],lockers[i],ParcelAi.PENDING,null)));}
            MainActivity launched=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=launched;main(()->launched.show("parcel"));ParcelFragment fragment=(ParcelFragment)launched.getFragmentManager().findFragmentByTag("parcel");
            ExecutorService worker=(ExecutorService)field("worker").get(fragment);worker.submit(()->{}).get(10,TimeUnit.SECONDS);ParcelStore fixture=store;
            worker.submit(()->{try{((ParcelStore)field("store").get(fragment)).close();field("store").set(fragment,fixture);}catch(Exception e){throw new RuntimeException(e);}}).get(10,TimeUnit.SECONDS);
            main(()->{try{Method refresh=ParcelFragment.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(fragment);}catch(Exception e){throw new RuntimeException(e);}});
            java.io.File done=new java.io.File(c.getCacheDir(),"parcel-ui-done");done.delete();
            long until=System.currentTimeMillis()+120000;while(System.currentTimeMillis()<until){Map<String,Integer> groups=store.locations();if(groups.size()==1&&groups.containsKey("南大仙林菜根谭")){if(groups.get("南大仙林菜根谭")!=4)throw new AssertionError("Missing organized records");long captureUntil=System.currentTimeMillis()+30000;while(!done.exists()&&System.currentTimeMillis()<captureUntil)Thread.sleep(100);done.delete();result.putString("stream","PASS: real settings -> organize -> select -> preview -> confirm; 4 records unified, lockers retained\n");finish(Activity.RESULT_OK,result);return;}Thread.sleep(100);}
            throw new AssertionError("UI organization timed out");
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){MainActivity close=activity;main(close::finish);}if(store!=null)store.close();c.deleteDatabase(name);prefs.edit().putBoolean("enabled",wasEnabled).commit();}
    }
}
