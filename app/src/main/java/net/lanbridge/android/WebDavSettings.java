package net.lanbridge.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One device-encrypted connection and one root. Tool paths are derived, never edited. */
final class WebDavSettings {
    static volatile String migrationStatus="";
    private static final Object REMOTE_LOCK=new Object();
    static LoginStore store(Context c){return new LoginStore(c,"webdav-profile","webdav-login-v1");}
    private static SharedPreferences settings(Context c){return c.getSharedPreferences("webdav-layout",0);}
    static synchronized void migrate(Context c)throws Exception{
        SharedPreferences relay=c.getSharedPreferences("relay",0);
        if(!relay.getBoolean("shared_migrated",false)){
            LoginStore old=new LoginStore(c,"relay-profile","relay-login-v1"),shared=store(c);
            if(old.hasPassword()&&!shared.hasPassword()){String password=old.password(old.origin(),old.username());if(!password.isEmpty())shared.save(old.origin(),old.username(),password,"");}
            SharedPreferences.Editor edit=relay.edit().putBoolean("shared_migrated",true);
            if(!relay.contains("remote_path"))edit.putString("remote_path",old.scope().equals("lan")||old.scope().isEmpty()?"文件中转站":old.scope());
            if(!edit.commit())throw new IOException("无法迁移连接设置");
        }
        SharedPreferences global=settings(c);
        if(!global.getBoolean("unified_v2",false)){
            String selected=global.getString("root",c.getSharedPreferences("ledger",0).getString("remote_path","WinToolbox"));RelayDav.path(selected,false);
            String legacy=relay.getString("remote_path","文件中转站");
            if(store(c).hasPassword()&&!legacy.equals(selected+"/file-relay"))queueSource(c,store(c),legacy);
            if(!global.edit().putString("root",selected).putBoolean("unified_v2",true).commit())throw new IOException("无法保存统一根目录");
        }
    }
    static String root(Context c){return settings(c).getString("root","WinToolbox");}
    static String path(Context c){return root(c)+"/file-relay";}
    static String identity(Context c){LoginStore s=store(c);return s.origin()+"\n"+s.username()+"\n"+root(c)+"\n"+s.revision();}
    static synchronized void save(Context c,String url,String username,String password,String root)throws Exception{
        migrate(c);RelayDav.path(root,false);LoginStore previous=store(c);
        if(previous.hasPassword()&&(!previous.origin().equals(url)||!previous.username().equals(username)||!root(c).equals(root)))queueSource(c,previous,path(c),root(c));
        previous.save(url,username,password,"");if(!settings(c).edit().putString("root",root).commit())throw new IOException("无法保存统一根目录");
    }
    private static String sourcePassword(LoginStore source,Connection target)throws Exception{return sameEndpoint(source.origin(),target.url)&&source.username().equals(target.user)?target.password:source.password(source.origin(),source.username());}
    private static boolean sameEndpoint(String a,String b)throws Exception{return RelayDav.endpoint(a).equals(RelayDav.endpoint(b));}
    static String fingerprint(String url,String path)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest((RelayDav.endpoint(url).toString()+"\n"+path).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b));return out.substring(0,12);}
    private static void queueSource(Context c,LoginStore source,String path)throws Exception{queueSource(c,source,path,null);}
    private static void queueSource(Context c,LoginStore source,String path,String ledgerRoot)throws Exception{
        RelayDav.path(path,false);if(source.origin().isEmpty())return;String prefix=fingerprint(source.origin(),path),id=fingerprint(source.origin(),path+"\n"+source.username());String pass=source.password(source.origin(),source.username());if(pass.isEmpty())return;new LoginStore(c,"webdav-migration-"+id,"webdav-migration-"+id).save(source.origin(),source.username(),pass,"");JSONArray tasks=new JSONArray(settings(c).getString("sources","[]"));for(int i=0;i<tasks.length();i++)if(tasks.getJSONObject(i).getString("id").equals(id)){JSONObject existing=tasks.getJSONObject(i);existing.put("generation",LedgerStore.id());if(ledgerRoot!=null)existing.put("ledger_root",ledgerRoot);if(!settings(c).edit().putString("sources",tasks.toString()).commit())throw new IOException("无法更新迁移任务");return;}
        JSONObject task=new JSONObject().put("id",id).put("prefix",prefix).put("path",path).put("generation",LedgerStore.id());if(ledgerRoot!=null)task.put("ledger_root",ledgerRoot);tasks.put(task);if(!settings(c).edit().putString("sources",tasks.toString()).commit())throw new IOException("无法保留旧目录迁移信息");
    }
    static final class Connection {
        final String url,user,password,root;
        Connection(String url,String user,String password,String root){this.url=url;this.user=user;this.password=password;this.root=root;}
        RelayDav client()throws Exception{return new RelayDav(url,root,user,password);}
        void ensure()throws Exception{try(RelayDav dav=client()){dav.ensure("");dav.ensure("file-relay");dav.ensure("ledger-v1");dav.ensure("config-backups");dav.ensure("config-backups/android");}}
    }
    static Connection connection(Context c)throws Exception{migrate(c);LoginStore s=store(c);String pass=s.password(s.origin(),s.username());if(pass.isEmpty())throw new IOException("请配置统一 WebDAV 连接");return new Connection(s.origin(),s.username(),pass,root(c));}
    static RelayDav rootClient(Context c)throws Exception{return connection(c).client();}
    static void ensureServices(Context c)throws Exception{connection(c).ensure();}
    static void migrateFiles(Context c)throws Exception{migrateFiles(c,connection(c));}
    static void migrateFiles(Context c,Connection target)throws Exception{synchronized(REMOTE_LOCK){
        migrate(c);JSONArray tasks=new JSONArray(settings(c).getString("sources","[]"));String destination=fingerprint(target.url,target.root+"/file-relay");int files=0;List<String> errors=new ArrayList<>();
        for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.getJSONObject(i);String id=task.getString("id"),prefix=task.optString("prefix",id),sourcePath=task.getString("path"),done="done."+id+"."+task.optString("generation","legacy")+"."+destination;if(settings(c).getBoolean(done,false))continue;
            LoginStore source=new LoginStore(c,"webdav-migration-"+id,"webdav-migration-"+id);if(sameEndpoint(source.origin(),target.url)&&sourcePath.equals(target.root+"/file-relay")){settings(c).edit().putBoolean(done,true).commit();continue;}
            migrationStatus="正在迁移旧中转目录（保留原文件）…";
            try(RelayDav old=new RelayDav(source.origin(),sourcePath,source.username(),sourcePassword(source,target));RelayDav current=new RelayDav(target.url,target.root+"/file-relay",target.user,target.password)){
                RelayDav.Entry origin=old.stat("");if(origin!=null){if(!origin.directory)throw new IOException("旧中转路径不是目录");current.ensure("");Deque<String> folders=new ArrayDeque<>();folders.add("");int count=0;
                    while(!folders.isEmpty()){String folder=folders.remove();for(RelayDav.Entry entry:old.list(folder)){
                        if(++count>50000)throw new IOException("旧中转目录超过 50000 项，请分批整理后重试");String absolute=sourcePath+"/"+entry.path;
                        if(sameEndpoint(source.origin(),target.url)&&((!sourcePath.equals(target.root)&&(absolute.equals(target.root)||absolute.startsWith(target.root+"/")))||absolute.equals(target.root+"/file-relay")||absolute.startsWith(target.root+"/file-relay/")||(sourcePath.equals(target.root)&&Arrays.asList("ledger-v1","config-backups","project-memory").contains(entry.path.split("/")[0]))))continue;
                        if(entry.directory){if(!parents(current,entry.path+"/reserved-placeholder"))parents(current,"legacy-"+prefix+"/"+entry.path+"/reserved-placeholder");folders.add(entry.path);continue;}
                        File temp=File.createTempFile("webdav-migrate-",".part",c.getCacheDir());try{old.download(entry,temp,(n,total)->{});copyWithoutOverwrite(current,temp,entry.path,"legacy-"+prefix,c.getCacheDir());files++;migrationStatus="正在迁移旧中转目录 · 已处理 "+files+" 个文件";}finally{temp.delete();}
                    }}
                }
                if(!settings(c).edit().putBoolean(done,true).commit())throw new IOException("无法保存迁移进度");
            }catch(Exception e){errors.add(sourcePath+"："+e.getMessage());}
        }
        if(!errors.isEmpty()){migrationStatus="旧中转迁移未完成，原文件保留："+String.join("；",errors);throw new IOException(migrationStatus);}
        migrationStatus=tasks.length()==0?"":files>0?"旧中转迁移完成 · "+files+" 个文件 · 原目录保留":"旧中转目录已迁移，原文件保留";
    }
    }
    static void importLegacyLedgers(Context context,LedgerStore local)throws Exception{importLegacyLedgers(context,local,connection(context));}
    static void importLegacyLedgers(Context context,LedgerStore local,Connection target)throws Exception{synchronized(REMOTE_LOCK){
        JSONArray tasks=new JSONArray(settings(context).getString("sources","[]"));String destination=fingerprint(target.url,target.root);List<String> errors=new ArrayList<>();
        for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.getJSONObject(i);if(!task.has("ledger_root"))continue;String id=task.getString("id"),previousRoot=task.getString("ledger_root"),done="ledgerdone."+id+"."+task.optString("generation","legacy")+"."+destination;if(settings(context).getBoolean(done,false))continue;LoginStore source=new LoginStore(context,"webdav-migration-"+id,"webdav-migration-"+id);
            if(sameEndpoint(source.origin(),target.url)&&previousRoot.equals(target.root)){settings(context).edit().putBoolean(done,true).commit();continue;}
            try(RelayDav dav=new RelayDav(source.origin(),previousRoot,source.username(),sourcePassword(source,target))){
                if(dav.stat("ledger-v1/ops")!=null)for(RelayDav.Entry opFile:dav.list("ledger-v1/ops")){
                    if(opFile.directory||!opFile.name.matches("[0-9a-f]{32}\\.json"))continue;if(opFile.size>1024*1024)throw new IOException("旧目录操作过大");File downloaded=File.createTempFile("legacy-ledger-",".json",context.getCacheDir());
                    try{dav.download(opFile,downloaded,(n,t)->{});JSONObject op=LedgerStore.read(downloaded);LedgerStore.validate(op);if(!opFile.name.equals(op.getString("op_id")+".json"))throw new IOException("旧目录操作文件名不匹配");JSONObject changes=op.getJSONObject("changes");
                        for(String key:LedgerStore.keys(changes))if(key.startsWith("attachment:")&&!changes.isNull(key)){JSONObject attachment=changes.getJSONObject(key);File blob=new File(local.blobs,attachment.getString("sha256"));if(!blob.isFile()||blob.length()!=attachment.getLong("size")||!LedgerStore.hash(blob).equals(attachment.getString("sha256"))){RelayDav.Entry resource=dav.stat("ledger-v1/blobs/"+attachment.getString("sha256"));if(resource==null||resource.size!=attachment.getLong("size"))throw new IOException("旧目录附件缺失");File stage=File.createTempFile("legacy-blob-",".part",context.getCacheDir());try{dav.download(resource,stage,(n,t)->{});if(!LedgerStore.hash(stage).equals(attachment.getString("sha256")))throw new IOException("旧目录附件校验失败");java.nio.file.Files.move(stage.toPath(),blob.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{stage.delete();}}}
                        local.ingest(op);
                    }finally{downloaded.delete();}
                }
                if(local.pendingParents()>0)throw new IOException("旧目录还有缺失的父版本，稍后重试");if(!settings(context).edit().putBoolean(done,true).commit())throw new IOException("无法保存旧记账迁移进度");
            }catch(Exception e){errors.add(previousRoot+"："+e.getMessage());}
        }
        if(!errors.isEmpty()){migrationStatus="旧记账目录迁移未完成，原记录保留："+String.join("；",errors);throw new IOException(migrationStatus);}
    }
    }
    private static boolean parents(RelayDav dav,String path)throws Exception{String parent=RelayDav.parent(path),current="";if(parent.isEmpty())return true;for(String segment:parent.split("/")){current=current.isEmpty()?segment:current+"/"+segment;RelayDav.Entry found=dav.stat(current);if(found!=null&&!found.directory)return false;dav.ensure(current);}return true;}
    private static boolean same(RelayDav dav,RelayDav.Entry entry,File file,File cache)throws Exception{if(entry==null||entry.directory||entry.size!=file.length())return false;File copy=File.createTempFile("webdav-compare-",".part",cache);try{dav.download(entry,copy,(n,t)->{});return LedgerStore.hash(copy).equals(LedgerStore.hash(file));}finally{copy.delete();}}
    static String copyWithoutOverwrite(RelayDav dav,File file,String relative,String legacy,File cache)throws Exception{
        String target=relative;boolean available=parents(dav,target);RelayDav.Entry existing=available?dav.stat(target):null;
        if(existing!=null&&same(dav,existing,file,cache))return target;
        if(!available||existing!=null){target=legacy+"/"+relative;if(!parents(dav,target))target=legacy+"/"+LedgerStore.hash(file)+"-"+new File(relative).getName();parents(dav,target);existing=dav.stat(target);if(existing!=null&&same(dav,existing,file,cache))return target;if(existing!=null){int dot=target.lastIndexOf('.');target=(dot>target.lastIndexOf('/')?target.substring(0,dot):target)+"-"+LedgerStore.hash(file).substring(0,12)+(dot>target.lastIndexOf('/')?target.substring(dot):"");}}
        if(!dav.putImmutable(file,target)){existing=dav.stat(target);if(!same(dav,existing,file,cache))throw new IOException("目标目录存在不同内容，已保留两端原文件："+target);}
        existing=dav.stat(target);if(!same(dav,existing,file,cache))throw new IOException("迁移文件校验失败："+target);return target;
    }
}
