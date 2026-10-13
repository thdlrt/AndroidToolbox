package net.lanbridge.android;

import android.content.Context;
import android.database.Cursor;
import java.io.IOException;
import java.util.*;

final class ParcelEditInstrumentation {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void ingest(ParcelStore store,String source,long date,String code,String track,String carrier,String place,String locker,String deadline)throws Exception{
        String body="提醒 "+source+"：取件码 "+code+"，运单 "+track+"，"+carrier+place+locker+"，"+(deadline==null?"":deadline);
        store.ingest(List.of(new ParcelSms.Message(source,date,"fixture",body)),List.of(new ParcelAi.Item(List.of(source),code,track,carrier,place,locker,ParcelAi.PENDING,deadline)));
    }
    private static ParcelStore.Parcel get(ParcelStore store,String id){for(boolean archive:new boolean[]{false,true})for(ParcelStore.Parcel p:store.list(archive,"address"))if(p.id.equals(id))return p;throw new AssertionError("Missing record");}
    private static long aliases(ParcelStore store){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM parcel_identity_aliases",null)){c.moveToFirst();return c.getLong(0);}}
    private static ParcelStore.Edit unchanged(ParcelStore.Parcel p)throws IOException{return new ParcelStore.Edit(p.code,p.trackingNumber,p.carrier,p.location,p.locker,p.deadline);}
    static int run(Context context)throws Exception{
        checks=0;String name="parcel-edit-"+UUID.randomUUID()+".db";ParcelStore store=new ParcelStore(context,name);long now=System.currentTimeMillis();
        try{
            ingest(store,"first",now,"1234","TRACK1","丰巢","北门","A柜","明晚");ParcelStore.Parcel original=store.list(false,"address").get(0);store.edit(original,unchanged(original));check(!get(store,original.id).manuallyEdited&&get(store,original.id).updatedAt==original.updatedAt,"Unchanged save locked fields");
            store.setCollected(original.id,true);ParcelStore.Parcel archived=get(store,original.id);store.edit(archived,new ParcelStore.Edit("5678","TRACK2","中通","南门室外9号","丰巢柜","后天"));ParcelStore.Parcel edited=get(store,original.id);
            check(edited.code.equals("5678")&&edited.trackingNumber.equals("TRACK2")&&edited.carrier.equals("中通")&&edited.deadline.equals("后天"),"Fields not edited");check(edited.location.equals("南门")&&edited.locker.equals("室外9号丰巢柜"),"Editor broke location formatting");check(edited.collected&&edited.manualCollected&&edited.sources.size()==1&&edited.sources.get(0).body.equals(original.sources.get(0).body)&&edited.receivedAt==original.receivedAt,"Edit lost archive/source/date");check(edited.manuallyEdited,"Edit protection absent");
            store.close();store=new ParcelStore(context,name);check(get(store,original.id).manuallyEdited,"Edit not durable");
            ingest(store,"old-reminder",now+1000,"1234","TRACK1","丰巢","北门","A柜","明晚");ParcelStore.Parcel reminder=get(store,original.id);check(store.list(true,"address").size()==1&&store.list(false,"address").isEmpty(),"Old identity made duplicate/resurrected parcel");check(reminder.code.equals("5678")&&reminder.trackingNumber.equals("TRACK2")&&reminder.carrier.equals("中通")&&reminder.location.equals("南门")&&reminder.locker.equals("室外9号丰巢柜")&&reminder.deadline.equals("后天"),"AI overwrote manual fields");check(reminder.sources.size()==2,"Reminder source discarded");
            store.edit(reminder,new ParcelStore.Edit("7890","TRACK3","中通","南门","室外9号丰巢柜",""));ingest(store,"middle-reminder",now+2000,"5678","TRACK2","中通","南门","室外9号丰巢柜","后天");check(get(store,original.id).code.equals("7890")&&get(store,original.id).deadline==null&&store.list(true,"newest").size()==1,"Repeated edit identity/clear protection failed");
            ingest(store,"no-track",now,"9090","","","西门","B柜",null);ParcelStore.Parcel noTrack=null;for(ParcelStore.Parcel p:store.list(false,"newest"))if(p.code.equals("9090"))noTrack=p;store.edit(noTrack,new ParcelStore.Edit("9091","","申通","东门","C柜",null));
            ingest(store,"no-track-reminder",now+1000,"9090","","","西门","B柜",null);check(store.list(false,"newest").size()==1&&get(store,noTrack.id).code.equals("9091")&&get(store,noTrack.id).sources.size()==2,"Untracked old identity duplicated or overwrote edit");
            ingest(store,"other-locker",now+2000,"9090","","","西门","D柜",null);check(store.list(false,"newest").size()==2,"Identity aliases merged different cabinets");
            ingest(store,"later",now+5L*86400000,"9090","","","西门","B柜",null);check(store.list(false,"newest").size()==3,"Identity alias ignored 72-hour limit");
            ingest(store,"partial",now,"1111","","","北门","X柜",null);ParcelStore.Parcel partial=null;for(ParcelStore.Parcel p:store.list(false,"newest"))if(p.code.equals("1111"))partial=p;store.edit(partial,new ParcelStore.Edit("1111","","","南站","X柜",null));ingest(store,"fill-fields",now+1000,"1111","NEWTRACK","韵达","北门","X柜",null);check(get(store,partial.id).location.equals("南站")&&get(store,partial.id).trackingNumber.equals("NEWTRACK")&&get(store,partial.id).carrier.equals("韵达"),"Unedited fields stopped receiving evidence");
            ParcelStore.Parcel stale=get(store,partial.id);store.getWritableDatabase().execSQL("UPDATE parcels SET updated_at=updated_at+1 WHERE id=?",new Object[]{stale.id});try{store.edit(stale,new ParcelStore.Edit("9999","","","错误地点","",null));throw new AssertionError("Stale edit accepted");}catch(IOException expected){checks++;}check(get(store,partial.id).code.equals("1111"),"Stale save changed fields");
            long beforeAliases=aliases(store);ParcelStore.Parcel before=get(store,original.id);store.getWritableDatabase().execSQL("CREATE TRIGGER reject_manual BEFORE UPDATE OF code ON parcels WHEN NEW.code='FAIL' BEGIN SELECT RAISE(ABORT,'synthetic'); END");try{store.edit(before,new ParcelStore.Edit("FAIL",before.trackingNumber,before.carrier,before.location,before.locker,before.deadline));throw new AssertionError("Write failure accepted");}catch(IOException expected){checks++;}store.getWritableDatabase().execSQL("DROP TRIGGER reject_manual");check(get(store,original.id).code.equals(before.code)&&aliases(store)==beforeAliases,"Failed edit left partial fields/aliases");
            for(String bad:new String[]{String.join("",Collections.nCopies(129,"x")),"12\n34","12\0"})try{new ParcelStore.Edit(bad,"","","","",null);throw new AssertionError("Invalid field accepted");}catch(IOException expected){checks++;}
            return checks;
        }finally{store.close();context.deleteDatabase(name);}
    }
}
