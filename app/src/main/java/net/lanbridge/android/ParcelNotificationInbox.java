package net.lanbridge.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only opted-in, keyword-matched SMS-app notifications are retained locally. */
final class ParcelNotificationInbox extends SQLiteOpenHelper {
    static final int MAX_ROWS=500,MAX_BODY=16000;
    private static final long RETENTION=14L*86400000;
    ParcelNotificationInbox(Context c){super(c,"parcel-notifications.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE incoming(hash TEXT PRIMARY KEY,date INTEGER NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,done INTEGER NOT NULL DEFAULT 0)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("通知缓存版本不支持");}
    boolean offer(String pkg,String sender,String body,long date,long now){
        if(body==null||body.trim().isEmpty()||body.length()>MAX_BODY||date<now-RETENTION||date>now+300000)return false;
        body=body.trim();sender=sender==null?"":sender;if(sender.length()>500)sender=sender.substring(0,500);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.delete("incoming","date<?",new String[]{Long.toString(now-RETENTION)});
            String hash=ParcelSms.sha256((pkg+"\n"+body).getBytes(StandardCharsets.UTF_8));
            try(Cursor cursor=db.rawQuery("SELECT hash FROM incoming WHERE hash=?",new String[]{hash})){if(cursor.moveToFirst())return false;}
            try(Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM incoming",null)){cursor.moveToFirst();if(cursor.getInt(0)>=MAX_ROWS){db.delete("incoming","hash IN (SELECT hash FROM incoming WHERE done=1 ORDER BY date LIMIT 1)",null);}}
            try(Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM incoming",null)){cursor.moveToFirst();if(cursor.getInt(0)>=MAX_ROWS)throw new IllegalStateException("通知缓存已满，请打开取件助手处理");}
            ContentValues row=new ContentValues();row.put("hash",hash);row.put("date",date);row.put("sender",sender);row.put("body",body);db.insertOrThrow("incoming",null,row);db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    List<ParcelSms.Message> pending(long now){prune(now);List<ParcelSms.Message> result=new ArrayList<>();try(Cursor cursor=getReadableDatabase().query("incoming",new String[]{"hash","date","sender","body"},"done=0 AND date>=?",new String[]{Long.toString(now-RETENTION)},null,null,"date ASC")){while(cursor.moveToNext())result.add(new ParcelSms.Message("notification:"+cursor.getString(0),cursor.getLong(1),cursor.getString(2),cursor.getString(3)));}return result;}
    void done(List<ParcelSms.Message> sources){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues row=new ContentValues();row.put("done",1);for(ParcelSms.Message source:sources)db.update("incoming",row,"hash=?",new String[]{source.id.substring("notification:".length())});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void clear(){getWritableDatabase().delete("incoming",null,null);}
    int count(long now){prune(now);try(Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM incoming WHERE done=0 AND date>=?",new String[]{Long.toString(now-RETENTION)})){cursor.moveToFirst();return cursor.getInt(0);}}
    private void prune(long now){getWritableDatabase().delete("incoming","date<?",new String[]{Long.toString(now-RETENTION)});}
}
