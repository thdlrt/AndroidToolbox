package net.lanbridge.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.IOException;
import java.util.*;

/** Private transactional parcel records. Business records never enter configuration backups. */
public final class ParcelStore extends SQLiteOpenHelper {
    public static final long DEDUPE_WINDOW_MS=72L*3600000;
    private static final String[] EDIT_FIELDS={"code","tracking_number","carrier","location","locker","deadline"};
    private static final String IDENTITIES="(SELECT id AS parcel_id,tracking_key,code_key,location_key,locker_key,carrier_key FROM parcels UNION SELECT parcel_id,tracking_key,code_key,location_key,locker_key,carrier_key FROM parcel_identity_aliases) i JOIN parcels p ON p.id=i.parcel_id";
    public static final class Edit {
        private final String[] values;
        public Edit(String code,String tracking,String carrier,String location,String locker,String deadline)throws IOException{
            values=new String[]{code,tracking,carrier,location,locker,deadline};String[] labels={"取件码","运单号","快递公司","地点","柜位","截止提示"};int[] limits={128,128,128,512,256,128};
            for(int i=0;i<values.length;i++){String raw=values[i]==null?"":values[i];if(raw.contains("\0")||raw.contains("\n")||raw.contains("\r"))throw new IOException(labels[i]+"格式无效或过长（最多 "+limits[i]+" 字符）");values[i]=raw.trim();if(values[i].length()>limits[i])throw new IOException(labels[i]+"格式无效或过长（最多 "+limits[i]+" 字符）");}
            ParcelLocation place=ParcelLocation.of(values[3],values[4]);values[3]=place.location;values[4]=place.locker;
            if(values[4].length()>256)throw new IOException("柜位内容过长（最多 256 字符）");
        }
    }
    public static final class Parcel {
        public final String id,code,trackingNumber,carrier,location,locker,status,deadline;
        public final long receivedAt,updatedAt;
        public final boolean collected,manualCollected,needsReview,manuallyEdited;
        public final List<ParcelSms.Message> sources;
        Parcel(Cursor row,List<ParcelSms.Message> sources){
            id=text(row,"id");code=text(row,"code");trackingNumber=text(row,"tracking_number");carrier=text(row,"carrier");location=text(row,"location");locker=text(row,"locker");status=text(row,"status");deadline=row.isNull(row.getColumnIndexOrThrow("deadline"))?null:text(row,"deadline");receivedAt=number(row,"received_at");updatedAt=number(row,"updated_at");collected=number(row,"collected")!=0;manualCollected=number(row,"manual_collected")!=0;needsReview=number(row,"needs_review")!=0;manuallyEdited=number(row,"manual_fields")!=0;this.sources=Collections.unmodifiableList(sources);
        }
    }
    public ParcelStore(Context context){this(context,"parcels.db");}
    ParcelStore(Context context,String name){super(context.getApplicationContext(),name,null,4);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE parcels(id TEXT PRIMARY KEY,code TEXT NOT NULL,tracking_number TEXT NOT NULL,carrier TEXT NOT NULL,location TEXT NOT NULL,locker TEXT NOT NULL,status TEXT NOT NULL,deadline TEXT,tracking_key TEXT NOT NULL,code_key TEXT NOT NULL,location_key TEXT NOT NULL,locker_key TEXT NOT NULL,carrier_key TEXT NOT NULL,collected INTEGER NOT NULL,manual_collected INTEGER NOT NULL,received_at INTEGER NOT NULL,last_event_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,needs_review INTEGER NOT NULL DEFAULT 0,manual_fields INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX parcel_tracking ON parcels(tracking_key)");db.execSQL("CREATE INDEX parcel_code ON parcels(code_key,location_key,locker_key,last_event_at)");
        db.execSQL("CREATE TABLE processed(fingerprint TEXT PRIMARY KEY,sms_id TEXT NOT NULL,date INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sources(fingerprint TEXT PRIMARY KEY,sms_id TEXT NOT NULL,date INTEGER NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,image_sha256 TEXT)");
        db.execSQL("CREATE TABLE parcel_sources(parcel_id TEXT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,fingerprint TEXT NOT NULL REFERENCES sources(fingerprint),PRIMARY KEY(parcel_id,fingerprint))");
        createLocations(db);
        createIdentities(db);
    }
    private static void createIdentities(SQLiteDatabase db){db.execSQL("CREATE TABLE parcel_identity_aliases(parcel_id TEXT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,tracking_key TEXT NOT NULL,code_key TEXT NOT NULL,location_key TEXT NOT NULL,locker_key TEXT NOT NULL,carrier_key TEXT NOT NULL,PRIMARY KEY(parcel_id,tracking_key,code_key,location_key,locker_key,carrier_key))");db.execSQL("CREATE INDEX parcel_alias_tracking ON parcel_identity_aliases(tracking_key)");db.execSQL("CREATE INDEX parcel_alias_code ON parcel_identity_aliases(code_key,location_key,locker_key)");}
    private static void createLocations(SQLiteDatabase db){db.execSQL("CREATE TABLE location_aliases(alias_key TEXT PRIMARY KEY,location TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<1||newVersion!=4)throw new IllegalStateException("不支持的取件数据版本");
        if(oldVersion<2)db.execSQL("ALTER TABLE parcels ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<3){createLocations(db);try(Cursor c=db.rawQuery("SELECT id,location,locker FROM parcels",null)){while(c.moveToNext()){ParcelLocation place=ParcelLocation.of(c.getString(1),c.getString(2));ContentValues values=new ContentValues();putLocation(values,place.location,place.locker);db.update("parcels",values,"id=?",new String[]{c.getString(0)});}}}
        if(oldVersion<4){db.execSQL("ALTER TABLE parcels ADD COLUMN manual_fields INTEGER NOT NULL DEFAULT 0");createIdentities(db);}
    }
    private static void putLocation(ContentValues values,String location,String locker){values.put("location",location);values.put("location_key",ParcelAi.normalize(location));values.put("locker",locker);values.put("locker_key",ParcelAi.normalize(locker));}
    private static ParcelAi.Item canonical(SQLiteDatabase db,ParcelAi.Item item){ParcelLocation place=ParcelLocation.of(item.location,item.locker);String location=place.location;try(Cursor c=db.rawQuery("SELECT location FROM location_aliases WHERE alias_key=?",new String[]{ParcelAi.normalize(location)})){if(c.moveToFirst())location=c.getString(0);}return item.at(location,place.locker);}
    private static String text(Cursor c,String key){return c.getString(c.getColumnIndexOrThrow(key));}
    private static long number(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}
    private static boolean processed(SQLiteDatabase db,String fingerprint){try(Cursor c=db.rawQuery("SELECT 1 FROM processed WHERE fingerprint=?",new String[]{fingerprint})){return c.moveToFirst();}}
    private static boolean linked(SQLiteDatabase db,String fingerprint){try(Cursor c=db.rawQuery("SELECT 1 FROM parcel_sources WHERE fingerprint=? LIMIT 1",new String[]{fingerprint})){return c.moveToFirst();}}
    public synchronized List<ParcelSms.Message> pendingSources(List<ParcelSms.Message> input){return pendingSources(input,false);}
    public synchronized List<ParcelSms.Message> pendingSources(List<ParcelSms.Message> input,boolean retryUnmatched){SQLiteDatabase db=getReadableDatabase();List<ParcelSms.Message> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(ParcelSms.Message message:input)if(seen.add(message.fingerprint)&&(!processed(db,message.fingerprint)||retryUnmatched&&message.imageSha256==null&&!linked(db,message.fingerprint)))result.add(message);return result;}

    /** Validate everything first; on any failure neither rows nor processed markers are committed. */
    public synchronized int ingest(List<ParcelSms.Message> messages,List<ParcelAi.Item> items)throws IOException{
        return ingest(messages,items,false);
    }
    public synchronized int ingest(List<ParcelSms.Message> messages,List<ParcelAi.Item> items,boolean retryUnmatched)throws IOException{
        ParcelAi.validateItems(messages,items,false);Map<String,ParcelSms.Message> sources=new HashMap<>();for(ParcelSms.Message message:messages)sources.put(message.id,message);SQLiteDatabase db=getWritableDatabase();Set<String> already=new HashSet<>(),touched=new HashSet<>();db.beginTransaction();
        try{
            for(ParcelSms.Message message:messages)if(processed(db,message.fingerprint)&&(!retryUnmatched||message.imageSha256!=null||linked(db,message.fingerprint)))already.add(message.fingerprint);
            List<ParcelAi.Item> ordered=new ArrayList<>(items);ordered.sort(Comparator.comparingLong(item->eventDate(item,sources)));
            for(ParcelAi.Item extracted:ordered){
                ParcelAi.Item item=canonical(db,extracted);
                if(!item.status.equals(ParcelAi.PENDING)&&!item.status.equals(ParcelAi.COLLECTED))continue;
                boolean fresh=false;for(String id:item.sourceIds)fresh|=!already.contains(sources.get(id).fingerprint);if(!fresh)continue;
                long date=eventDate(item,sources);Match identity=find(db,item,date);String match=identity.id;String id=match==null?UUID.randomUUID().toString().replace("-",""):match;ContentValues record=new ContentValues();long now=System.currentTimeMillis();
                if(match==null){record.put("needs_review",identity.review?1:0);record.put("id",id);record.put("code",item.code);record.put("tracking_number",item.trackingNumber);record.put("carrier",item.carrier);record.put("location",item.location);record.put("locker",item.locker);record.put("status",item.status);record.put("deadline",item.deadline);record.put("tracking_key",ParcelAi.normalize(item.trackingNumber));record.put("code_key",ParcelAi.normalize(item.code));record.put("location_key",ParcelAi.normalize(item.location));record.put("locker_key",ParcelAi.normalize(item.locker));record.put("carrier_key",ParcelAi.normalize(item.carrier));record.put("collected",item.status.equals(ParcelAi.COLLECTED)?1:0);record.put("manual_collected",0);record.put("received_at",date);record.put("last_event_at",date);record.put("updated_at",now);db.insertOrThrow("parcels",null,record);}
                else try(Cursor old=db.rawQuery("SELECT * FROM parcels WHERE id=?",new String[]{id})){if(!old.moveToFirst())throw new IOException("取件记录不存在");boolean newer=date>=number(old,"last_event_at"),manual=number(old,"manual_collected")!=0;
                    String[] incoming={item.code,item.trackingNumber,item.carrier,item.location,item.locker};String[] indexes={"code_key","tracking_key","carrier_key","location_key","locker_key"};int protectedFields=(int)number(old,"manual_fields");for(int i=0;i<incoming.length;i++)if((protectedFields&(1<<i))==0)updateField(record,old,EDIT_FIELDS[i],indexes[i],incoming[i],newer);
                    if(newer&&!manual&&number(old,"collected")==0){record.put("status",item.status);record.put("collected",item.status.equals(ParcelAi.COLLECTED)?1:0);}if((protectedFields&32)==0&&item.deadline!=null&&(newer||old.isNull(old.getColumnIndexOrThrow("deadline"))))record.put("deadline",item.deadline);record.put("received_at",Math.min(number(old,"received_at"),date));record.put("last_event_at",Math.max(number(old,"last_event_at"),date));record.put("updated_at",now);db.update("parcels",record,"id=?",new String[]{id});
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
            Set<String> tracked=new LinkedHashSet<>();try(Cursor c=db.rawQuery("SELECT p.id,i.carrier_key FROM "+IDENTITIES+" WHERE i.tracking_key=?",new String[]{track})){while(c.moveToNext())if(carrier.isEmpty()||c.getString(1).isEmpty()||carrier.equals(c.getString(1)))tracked.add(c.getString(0));}
            if(tracked.size()==1)return new Match(tracked.iterator().next(),false);if(tracked.size()>1)return new Match(null,true);
        }
        if(code.isEmpty()||(location.isEmpty()&&locker.isEmpty()))return new Match(null,false);
        String[] args={code,location,locker,Long.toString(Math.max(0,date-DEDUPE_WINDOW_MS)),Long.toString(date+DEDUPE_WINDOW_MS)};
        Set<String> matches=new LinkedHashSet<>();boolean archivedAmbiguity=false;try(Cursor c=db.rawQuery("SELECT p.id,i.carrier_key,i.tracking_key,p.collected FROM "+IDENTITIES+" WHERE i.code_key=? AND i.location_key=? AND i.locker_key=? AND p.last_event_at>=? AND p.last_event_at<=?",args)){
            while(c.moveToNext()){String previousTrack=c.getString(2);if(!track.isEmpty()&&!previousTrack.isEmpty()&&!track.equals(previousTrack))continue;if(!carrier.isEmpty()&&!c.getString(1).isEmpty()&&!carrier.equals(c.getString(1)))continue;
                if(item.status.equals(ParcelAi.PENDING)&&c.getInt(3)!=0){archivedAmbiguity=true;continue;}matches.add(c.getString(0));}
        }
        if(archivedAmbiguity)return new Match(null,true);
        return matches.size()==1?new Match(matches.iterator().next(),false):new Match(null,matches.size()>1);
    }
    public synchronized void edit(Parcel expected,Edit edit)throws IOException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try(Cursor old=db.rawQuery("SELECT * FROM parcels WHERE id=?",new String[]{expected.id})){
            if(!old.moveToFirst())throw new IOException("取件记录不存在");
            if(number(old,"updated_at")!=expected.updatedAt)throw new IOException("记录已更新，请关闭后重新打开编辑");
            ContentValues values=new ContentValues();int changed=0;String[] indexes={"code_key","tracking_key","carrier_key","location_key","locker_key"};
            for(int i=0;i<EDIT_FIELDS.length;i++){String before=old.isNull(old.getColumnIndexOrThrow(EDIT_FIELDS[i]))?"":text(old,EDIT_FIELDS[i]);String after=edit.values[i];if(!before.equals(after)){changed|=1<<i;if(i==5&&after.isEmpty())values.putNull(EDIT_FIELDS[i]);else values.put(EDIT_FIELDS[i],after);if(i<5)values.put(indexes[i],ParcelAi.normalize(after));}}
            if(changed!=0){if((changed&31)!=0)db.execSQL("INSERT OR IGNORE INTO parcel_identity_aliases SELECT id,tracking_key,code_key,location_key,locker_key,carrier_key FROM parcels WHERE id=?",new Object[]{expected.id});values.put("manual_fields",number(old,"manual_fields")|changed);values.put("updated_at",Math.max(System.currentTimeMillis(),expected.updatedAt+1));db.update("parcels",values,"id=?",new String[]{expected.id});}
            db.setTransactionSuccessful();
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("快递信息保存失败，未修改记录");}finally{db.endTransaction();}
    }
    public synchronized List<Parcel> list(boolean archived,String sort){
        String order=sort.equals("address")?"location_key,locker_key,received_at DESC":sort.equals("locker")?"locker_key,location_key,received_at DESC":sort.equals("carrier")?"carrier_key,location_key,received_at DESC":"received_at DESC";
        SQLiteDatabase db=getReadableDatabase();List<Parcel> rows=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT * FROM parcels WHERE collected=? ORDER BY "+order+",id",new String[]{archived?"1":"0"})){while(c.moveToNext())rows.add(new Parcel(c,sources(db,text(c,"id"))));}return rows;
    }
    /** Counts include archived records, so old names do not return after restoring a parcel. */
    public synchronized Map<String,Integer> locations(){Map<String,Integer> result=new LinkedHashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT location,COUNT(*) FROM parcels WHERE location<>'' GROUP BY location ORDER BY location",null)){while(c.moveToNext())result.put(c.getString(0),c.getInt(1));}return result;}
    /** Explicit user selection only: group names change, package identities and sources do not. */
    public synchronized int unifyLocations(List<String> selected,String target)throws IOException{
        String name=ParcelLocation.format(target);ParcelLocation place=ParcelLocation.of(name,"");
        if(name.isEmpty()||name.length()>512||name.contains("\0")||name.contains("\n")||!place.locker.isEmpty())throw new IOException("请填写站点名称，室内 / 室外和柜号应留在柜位中");
        name=place.location;if(selected.isEmpty())throw new IOException("请选择需要统一的地点");
        Set<String> keys=new HashSet<>();for(String source:selected){String key=ParcelAi.normalize(source);if(key.isEmpty())throw new IOException("地点名称无效");keys.add(key);}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int count=0;
        try{
            // Retarget earlier aliases as well: renaming A -> B -> C stays stable for future A imports.
            List<String> aliases=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT alias_key,location FROM location_aliases",null)){while(c.moveToNext())if(keys.contains(ParcelAi.normalize(c.getString(1))))aliases.add(c.getString(0));}
            ContentValues rename=new ContentValues();rename.put("location",name);rename.put("location_key",ParcelAi.normalize(name));rename.put("updated_at",System.currentTimeMillis());
            count=db.update("parcels",rename,"location_key IN ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.toArray(new String[0]));
            if(count==0)throw new IOException("地点已变化，请重新打开整理地点");
            aliases.addAll(keys);aliases.add(ParcelAi.normalize(name));for(String key:aliases){ContentValues alias=new ContentValues();alias.put("alias_key",key);alias.put("location",name);db.insertWithOnConflict("location_aliases",null,alias,SQLiteDatabase.CONFLICT_REPLACE);}
            db.setTransactionSuccessful();return count;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("地点整理失败，未修改记录或规则");}finally{db.endTransaction();}
    }
    private static List<ParcelSms.Message> sources(SQLiteDatabase db,String parcel){List<ParcelSms.Message> result=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT s.* FROM sources s JOIN parcel_sources p ON p.fingerprint=s.fingerprint WHERE p.parcel_id=? ORDER BY s.date,s.sms_id",new String[]{parcel})){while(c.moveToNext())result.add(new ParcelSms.Message(text(c,"sms_id"),number(c,"date"),text(c,"sender"),text(c,"body"),c.isNull(c.getColumnIndexOrThrow("image_sha256"))?null:text(c,"image_sha256")));}return result;}
    public synchronized void setCollected(String id,boolean collected)throws IOException{ContentValues values=new ContentValues();values.put("collected",collected?1:0);values.put("manual_collected",collected?1:0);values.put("needs_review",0);values.put("status",collected?ParcelAi.COLLECTED:ParcelAi.PENDING);values.put("updated_at",System.currentTimeMillis());if(getWritableDatabase().update("parcels",values,"id=?",new String[]{id})!=1)throw new IOException("取件记录不存在");}
}
