package net.lanbridge.android;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Synthetic SMS + local HTTP fixture only; target an explicit SDK emulator. */
public final class ParcelAutoInstrumentation extends Instrumentation {
 private Bundle args;private int checks;
 @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 private void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 private void idle(ParcelAutoReader reader)throws Exception{long until=System.currentTimeMillis()+15000;while(reader.running&&System.currentTimeMillis()<until)Thread.sleep(40);check(!reader.running,"automatic job did not finish");}
 @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();JSONObject old=null;boolean enabled=ParcelAutoReader.enabled(c);int days=ParcelAutoReader.days(c);try{
  AiSettings settings=new AiSettings(c);old=settings.exportSnapshot();String provider=settings.saveProvider("auto-fixture","Auto fixture","openai",args.getString("endpoint"),"fixture-key");settings.saveMultimodal(provider,"multimodal-fixture");ParcelAutoReader.configure(c,true,1);
  ParcelAutoReader reader=ParcelAutoReader.get(c);startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();idle(reader);
  check(reader.status.contains("HTTP 503"),"first failed request should be visible");
  reader.request(true);idle(reader);check(reader.status.contains("完成"),"retry should complete");
  try(ParcelStore store=new ParcelStore(c)){boolean found=false;for(ParcelStore.Parcel p:store.list(false,"address"))if(p.code.equals("064-123"))found=true;check(found,"foreground opening did not import fixture parcel");}
  reader.request(true);idle(reader);check(reader.status.contains("没有新的"),"repeat source not deduplicated");
  long revision=reader.revision;reader.opened();check(reader.revision==revision,"rapid activity return triggered scan");ParcelAutoReader.configure(c,false,1);reader.request(true);check(reader.revision==revision,"disabled still runs");
  result.putString("stream","PASS: "+checks+" automatic read assertions: app start, error, retry, SQLite, dedupe, cooldown and disable\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{try{ParcelAutoReader.configure(c,enabled,days);if(old!=null)new AiSettings(c).importSnapshot(old,new AiSettings(c).revision());}catch(Exception ignored){}}}
}
