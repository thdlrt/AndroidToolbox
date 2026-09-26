package net.lanbridge.android;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Portable Android configuration schema. Explicit fields prevent credentials/data leakage. */
final class AndroidConfigBackup {
    static JSONObject create(JSONObject favorites,String origin,String username,String mode)throws Exception{
        JSONObject value=new JSONObject().put("schema",1).put("platform","android").put("scope","config").put("created_at",System.currentTimeMillis()/1000.0).put("app_version",BuildConfig.VERSION_NAME)
            .put("preferences",new JSONObject().put("home",favorites).put("nas",new JSONObject().put("origin",origin).put("username",username).put("scope",mode)))
            .put("excluded",new JSONArray(Arrays.asList("passwords","keystore","webdav_connection","ledger","files","caches","logs")));
        validate(value);return value;
    }
    static void validate(JSONObject value)throws Exception{
        if(value.optInt("schema")!=1||!value.optString("platform").equals("android")||!value.optString("scope").equals("config")||value.toString().length()>1024*1024)throw new IOException("不是受支持的 Android 配置备份");
        JSONObject prefs=value.getJSONObject("preferences");if(!LedgerStore.keys(prefs).equals(new HashSet<>(Arrays.asList("home","nas"))))throw new IOException("配置备份包含不支持的范围");
        JSONObject home=prefs.getJSONObject("home");for(String key:LedgerStore.keys(home))if(!key.matches("favorite\\.[a-z0-9_-]{1,50}")||!(home.get(key) instanceof Boolean))throw new IOException("首页配置字段无效");
        JSONObject nas=prefs.getJSONObject("nas");if(!LedgerStore.keys(nas).equals(new HashSet<>(Arrays.asList("origin","username","scope"))))throw new IOException("NAS 配置字段无效");
        if(!(nas.get("origin") instanceof String)||!(nas.get("username") instanceof String)||nas.getString("origin").length()>2048||nas.getString("username").length()>1000||nas.getString("username").contains("\0")||!Arrays.asList("lan","all").contains(nas.getString("scope")))throw new IOException("NAS 配置格式无效");
        if(!nas.getString("origin").isEmpty())RelayDav.endpoint(nas.getString("origin"));
    }
}
