package net.lanbridge.android;

import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Shared encrypted AI configuration format; never contains SMS or parcel data. */
final class AiConfigCodec {
    static final int LIMIT=1024*1024, ITERATIONS=200000;
    static final String FORMAT="wintoolbox-ai-config", ENCRYPTED="wintoolbox-ai-config-encrypted";
    static final byte[] AAD="wintoolbox-ai-config-v1".getBytes(StandardCharsets.UTF_8);
    static String string(JSONObject o,String key,int limit)throws Exception {
        Object value=o.get(key);if(!(value instanceof String)||((String)value).length()>limit)throw new IOException("AI 配置字段无效："+key);return (String)value;
    }
    static String endpoint(String text)throws Exception {
        URI u=new URI(text);if(!Arrays.asList("http","https").contains(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new IOException("请填写完整 HTTP/HTTPS AI 服务地址，密钥单独保存");
        return text.replaceAll("/+$","");
    }
    static void validate(JSONObject value)throws Exception {
        if(value.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT||!FORMAT.equals(value.optString("format"))||value.optInt("version")!=1)throw new IOException("AI 配置格式或大小无效");
        JSONArray providers=value.getJSONArray("providers");JSONObject roles=value.getJSONObject("roles"),secrets=value.getJSONObject("secrets");Set<String> ids=new HashSet<>();
        if(providers.length()>100||roles.length()>100||secrets.length()>100)throw new IOException("AI 配置项目过多");
        for(int i=0;i<providers.length();i++){JSONObject p=providers.getJSONObject(i);String id=string(p,"id",100);if(!id.matches("[A-Za-z0-9._-]+")||!ids.add(id))throw new IOException("AI 服务标识无效或重复");
            if(!Arrays.asList("openai","dashscope","gemini").contains(string(p,"kind",30)))throw new IOException("AI 服务类型不受支持");endpoint(string(p,"base_url",2048));if(p.has("name"))string(p,"name",200);if(p.has("region"))string(p,"region",50);
        }
        Iterator<String> keys=roles.keys();while(keys.hasNext()){String name=keys.next();if(!name.matches("[A-Za-z0-9._-]{1,100}"))throw new IOException("AI 角色无效");JSONObject r=roles.getJSONObject(name);if(!ids.contains(string(r,"provider_id",100))&&!"local".equals(r.getString("provider_id")))throw new IOException("AI 角色引用不存在的服务");string(r,"model",200);}
        keys=secrets.keys();while(keys.hasNext()){String id=keys.next();if(!ids.contains(id))throw new IOException("AI 密钥引用不存在的服务");string(secrets,id,8192);}
    }
    static JSONObject encrypt(JSONObject value,String password)throws Exception {
        byte[] salt=new byte[16],nonce=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(nonce);return encrypt(value,password,salt,nonce);
    }
    static JSONObject encrypt(JSONObject value,String password,byte[] salt,byte[] nonce)throws Exception {
        validate(value);if(password.isEmpty())throw new IOException("缺少同步密码");byte[] result=crypt(Cipher.ENCRYPT_MODE,value.toString().getBytes(StandardCharsets.UTF_8),password,salt,nonce);
        JSONObject envelope=new JSONObject().put("format",ENCRYPTED).put("version",1).put("salt",Base64.getEncoder().encodeToString(salt)).put("nonce",Base64.getEncoder().encodeToString(nonce)).put("ciphertext",Base64.getEncoder().encodeToString(result));if(envelope.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("AI 配置密文过大");return envelope;
    }
    static JSONObject decrypt(JSONObject envelope,String password)throws Exception {
        try{
            if(!ENCRYPTED.equals(envelope.optString("format"))||envelope.optInt("version")!=1||envelope.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("AI 配置密文格式无效");
            byte[] salt=Base64.getDecoder().decode(envelope.getString("salt")),nonce=Base64.getDecoder().decode(envelope.getString("nonce")),body=Base64.getDecoder().decode(envelope.getString("ciphertext"));
            if(salt.length!=16||nonce.length!=12||body.length>LIMIT+16||body.length<16)throw new IOException("AI 配置密文长度无效");
            byte[] plain=crypt(Cipher.DECRYPT_MODE,body,password,salt,nonce);JSONObject value;
            try{value=new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}validate(value);return value;
        }catch(javax.crypto.AEADBadTagException e){throw new IOException("AI 配置无法解密：请确认两端使用相同 WebDAV 密码，或重新上传配置");}
    }
    private static byte[] crypt(int mode,byte[] body,String password,byte[] salt,byte[] nonce)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);byte[] key;
        try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);return cipher.doFinal(body);}finally{Arrays.fill(key,(byte)0);}
    }
}
