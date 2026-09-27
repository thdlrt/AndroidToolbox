package net.lanbridge.android;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Synthetic emulator-only SMS and encrypted cross-client acceptance. */
public final class AiParcelIntegration extends Instrumentation {
    private Bundle args;private int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    @Override public void onStart(){Bundle out=new Bundle();try{
        Context c=getTargetContext();String host=args.getString("endpoint");WebDavSettings.save(c,host+"/dav/","user","pass","AiFixture");
        AiSettings settings=new AiSettings(c);JSONObject downloaded=AiConfigSync.download(c);settings.importSnapshot(downloaded,settings.revision());
        check(settings.get().getString("model").equals("pc-fixture-model"),"PC profile import");
        String id=settings.saveProvider("mobile-fixture","Mobile fixture","openai",host+"/v1","fixture-mobile-key");
        String vid=settings.saveProvider("vision-fixture","Vision fixture","openai",host+"/vision/v1","fixture-vision-key");
        settings.saveRoles(id,"parcel-fixture-model",vid,"vision-fixture-model",true);
        check(settings.parcelConfig(false).key.equals("fixture-vision-key"),"legacy text uses multimodal provider");check(settings.parcelConfig(true).key.equals("fixture-vision-key"),"vision key routed");
        check(settings.get().getString("vision_model").equals("vision-fixture-model"),"vision form round trip");
        check(!settings.profiles().toString().contains("fixture-mobile-key"),"no key in UI response");
        String oldRevision=settings.revision();settings.saveRoles(id,"parcel-fixture-model",vid,"vision-fixture-model",true);try{settings.importSnapshot(downloaded,oldRevision);throw new AssertionError("stale import accepted");}catch(IOException expected){checks++;}
        AiConfigSync.upload(c);
        List<ParcelSms.Message> messages=new ArrayList<>();long now=System.currentTimeMillis();for(ParcelSms.Message m:ParcelSms.query(c,now-86400000L,now+1000))if(m.body.startsWith("PARCEL_FIXTURE_"))messages.add(m);
        check(messages.size()>=2,"actual SMS provider contains fixtures");String db="parcel-flow-"+System.nanoTime()+".db";
        try(ParcelStore store=new ParcelStore(c,db)){
            List<ParcelSms.Message> pending=store.pendingSources(messages);List<ParcelAi.Item> items=ParcelAi.parse(settings.parcelConfig(false),pending,Collections.emptyList());
            store.ingest(pending,items);check(store.list(false,"address").size()==2,"AI parsed two parcels");check(store.pendingSources(messages).isEmpty(),"source dedupe avoids repeat AI");
            String parcel=store.list(false,"address").get(0).id;store.setCollected(parcel,true);check(store.list(false,"address").size()==1&&store.list(true,"address").size()==1,"archive hides collected");
            store.setCollected(parcel,false);check(store.list(false,"locker").size()==2,"undo archive");
        }finally{c.deleteDatabase(db);}
        JSONObject result=new JSONObject().put("checks",checks).put("sms_messages",messages.size()).put("text_model",settings.parcelConfig(false).model).put("vision_model",settings.parcelConfig(true).model);
        try(FileOutputStream stream=c.openFileOutput("parcel-flow-result.json",Context.MODE_PRIVATE)){stream.write(result.toString().getBytes(StandardCharsets.UTF_8));}
        out.putString("stream","PASS: "+checks+" SMS/AI/config cross-client checks");finish(-1,out);
    }catch(Throwable e){out.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage());finish(0,out);}}
}
