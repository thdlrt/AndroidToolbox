package net.lanbridge.android;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Startup/change/periodic sync; local writes never depend on remote availability. */
public final class LedgerSync {
    private static LedgerSync singleton;
    public static synchronized LedgerSync get(Context context){if(singleton==null)singleton=new LedgerSync(context.getApplicationContext());return singleton;}
    private final Context context;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private volatile LedgerStore store;
    private final Map<String,String> verifiedOps=new HashMap<>();private String verifiedIdentity="";
    public volatile String status="尚未同步";
    public volatile boolean busy;
    public volatile long revision;
    private LedgerSync(Context context){this.context=context;worker.scheduleWithFixedDelay(this::perform,0,60,TimeUnit.SECONDS);}
    public synchronized LedgerStore store()throws Exception{if(store==null)store=new LedgerStore(new File(context.getFilesDir(),"ledger-v1"));return store;}
    public String remotePath(){return WebDavSettings.root(context);}
    public void sync(){worker.execute(this::perform);}
    public void changed(){revision++;sync();}
    public void submit(Runnable action){worker.execute(action);}
    private static void verified(File file,JSONObject attachment)throws Exception{if(file.length()!=attachment.getLong("size")||!LedgerStore.hash(file).equals(attachment.getString("sha256")))throw new IOException("附件完整性校验失败");}
    private void perform(){
        if(busy)return;busy=true;
        try{
            WebDavSettings.migrate(context);LoginStore credentials=WebDavSettings.store(context);
            if(!credentials.hasPassword()){status="离线保存 · 请配置统一 WebDAV 连接";return;}
            WebDavSettings.Connection target=WebDavSettings.connection(context);String identity=target.url+"\n"+target.user+"\n"+target.root+"\n"+credentials.revision();if(!identity.equals(verifiedIdentity)){verifiedOps.clear();verifiedIdentity=identity;}
            LedgerStore local=store();status="正在合并同步…";target.ensure();String ledgerMigrationError="";try{WebDavSettings.importLegacyLedgers(context,local,target);}catch(Exception legacy){ledgerMigrationError=legacy.getMessage();}
            try(RelayDav dav=new RelayDav(target.url,target.root,target.user,target.password)){
                dav.ensure("");dav.ensure("ledger-v1");dav.ensure("ledger-v1/ops");dav.ensure("ledger-v1/blobs");
                Map<String,RelayDav.Entry> remote=new HashMap<>();for(RelayDav.Entry entry:dav.list("ledger-v1/ops"))if(!entry.directory&&entry.name.matches("[0-9a-f]{32}\\.json"))remote.put(entry.name,entry);
                Map<String,JSONObject> known=new HashMap<>();for(JSONObject op:local.operations())known.put(op.getString("op_id")+".json",op);
                int downloaded=0,uploaded=0;
                for(RelayDav.Entry entry:remote.values()){
                    String fingerprint=entry.etag+"/"+entry.size+"/"+entry.modified;if(known.containsKey(entry.name)&&RelayDav.strong(entry.etag)&&fingerprint.equals(verifiedOps.get(entry.name)))continue;if(entry.size>1024*1024)throw new IOException("同步操作过大");
                    File temp=File.createTempFile("ledger-op-",".json",context.getCacheDir());
                    try{dav.download(entry,temp,(n,t)->{});JSONObject op=LedgerStore.read(temp);LedgerStore.validate(op);if(!entry.name.equals(op.getString("op_id")+".json"))throw new IOException("操作文件名不匹配");if(known.containsKey(entry.name)&&!LedgerStore.equivalent(known.get(entry.name),op))throw new IOException("远端不可变操作被修改，已保留本地版本");
                        JSONObject changes=op.getJSONObject("changes");for(String key:LedgerStore.keys(changes))if(key.startsWith("attachment:")&&!changes.isNull(key)){
                            JSONObject a=changes.getJSONObject(key);File blob=new File(local.blobs,a.getString("sha256"));
                            if(!blob.isFile()||blob.length()!=a.getLong("size")||!LedgerStore.hash(blob).equals(a.getString("sha256"))){RelayDav.Entry resource=dav.stat("ledger-v1/blobs/"+a.getString("sha256"));if(resource==null||resource.size!=a.getLong("size"))throw new IOException("远端附件尚未完整上传，请稍后重试");File staged=File.createTempFile("ledger-blob-",".part",context.getCacheDir());try{dav.download(resource,staged,(n,t)->{});verified(staged,a);java.nio.file.Files.move(staged.toPath(),blob.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{staged.delete();}}
                            verified(blob,a);
                        }
                        local.ingest(op);verifiedOps.put(entry.name,fingerprint);if(!known.containsKey(entry.name)){downloaded++;revision++;}
                    }finally{temp.delete();}
                }
                for(JSONObject op:local.operations()){
                    String name=op.getString("op_id")+".json";if(remote.containsKey(name))continue;
                    JSONObject changes=op.getJSONObject("changes");for(String key:LedgerStore.keys(changes))if(key.startsWith("attachment:")&&!changes.isNull(key)){
                        JSONObject a=changes.getJSONObject(key);File blob=new File(local.blobs,a.getString("sha256"));verified(blob,a);
                        if(!dav.putImmutable(blob,"ledger-v1/blobs/"+a.getString("sha256"))){RelayDav.Entry e=dav.stat("ledger-v1/blobs/"+a.getString("sha256"));File temp=File.createTempFile("ledger-check-",".part",context.getCacheDir());try{if(e==null||e.size!=a.getLong("size"))throw new IOException("远端附件冲突");dav.download(e,temp,(n,t)->{});verified(temp,a);}finally{temp.delete();}}
                    }
                    if(!dav.putImmutable(new File(local.ops,name),"ledger-v1/ops/"+name)){
                        RelayDav.Entry e=dav.stat("ledger-v1/ops/"+name);File temp=File.createTempFile("ledger-check-",".json",context.getCacheDir());try{if(e==null||e.size>2*1024*1024)throw new IOException("操作校验失败");dav.download(e,temp,(n,t)->{});if(!LedgerStore.equivalent(op,LedgerStore.read(temp)))throw new IOException("不可变操作内容冲突");}finally{temp.delete();}
                    }
                    uploaded++;
                }
                if(local.pendingParents()>0)throw new IOException(local.pendingParents()+" 条操作等待缺失的父版本，未丢弃本地数据");
                int conflicts=0;for(String type:new String[]{"entry","project","network_profile"}){JSONArray items=local.list(type);for(int i=0;i<items.length();i++)conflicts+=items.getJSONObject(i).getJSONObject("_conflicts").length();}
                try{WebDavSettings.migrateFiles(context,target);}catch(Exception migration){WebDavSettings.migrationStatus="旧中转迁移未完成，原文件保留："+migration.getMessage();}if(!ledgerMigrationError.isEmpty())WebDavSettings.migrationStatus="旧记账迁移未完成，原记录保留："+ledgerMigrationError;
                status="已同步 "+java.time.LocalTime.now().withNano(0)+" · 上传 "+uploaded+" / 下载 "+downloaded+(conflicts>0?" · "+conflicts+" 个字段待处理":"");
            }
        }catch(Exception e){status="离线数据已保留 · 同步失败："+e.getMessage();}finally{busy=false;revision++;}
    }
}
