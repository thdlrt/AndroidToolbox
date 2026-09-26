package net.lanbridge.android;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Manual configuration snapshots only; ledger and current WebDAV identity are never restored. */
final class ConfigBackupService {
    static final String DIRECTORY="config-backups/android";
    static JSONObject snapshot(Context context)throws Exception{
        JSONObject home=new JSONObject();for(Map.Entry<String,?> e:context.getSharedPreferences("MainActivity",0).getAll().entrySet())if(e.getKey().matches("favorite\\.[a-z0-9_-]{1,50}")&&e.getValue() instanceof Boolean)home.put(e.getKey(),e.getValue());
        LoginStore nas=new LoginStore(context);return AndroidConfigBackup.create(home,nas.origin(),nas.username(),nas.scope());
    }
    static String upload(Context context)throws Exception{
        WebDavSettings.Connection connection=WebDavSettings.connection(context);connection.ensure();JSONObject value=snapshot(context);String name="android-config-"+java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now())+"-"+LedgerStore.id()+".json";
        File local=File.createTempFile("config-backup-",".json",context.getCacheDir());try{LedgerStore.write(local,value.toString(2).getBytes(StandardCharsets.UTF_8));try(RelayDav dav=connection.client()){if(!dav.putImmutable(local,DIRECTORY+"/"+name))throw new IOException("备份名称已存在，请重试");}return name;}finally{local.delete();}
    }
    static List<RelayDav.Entry> list(Context context)throws Exception{WebDavSettings.ensureServices(context);try(RelayDav dav=WebDavSettings.rootClient(context)){List<RelayDav.Entry> result=dav.list(DIRECTORY);result.removeIf(e->e.directory||!e.name.matches("android-config-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.json"));return result;}}
    static JSONObject download(Context context,RelayDav.Entry entry)throws Exception{
        if(!entry.path.startsWith(DIRECTORY+"/")||entry.directory||entry.size>1024*1024)throw new IOException("配置备份路径或大小无效");File local=File.createTempFile("config-restore-",".json",context.getCacheDir());try(RelayDav dav=WebDavSettings.rootClient(context)){dav.download(entry,local,(n,t)->{});JSONObject value=LedgerStore.read(local);AndroidConfigBackup.validate(value);return value;}finally{local.delete();}
    }
    static File restore(Context context,JSONObject snapshot)throws Exception{
        AndroidConfigBackup.validate(snapshot);JSONObject current=snapshot(context);File recovery=new File(context.getFilesDir(),"config-recovery/"+LedgerStore.id()+".json");LedgerStore.write(recovery,current.toString(2).getBytes(StandardCharsets.UTF_8));
        try{apply(context,snapshot);}catch(Exception failure){try{apply(context,current);}catch(Exception restore){failure.addSuppressed(restore);}throw failure;}return recovery;
    }
    private static void apply(Context context,JSONObject value)throws Exception{
        JSONObject prefs=value.getJSONObject("preferences"),home=prefs.getJSONObject("home"),nas=prefs.getJSONObject("nas");SharedPreferences storage=context.getSharedPreferences("MainActivity",0);SharedPreferences.Editor editor=storage.edit();for(String key:storage.getAll().keySet())if(key.startsWith("favorite."))editor.remove(key);for(String key:LedgerStore.keys(home))editor.putBoolean(key,home.getBoolean(key));if(!editor.commit())throw new IOException("无法写入首页配置");
        LoginStore old=new LoginStore(context);boolean accountChanged=!old.origin().equals(nas.getString("origin"))||!old.username().equals(nas.getString("username"));SharedPreferences.Editor profile=context.getSharedPreferences("profile",0).edit().putString("origin",nas.getString("origin")).putString("username",nas.getString("username")).putString("scope",nas.getString("scope"));if(accountChanged)profile.remove("iv").remove("cipher");if(!profile.commit())throw new IOException("无法写入 NAS 配置");
    }
}
