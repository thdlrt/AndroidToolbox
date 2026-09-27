package net.lanbridge.android;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Manual encrypted configuration snapshots, separate from automatic business-data synchronization. */
final class ConfigBackupService {
    static final Object CONFIG_LOCK=new Object();
    static final String DIRECTORY="config-backups/shared",LEGACY_DIRECTORY="config-backups/android",LEGACY_AI="ai-config/config-v1.json";
    private static final String[] MUTATED_PREFS={"MainActivity","profile","ai-config"};
    static final class Preview {
        final String platform,summary;private final JSONObject value,state;private final boolean applyConfig,applyAi;
        Preview(String platform,String summary,JSONObject value,JSONObject state,boolean config,boolean ai)throws Exception{this.platform=platform;this.summary=summary;this.value=new JSONObject(value.toString());this.state=new JSONObject(state.toString());applyConfig=config;applyAi=ai;}
    }
    static JSONObject snapshot(Context context)throws Exception{synchronized(CONFIG_LOCK){JSONObject home=new JSONObject();for(Map.Entry<String,?> e:context.getSharedPreferences("MainActivity",0).getAll().entrySet())if(e.getKey().matches("favorite\\.[a-z0-9_-]{1,50}")&&e.getValue() instanceof Boolean)home.put(e.getKey(),e.getValue());LoginStore nas=new LoginStore(context);return AndroidConfigBackup.create(home,nas.origin(),nas.username(),nas.scope());}}
    static String upload(Context context)throws Exception{
        WebDavSettings.Connection connection=WebDavSettings.connection(context);JSONObject value;synchronized(CONFIG_LOCK){value=ConfigBackupCodec.payload("android",snapshot(context),new AiSettings(context).exportSnapshot());}JSONObject encrypted=ConfigBackupCodec.encrypt(value,connection.password);
        String name="config-android-"+java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now())+"-"+LedgerStore.id()+".wtconfig.json";connection.ensure();File local=File.createTempFile("config-encrypted-",".json",context.getCacheDir());try{LedgerStore.write(local,encrypted.toString().getBytes(StandardCharsets.UTF_8));try(RelayDav dav=connection.client()){dav.ensure(DIRECTORY);if(!dav.putImmutable(local,DIRECTORY+"/"+name))throw new IOException("备份名称冲突，请重试");}return name;}finally{local.delete();}
    }
    static List<RelayDav.Entry> list(Context context)throws Exception{
        WebDavSettings.Connection connection=WebDavSettings.connection(context);connection.ensure();try(RelayDav dav=connection.client()){dav.ensure(DIRECTORY);List<RelayDav.Entry> result=new ArrayList<>();for(RelayDav.Entry e:dav.list(DIRECTORY))if(!e.directory&&e.name.matches("config-(windows|android)-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.wtconfig\\.json"))result.add(e);if(dav.stat(LEGACY_DIRECTORY)!=null)for(RelayDav.Entry e:dav.list(LEGACY_DIRECTORY))if(!e.directory&&e.name.matches("android-config-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.json"))result.add(e);RelayDav.Entry oldAi=dav.stat(LEGACY_AI);if(oldAi!=null&&!oldAi.directory)result.add(oldAi);result.sort(Comparator.comparingLong((RelayDav.Entry e)->e.modified).reversed());return result;}
    }
    static String label(RelayDav.Entry entry){return entry.path.equals(LEGACY_AI)?"旧版 AI 配置":entry.path.startsWith(LEGACY_DIRECTORY+"/")?"旧版 Android 配置 · "+entry.name:entry.name.startsWith("config-windows-")?"Windows · "+entry.name:"Android · "+entry.name;}
    static JSONObject download(Context context,RelayDav.Entry entry)throws Exception{return download(WebDavSettings.connection(context),context,entry);}
    private static JSONObject download(WebDavSettings.Connection connection,Context context,RelayDav.Entry entry)throws Exception{
        boolean shared=entry.path.matches("config-backups/shared/config-(windows|android)-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.wtconfig\\.json"),legacy=entry.path.matches("config-backups/android/android-config-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.json"),oldAi=entry.path.equals(LEGACY_AI);
        if((!shared&&!legacy&&!oldAi)||entry.directory||entry.size<1||entry.size>ConfigBackupCodec.LIMIT)throw new IOException("配置备份路径或大小无效");File encrypted=File.createTempFile("config-download-",".json",context.getCacheDir());try(RelayDav dav=connection.client()){dav.download(entry,encrypted,(n,t)->{});JSONObject value=LedgerStore.read(encrypted);if(shared)return ConfigBackupCodec.decrypt(value,connection.password);if(oldAi)return AiConfigCodec.decrypt(value,connection.password);AndroidConfigBackup.validate(value);return value;}finally{encrypted.delete();}
    }
    static Preview preview(Context context,RelayDav.Entry entry)throws Exception{
        String identity=WebDavSettings.identity(context);WebDavSettings.Connection connection=WebDavSettings.connection(context);JSONObject baseline;synchronized(CONFIG_LOCK){if(!identity.equals(WebDavSettings.identity(context)))throw new IOException("连接已变化，请重新查看备份");baseline=state(context);}
        return previewValue(context,download(connection,context,entry),baseline);
    }
    private static Preview previewValue(Context context,JSONObject value,JSONObject baseline)throws Exception{
        boolean legacyConfig=value.optString("scope").equals("config")&&value.optString("platform").equals("android"),legacyAi=value.optString("format").equals(AiConfigCodec.FORMAT);String platform;boolean config,ai;
        if(legacyConfig){AndroidConfigBackup.validate(value);platform="旧版 Android";config=true;ai=false;}
        else if(legacyAi){AiConfigCodec.validate(value);platform="旧版 AI";config=false;ai=true;}
        else{ConfigBackupCodec.validate(value);platform=value.getString("platform");config=platform.equals("android");ai=true;}
        String summary=config?"将恢复 Android 首页、NAS 设置"+(ai?"及共享 AI 服务、模型和密钥。":"，不修改 AI 设置。")+" NAS 账号变化后需重新输入密码。":"仅导入共享 AI 服务、模型和密钥，保留 Android 本机偏好。";
        summary+="\n\n保留当前 WebDAV 连接、取件 AI 开关和所有业务数据。备份中缺少的手机取件模型继续保留。恢复前自动保存加密恢复副本。";
        return new Preview(platform,summary,value,baseline,config,ai);
    }
    /** Compatibility API; user-facing restore always uses an earlier Preview. */
    static File restore(Context context,JSONObject value)throws Exception{JSONObject baseline;synchronized(CONFIG_LOCK){baseline=state(context);}return restore(context,previewValue(context,value,baseline));}
    static File restore(Context context,Preview preview)throws Exception{
        WebDavSettings.Connection connection=WebDavSettings.connection(context);synchronized(CONFIG_LOCK){
            if(!LedgerStore.equivalent(preview.state,state(context)))throw new IOException("本机配置或连接已变化，请重新预览后恢复");
            JSONObject incomingConfig=preview.value.optString("format").equals(ConfigBackupCodec.FORMAT)?preview.value.getJSONObject("config"):preview.value;
            JSONObject incomingAi=preview.value.optString("format").equals(ConfigBackupCodec.FORMAT)?preview.value.getJSONObject("ai"):preview.value;
            JSONObject currentAi=new AiSettings(context).exportSnapshot(),merged=preview.applyAi?ConfigBackupCodec.mergeAndroidRoles(incomingAi,currentAi):currentAi;
            if(preview.applyConfig)AndroidConfigBackup.validate(incomingConfig);
            JSONObject current=ConfigBackupCodec.payload("android",snapshot(context),currentAi),raw=raw(context,MUTATED_PREFS);File recovery=new File(context.getFilesDir(),"config-recovery/"+LedgerStore.id()+".wtconfig.json");LedgerStore.write(recovery,ConfigBackupCodec.encrypt(current,connection.password).toString().getBytes(StandardCharsets.UTF_8));
            LoginStore journal=journal(context);journal.save("configuration-restore","local",raw.toString(),"");
            try{if(preview.applyConfig)apply(context,incomingConfig);if(preview.applyAi)new AiSettings(context).importSnapshot(merged,new AiSettings(context).revision());journal.forget();return recovery;}
            catch(Exception failure){try{restoreRaw(context,raw);journal.forget();}catch(Exception rollback){failure.addSuppressed(rollback);}throw new IOException("配置恢复失败，已尝试回滚；请重新打开应用检查");}
        }
    }
    static void recoverPending(Context context)throws Exception{synchronized(CONFIG_LOCK){LoginStore journal=journal(context);if(!journal.hasPassword())return;JSONObject previous=new JSONObject(journal.password("configuration-restore","local"));restoreRaw(context,previous);journal.forget();}}
    private static LoginStore journal(Context c){return new LoginStore(c,"config-restore-journal","config-restore-journal-v1");}
    private static JSONObject state(Context context)throws Exception{return new JSONObject().put("preferences",raw(context,new String[]{"MainActivity","profile","ai-config","parcel-local"})).put("webdav",WebDavSettings.identity(context));}
    private static JSONObject raw(Context context,String[] names)throws Exception{JSONObject result=new JSONObject();for(String name:names){JSONObject values=new JSONObject();for(Map.Entry<String,?> e:context.getSharedPreferences(name,0).getAll().entrySet()){Object v=e.getValue();String type=v instanceof Boolean?"boolean":v instanceof Integer?"int":v instanceof Long?"long":v instanceof Float?"float":v instanceof Set?"set":"string";values.put(e.getKey(),new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));}result.put(name,values);}return result;}
    private static void restoreRaw(Context context,JSONObject snapshot)throws Exception{if(!LedgerStore.keys(snapshot).equals(new HashSet<>(Arrays.asList(MUTATED_PREFS))))throw new IOException("本机恢复记录范围无效");for(String name:MUTATED_PREFS){SharedPreferences.Editor edit=context.getSharedPreferences(name,0).edit().clear();JSONObject values=snapshot.getJSONObject(name);for(String key:LedgerStore.keys(values)){JSONObject item=values.getJSONObject(key);switch(item.getString("type")){case "boolean":edit.putBoolean(key,item.getBoolean("value"));break;case "int":edit.putInt(key,item.getInt("value"));break;case "long":edit.putLong(key,item.getLong("value"));break;case "float":edit.putFloat(key,(float)item.getDouble("value"));break;case "set":Set<String> set=new HashSet<>();JSONArray array=item.getJSONArray("value");for(int i=0;i<array.length();i++)set.add(array.getString(i));edit.putStringSet(key,set);break;default:edit.putString(key,item.getString("value"));}}if(!edit.commit())throw new IOException("本机配置回滚写入失败");}}
    private static void apply(Context context,JSONObject value)throws Exception{
        JSONObject prefs=value.getJSONObject("preferences"),home=prefs.getJSONObject("home"),nas=prefs.getJSONObject("nas");SharedPreferences storage=context.getSharedPreferences("MainActivity",0);SharedPreferences.Editor editor=storage.edit();for(String key:storage.getAll().keySet())if(key.startsWith("favorite."))editor.remove(key);for(String key:LedgerStore.keys(home))editor.putBoolean(key,home.getBoolean(key));if(!editor.commit())throw new IOException("无法写入首页配置");LoginStore old=new LoginStore(context);boolean accountChanged=!old.origin().equals(nas.getString("origin"))||!old.username().equals(nas.getString("username"));SharedPreferences.Editor profile=context.getSharedPreferences("profile",0).edit().putString("origin",nas.getString("origin")).putString("username",nas.getString("username")).putString("scope",nas.getString("scope"));if(accountChanged)profile.remove("iv").remove("cipher");if(!profile.commit())throw new IOException("无法写入 NAS 配置");
    }
}
