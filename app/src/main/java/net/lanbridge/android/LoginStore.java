package net.lanbridge.android;

import android.content.*;
import android.security.keystore.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** NAS passwords are bound to this device's Android Keystore and excluded from backup. */
public final class LoginStore {
    private final String alias;
    private final SharedPreferences preferences;
    public LoginStore(Context context) { this(context,"profile","lanbridge-login-v1"); }
    public LoginStore(Context context,String name,String alias) { preferences=context.getSharedPreferences(name,Context.MODE_PRIVATE);this.alias=alias; }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(alias)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        return (SecretKey)ks.getKey(alias,null);
    }
    public boolean hasPassword() { return preferences.contains("cipher"); }
    public String origin() { return preferences.getString("origin",""); }
    public String username() { return preferences.getString("username",""); }
    public String revision() { return preferences.getString("revision",""); }
    public String scope() { return preferences.getString("scope","lan"); }
    public void config(String origin,String username,String scope) { preferences.edit().putString("origin",origin).putString("username",username).putString("scope",scope).apply(); }
    public void save(String origin,String username,String password,String scope) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] plain=new JSONObject().put("origin",origin).put("username",username).put("password",password).toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted=cipher.doFinal(plain);
        preferences.edit().putString("origin",origin).putString("username",username).putString("scope",scope).putString("revision",java.util.UUID.randomUUID().toString())
            .putString("iv",Base64.getEncoder().encodeToString(cipher.getIV())).putString("cipher",Base64.getEncoder().encodeToString(encrypted)).apply();
        java.util.Arrays.fill(plain,(byte)0);
    }
    public String password(String origin,String username) throws Exception {
        if(!hasPassword()) return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(preferences.getString("iv",""))));
        byte[] bytes=cipher.doFinal(Base64.getDecoder().decode(preferences.getString("cipher","")));
        JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));java.util.Arrays.fill(bytes,(byte)0);
        return origin.equals(value.getString("origin")) && username.equals(value.getString("username"))?value.getString("password"):"";
    }
    public void forget() { preferences.edit().remove("iv").remove("cipher").apply(); }
}
