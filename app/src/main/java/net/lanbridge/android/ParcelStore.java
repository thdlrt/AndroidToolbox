package net.lanbridge.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.IOException;
import java.util.*;

/** Private transactional parcel records. Business records never enter configuration backups. */
public final class ParcelStore extends SQLiteOpenHelper {
    public static final long DEDUPE_WINDOW_MS=72L*3600000;
    public static final class Parcel {
        public final String id,code,trackingNumber,carrier,location,locker,status,deadline;
        public final long receivedAt,updatedAt;
        public final boolean collected,manualCollected,needsReview;
        public final List<ParcelSms.Message> sources;
        Parcel(Cursor row,List<ParcelSms.Message> sources){
            id=text(row,"id");code=text(row,"code");trackingNumber=text(row,"tracking_number");carrier=text(row,"carrier");location=text(row,"location");locker=text(row,"locker");status=text(row,"status");deadline=row.isNull(row.getColumnIndexOrThrow("deadline"))?null:text(row,"deadline");receivedAt=number(row,"received_at");updatedAt=number(row,"updated_at");collected=number(row,"collected")!=0;manualCollected=number(row,"manual_collected")!=0;needsReview=number(row,"needs_review")!=0;this.sources=Collections.unmodifiableList(sources);
        }
    }
    public ParcelStore(Context context){this(context,"parcels.db");}
    ParcelStore(Context context,String name){super(context.getApplicationContext(),name,null,2);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE parcels(id TEXT PRIMARY KEY,code TEXT NOT NULL,tracking_number TEXT NOT NULL,carrier TEXT NOT NULL,location TEXT NOT NULL,locker TEXT NOT NULL,status TEXT NOT NULL,deadline TEXT,tracking_key TEXT NOT NULL,code_key TEXT NOT NULL,location_key TEXT NOT NULL,locker_key TEXT NOT NULL,carrier_key TEXT NOT NULL,collected INTEGER NOT NULL,manual_collected INTEGER NOT NULL,received_at INTEGER NOT NULL,last_event_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,needs_review INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX parcel_tracking ON parcels(tracking_key)");db.execSQL("CREATE INDEX parcel_code ON parcels(code_key,location_key,locker_key,last_event_at)");
        db.execSQL("CREATE TABLE processed(fingerprint TEXT PRIMARY KEY,sms_id TEXT NOT NULL,date INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sources(fingerprint TEXT PRIMARY KEY,sms_id TEXT NOT NULL,date INTEGER NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,image_sha256 TEXT)");
        db.execSQL("CREATE TABLE parcel_sources(parcel_id TEXT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,fingerprint TEXT NOT NULL REFERENCES sources(fingerprint),PRIMARY KEY(parcel_id,fingerprint))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion==1&&newVersion==2)db.execSQL("ALTER TABLE parcels ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0");else throw new IllegalStateException("不支持的取件数据版本");}
    private static String text(Cursor c,String key){return c.getString(c.getColumnIndexOrThrow(key));}
    private static long number(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}
    private static boolean processed(SQLiteDatabase db,String fingerprint){try(Cursor c=db.rawQuery("SELECT 1 FROM processed WHERE fingerprint=?",new String[]{fingerprint})){return c.moveToFirst();}}
    public synchronized List<ParcelSms.Message> pendingSources(List<ParcelSms.Message> input){SQLiteDatabase db=getReadableDatabase();List<ParcelSms.Message> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(ParcelSms.Message message:input)if(seen.add(message.fingerprint)&&!processed(db,message.fingerprint))result.add(message);return result;}

    /** Validate everything first; on any failure neither rows nor processed markers are committed. */
    public synchronized int ingest(List<ParcelSms.Message> messages,List<ParcelAi.Item> items)throws IOException{
        ParcelAi.validateItems(messages,items,true);Map<String,ParcelSms.Message> sources=new HashMap<>();for(ParcelSms.Message message:messages)sources.put(message.id,message);SQLiteDatabase db=getWritableDatabase();Set<String> already=new HashSet<>(),touched=new HashSet<>();db.beginTransaction();
        try{
            for(ParcelSms.Message message:messages)if(processed(db,message.fingerprint))already.add(message.fingerprint);
            List<ParcelAi.Item> ordered=new ArrayList<>(items);ordered.sort(Comparator.comparingLong(item->eventDate(item,sources)));
            for(ParcelAi.Item item:ordered){
                if(!item.status.equals(ParcelAi.PENDING)&&!item.status.equals(ParcelAi.COLLECTED))continue;
                boolean fresh=false;for(String id:item.sourceIds)fresh|=!already.contains(sources.get(id).fingerprint);if(!fresh)continue;
                long date=eventDate(item,sources);Match identity=find(db,item,date);String match=identity.id;String id=match==null?UUID.randomUUID().toString().replace("-",""):match;ContentValues record=new ContentValues();long now=System.currentTimeMillis();
                if(match==null){record.put("needs_review",identity.review?1:0);record.put("id",id);record.put("code",item.code);record.put("tracking_number",item.trackingNumber);record.put("carrier",item.carrier);record.put("location",item.location);record.put("locker",item.locker);record.put("status",item.status);record.put("deadline",item.deadline);record.put("tracking_key",ParcelAi.normalize(item.trackingNumber));record.put("code_key",ParcelAi.normalize(item.code));record.put("location_key",ParcelAi.normalize(item.location));record.put("locker_key",ParcelAi.normalize(item.locker));record.put("carrier_key",ParcelAi.normalize(item.carrier));record.put("collected",item.status.equals(ParcelAi.COLLECTED)?1:0);record.put("manual_collected",0);record.put("received_at",date);record.put("last_event_at",date);record.put("updated_at",now);db.insertOrThrow("parcels",null,record);}
                else try(Cursor old=db.rawQuery("SELECT * FROM parcels WHERE id=?",new String[]{id})){if(!old.moveToFirst())throw new IOException("取件记录不存在");boolean newer=date>=number(old,"last_event_at"),manual=number(old,"manual_collected")!=0;
                    updateField(record,old,"code","code_key",item.code,newer);updateField(record,old,"tracking_number","tracking_key",item.trackingNumber,newer);updateField(record,old,"carrier","carrier_key",item.carrier,newer);updateField(record,old,"location","location_key",item.location,newer);updateField(record,old,"locker","locker_key",item.locker,newer);
                    if(newer&&!manual&&number(old,"collected")==0){record.put("status",item.status);record.put("collected",item.status.equals(ParcelAi.COLLECTED)?1:0);}if(item.deadline!=null&&(newer||old.isNull(old.getColumnIndexOrThrow("deadline"))))record.put("deadline",item.deadline);record.put("received_at",Math.min(number(old,"received_at"),date));record.put("last_event_at",Math.max(number(old,"last_event_at"),date));record.put("updated_at",now);db.update("parcels",record,"id=?",new String[]{id});
                }
                for(String sourceId:item.sourceIds){ParcelSms.Message source=sources.get(sourceId);ContentValues value=new ContentValues();value.put("fingerprint",source.fingerprint);value.put("sms_id",source.id);value.put("date",source.date);value.put("sender",source.sender);value.put("body",source.body);value.put("image_sha256",source.imageSha256);db.insertWithOnConflict("sources",null,value,SQLiteDatabase.CONFLICT_IGNORE);ContentValues link=new ContentValues();link.put("parcel_id",id);link.put("fingerprint",source.fingerprint);db.insertWithOnConflict("parcel_sources",null,link,SQLiteDatabase.CONFLICT_IGNORE);}
                touched.add(id);
            }
            for(ParcelSms.Message source:messages){ContentValues marker=new ContentValues();marker.put("fingerprint",source.fingerprint);marker.put("sms_id",source.id);marker.put("date",source.date);db.insertWithOnConflict("processed",null,marker,SQLiteDatabase.CONFLICT_IGNORE);}
            db.setTransactionSuccessful();return touched.size();
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("取件数据保存失败，本批结果未写入");}finally{db.endTransaction();}
    }
    private static long eventDate(ParcelAi.Item item,Map<String,ParcelSms.Message> sources){long date=0;for(String id:item.sourceIds)date=Math.max(date,sources.get(id).date);return date;}
    private static void updateField(ContentValues values,Cursor old,String field,String index,String incoming,boolean newer){if(!incoming.isEmpty()&&(newer||text(old,field).isEmpty())){values.put(field,incoming);values.put(index,ParcelAi.normalize(incoming));}}
    private static final class Match {final String id;final boolean review;Match(String id,boolean review){this.id=id;this.review=review;}}
    /** Identity candidates only, never SMS templates. Ambiguous candidates stay visible as new rows. */
    private static Match find(SQLiteDatabase db,ParcelAi.Item item,long date){
        String track=ParcelAi.normalize(item.trackingNumber),code=ParcelAi.normalize(item.code),location=ParcelAi.normalize(item.location),locker=ParcelAi.normalize(item.locker),carrier=ParcelAi.normalize(item.carrier);
        if(!track.isEmpty()){
            List<String> tracked=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,carrier_key FROM parcels WHERE tracking_key=?",new String[]{track})){while(c.moveToNext())if(carrier.isEmpty()||c.getString(1).isEmpty()||carrier.equals(c.getString(1)))tracked.add(c.getString(0));}
            if(tracked.size()==1)return new Match(tracked.get(0),false);if(tracked.size()>1)return new Match(null,true);
        }
        if(code.isEmpty()||(location.isEmpty()&&locker.isEmpty()))return new Match(null,false);
        String[] args={code,location,locker,Long.toString(Math.max(0,date-DEDUPE_WINDOW_MS)),Long.toString(date+DEDUPE_WINDOW_MS)};
        List<String> matches=new ArrayList<>();boolean archivedAmbiguity=false;try(Cursor c=db.rawQuery("SELECT id,carrier_key,tracking_key,collected FROM parcels WHERE code_key=? AND location_key=? AND locker_key=? AND last_event_at>=? AND last_event_at<=?",args)){
            while(c.moveToNext()){String previousTrack=c.getString(2);if(!track.isEmpty()&&!previousTrack.isEmpty()&&!track.equals(previousTrack))continue;if(!carrier.isEmpty()&&!c.getString(1).isEmpty()&&!carrier.equals(c.getString(1)))continue;
                if(item.status.equals(ParcelAi.PENDING)&&c.getInt(3)!=0){archivedAmbiguity=true;continue;}matches.add(c.getString(0));}
        }
        if(archivedAmbiguity)return new Match(null,true);
        return matches.size()==1?new Match(matches.get(0),false):new Match(null,matches.size()>1);
    }
    public synchronized List<Parcel> list(boolean archived,String sort){
        String order=sort.equals("address")?"location_key,locker_key,received_at DESC":sort.equals("locker")?"locker_key,location_key,received_at DESC":sort.equals("carrier")?"carrier_key,location_key,received_at DESC":"received_at DESC";
        SQLiteDatabase db=getReadableDatabase();List<Parcel> rows=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT * FROM parcels WHERE collected=? ORDER BY "+order+",id",new String[]{archived?"1":"0"})){while(c.moveToNext())rows.add(new Parcel(c,sources(db,text(c,"id"))));}return rows;
    }
    private static List<ParcelSms.Message> sources(SQLiteDatabase db,String parcel){List<ParcelSms.Message> result=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT s.* FROM sources s JOIN parcel_sources p ON p.fingerprint=s.fingerprint WHERE p.parcel_id=? ORDER BY s.date,s.sms_id",new String[]{parcel})){while(c.moveToNext())result.add(new ParcelSms.Message(text(c,"sms_id"),number(c,"date"),text(c,"sender"),text(c,"body"),c.isNull(c.getColumnIndexOrThrow("image_sha256"))?null:text(c,"image_sha256")));}return result;}
    public synchronized void setCollected(String id,boolean collected)throws IOException{ContentValues values=new ContentValues();values.put("collected",collected?1:0);values.put("manual_collected",collected?1:0);values.put("needs_review",0);values.put("status",collected?ParcelAi.COLLECTED:ParcelAi.PENDING);values.put("updated_at",System.currentTimeMillis());if(getWritableDatabase().update("parcels",values,"id=?",new String[]{id})!=1)throw new IOException("取件记录不存在");}
}
