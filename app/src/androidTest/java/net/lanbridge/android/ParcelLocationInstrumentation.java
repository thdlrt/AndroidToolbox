package net.lanbridge.android;

import android.content.*;
import java.io.IOException;
import java.util.*;

/** Real SQLite migration/alias acceptance; all data lives in a disposable test database. */
final class ParcelLocationInstrumentation {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void ingest(ParcelStore store,String id,String code,String location,String locker)throws Exception{
        store.ingest(List.of(new ParcelSms.Message(id,1800000000000L,"fixture",id+" 提醒：取件码"+code+"。"+location+locker)),List.of(new ParcelAi.Item(List.of(id),code,"","丰巢",location,locker,ParcelAi.PENDING,null)));
    }
    private static ParcelStore.Parcel code(ParcelStore store,String code){for(boolean archived:new boolean[]{false,true})for(ParcelStore.Parcel parcel:store.list(archived,"address"))if(parcel.code.equals(code))return parcel;throw new AssertionError("Missing "+code);}
    static int run(Context context)throws Exception{
        checks=0;String name="parcel-locations-"+UUID.randomUUID()+".db";ParcelStore store=new ParcelStore(context,name);
        try{
            ingest(store,"a","80010001","南大仙林菜根谭","室内10号丰巢柜");
            ingest(store,"b","80010002","南大仙林菜根谭室内","9号丰巢柜");
            ingest(store,"c","80010003","南大仙林菜根谭室外39号","丰巢柜");
            check(store.locations().size()==1&&store.locations().get("南大仙林菜根谭")==3,"Separate imports made different groups");
            check(code(store,"80010002").locker.equals("室内9号丰巢柜")&&code(store,"80010003").locker.equals("室外39号丰巢柜"),"Cabinet detail lost");
            String original=code(store,"80010002").id;ingest(store,"repeat","80010002","南大仙林菜根谭","室内9号丰巢柜");
            check(code(store,"80010002").id.equals(original)&&code(store,"80010002").sources.size()==2,"Different extraction format duplicated reminder");
            ingest(store,"different","80010002","南大仙林菜根谭室内","10号丰巢柜");check(store.list(false,"address").size()==4,"Different cabinets merged");
            store.setCollected(original,true);
            // Recreate an actual version-2 layout with the legacy split location/locker fields.
            ContentValues old=new ContentValues();old.put("location","南大仙林菜根谭室内");old.put("location_key",ParcelAi.normalize("南大仙林菜根谭室内"));old.put("locker","9号丰巢柜");old.put("locker_key",ParcelAi.normalize("9号丰巢柜"));store.getWritableDatabase().update("parcels",old,"id=?",new String[]{original});
            store.getWritableDatabase().execSQL("DROP TABLE parcel_identity_aliases");store.getWritableDatabase().execSQL("ALTER TABLE parcels DROP COLUMN manual_fields");store.getWritableDatabase().execSQL("DROP TABLE location_aliases");store.getWritableDatabase().setVersion(2);store.close();store=new ParcelStore(context,name);
            check(store.locations().size()==1,"Version-2 migration did not unify locations");ParcelStore.Parcel migrated=store.list(true,"address").get(0);
            check(migrated.id.equals(original)&&migrated.manualCollected&&migrated.collected&&migrated.sources.size()==2&&migrated.locker.equals("室内9号丰巢柜"),"Migration lost source, state or cabinet");
            ingest(store,"alias","9021","南大菜根谭","室外2号丰巢柜");
            check(store.unifyLocations(List.of("南大菜根谭","南大仙林菜根谭"),"南大仙林菜根谭")==5,"Organizing to a selected name counted records twice");
            ingest(store,"alias2","9021","南大菜根谭","室外2号丰巢柜");check(store.locations().size()==1,"Existing-name alias did not apply to later reminder");
            check(store.unifyLocations(List.of("南大菜根谭","南大仙林菜根谭"),"菜根谭取件站")==5,"Manual organize missed archived rows");
            check(store.list(true,"address").get(0).manualCollected&&store.list(true,"address").get(0).sources.size()==2,"Manual organize changed state/sources");
            store.close();store=new ParcelStore(context,name);ingest(store,"future","9022","南大仙林菜根谭室外39号","丰巢柜");
            check(store.locations().size()==1&&code(store,"9022").location.equals("菜根谭取件站"),"Alias did not survive reopen/future imports");
            store.unifyLocations(List.of("菜根谭取件站"),"菜根谭");ingest(store,"chain","9023","南大菜根谭","室内1号丰巢柜");check(code(store,"9023").location.equals("菜根谭"),"Renaming an alias target broke earlier aliases");
            store.getWritableDatabase().execSQL("CREATE TRIGGER reject_rename BEFORE UPDATE OF location ON parcels WHEN NEW.location='失败名称' BEGIN SELECT RAISE(ABORT,'synthetic'); END");
            try{store.unifyLocations(List.of("菜根谭"),"失败名称");throw new AssertionError("Expected failed rename");}catch(IOException expected){}
            store.getWritableDatabase().execSQL("DROP TRIGGER reject_rename");ingest(store,"rollback","9024","南大菜根谭","室内1号丰巢柜");check(code(store,"9024").location.equals("菜根谭")&&store.locations().size()==1,"Failed rename left partial rows/aliases");
            try{store.unifyLocations(List.of("菜根谭"),"菜根谭室内9号");throw new AssertionError("Cabinet accepted as station");}catch(IOException expected){checks++;}
            return checks;
        }finally{store.close();context.deleteDatabase(name);}
    }
}
