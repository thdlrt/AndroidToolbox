package net.lanbridge.android;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Cross-platform configuration backup v2. Data records are never part of this envelope. */
final class ConfigBackupCodec {
    static final int LIMIT=4*1024*1024;
    static final String FORMAT="wintoolbox-config-backup",ENCRYPTED="wintoolbox-config-backup-encrypted";
    static final byte[] AAD="wintoolbox-config-backup-v2".getBytes(StandardCharsets.UTF_8);
    private static void exact(JSONObject object,String... keys)throws IOException{if(!LedgerStore.keys(object).equals(new HashSet<>(Arrays.asList(keys))))throw new IOException("配置备份字段不符合协议");}
    static void validate(JSONObject value)throws Exception{
        exact(value,"format","version","platform","created_at","config","ai");Object version=value.get("version"),date=value.get("created_at");
        if(!FORMAT.equals(value.optString("format"))||!(version instanceof Integer||version instanceof Long)||((Number)version).longValue()!=2||!Arrays.asList("windows","android").contains(value.optString("platform"))||!(date instanceof Number)||!Double.isFinite(((Number)date).doubleValue())||((Number)date).doubleValue()<0||value.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("配置备份格式或大小无效");
        JSONObject config=value.getJSONObject("config");if(value.getString("platform").equals("android"))AndroidConfigBackup.validate(config);else{if(config.length()>1000)throw new IOException("配置项目过多");for(String name:LedgerStore.keys(config))if(name.isEmpty()||name.length()>200||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")||!(config.get(name) instanceof JSONObject))throw new IOException("Windows 配置映射无效");}
        AiConfigCodec.validate(value.getJSONObject("ai"));
    }
    static JSONObject payload(String platform,JSONObject config,JSONObject ai)throws Exception{return new JSONObject().put("format",FORMAT).put("version",2).put("platform",platform).put("created_at",System.currentTimeMillis()/1000.0).put("config",config).put("ai",ai);}
    static JSONObject encrypt(JSONObject payload,String password)throws Exception{byte[] salt=new byte[16],nonce=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(nonce);return encrypt(payload,password,salt,nonce);}
    static JSONObject encrypt(JSONObject payload,String password,byte[] salt,byte[] nonce)throws Exception{validate(payload);if(password.isEmpty()||salt.length!=16||nonce.length!=12)throw new IOException("配置备份加密参数无效");byte[] plain=payload.toString().getBytes(StandardCharsets.UTF_8);byte[] encrypted;try{encrypted=crypt(Cipher.ENCRYPT_MODE,plain,password,salt,nonce);}finally{Arrays.fill(plain,(byte)0);}JSONObject value=new JSONObject().put("format",ENCRYPTED).put("version",2).put("salt",Base64.getEncoder().encodeToString(salt)).put("nonce",Base64.getEncoder().encodeToString(nonce)).put("ciphertext",Base64.getEncoder().encodeToString(encrypted));if(value.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("加密配置备份超过 4 MiB");return value;}
    static JSONObject decrypt(JSONObject envelope,String password)throws Exception{
        try{exact(envelope,"format","version","salt","nonce","ciphertext");Object version=envelope.get("version");if(!ENCRYPTED.equals(envelope.optString("format"))||!(version instanceof Integer||version instanceof Long)||((Number)version).longValue()!=2||envelope.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("配置备份密文格式无效");byte[] salt=decode(envelope,"salt"),nonce=decode(envelope,"nonce"),body=decode(envelope,"ciphertext");if(salt.length!=16||nonce.length!=12||body.length<16||body.length>LIMIT)throw new IOException("配置备份密文长度无效");byte[] plain=crypt(Cipher.DECRYPT_MODE,body,password,salt,nonce);JSONObject result;try{result=new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}validate(result);return result;
        }catch(javax.crypto.AEADBadTagException e){throw new IOException("无法解密配置备份，请确认 WebDAV 密码一致且备份未损坏");}catch(IllegalArgumentException e){throw new IOException("配置备份编码无效");}
    }
    private static byte[] decode(JSONObject value,String key)throws Exception{if(!(value.get(key) instanceof String))throw new IOException("配置备份编码类型无效");String text=value.getString(key);byte[] decoded=Base64.getDecoder().decode(text);if(!Base64.getEncoder().encodeToString(decoded).equals(text))throw new IOException("配置备份编码不规范");return decoded;}
    private static byte[] crypt(int mode,byte[] body,String password,byte[] salt,byte[] nonce)throws Exception{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,200000,256);byte[] key;try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);return cipher.doFinal(body);}finally{Arrays.fill(key,(byte)0);}}

    /** Incoming shared roles win; absent device-specific parcel roles retain their original provider/key. */
    static JSONObject mergeAndroidRoles(JSONObject incoming,JSONObject local)throws Exception{
        AiConfigCodec.validate(incoming);AiConfigCodec.validate(local);JSONObject result=new JSONObject(incoming.toString()),roles=result.getJSONObject("roles"),secrets=result.getJSONObject("secrets");JSONArray providers=result.getJSONArray("providers"),oldProviders=local.getJSONArray("providers");Map<String,JSONObject> byId=new HashMap<>();for(int i=0;i<providers.length();i++)byId.put(providers.getJSONObject(i).getString("id"),providers.getJSONObject(i));Map<String,String> preserved=new HashMap<>();
        for(String name:new String[]{"parcel","parcel_vision"}){JSONObject role=local.getJSONObject("roles").optJSONObject(name);if(roles.has(name)||role==null)continue;String original=role.getString("provider_id"),target=original;
            if(!original.equals("local")){JSONObject old=null;for(int i=0;i<oldProviders.length();i++)if(oldProviders.getJSONObject(i).getString("id").equals(original))old=oldProviders.getJSONObject(i);if(old==null)throw new IOException("本机功能模型引用无效");String key=local.getJSONObject("secrets").optString(original,"");JSONObject replacement=byId.get(original);
                if(preserved.containsKey(original))target=preserved.get(original);else if(replacement!=null&&(!LedgerStore.equivalent(old,replacement)||!key.equals(secrets.optString(original,"")))){String suffix=ParcelSms.sha256((old.toString()+"\n"+key).getBytes(StandardCharsets.UTF_8)).substring(0,16);target="preserved-"+suffix;int count=0;while(byId.containsKey(target))target="preserved-"+suffix+"-"+(++count);}
                if(!byId.containsKey(target)){JSONObject copy=new JSONObject(old.toString()).put("id",target);providers.put(copy);byId.put(target,copy);if(!key.isEmpty())secrets.put(target,key);}preserved.put(original,target);
            }
            roles.put(name,new JSONObject(role.toString()).put("provider_id",target));
        }
        AiConfigCodec.validate(result);return result;
    }
}
