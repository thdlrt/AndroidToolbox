package net.lanbridge.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class AndroidConfigBackupTest {
    @Test public void configurationSchemaKeepsUsefulNasSettingsAndExcludesData()throws Exception{JSONObject value=AndroidConfigBackup.create(new JSONObject().put("favorite.ledger",false),"https://nas.example/","user","all");AndroidConfigBackup.validate(value);assertEquals("user",value.getJSONObject("preferences").getJSONObject("nas").getString("username"));assertFalse(value.toString().contains("cipher"));assertFalse(value.getJSONObject("preferences").has("ledger"));}
    @Test public void rejectsPasswordOrBusinessFields()throws Exception{JSONObject value=AndroidConfigBackup.create(new JSONObject(),"","","lan");value.getJSONObject("preferences").getJSONObject("nas").put("password","secret");try{AndroidConfigBackup.validate(value);fail();}catch(java.io.IOException expected){}value=AndroidConfigBackup.create(new JSONObject(),"","","lan");value.getJSONObject("preferences").put("ledger",new JSONObject());try{AndroidConfigBackup.validate(value);fail();}catch(java.io.IOException expected){}}
    @Test public void rejectsCredentialBearingNasUrl()throws Exception{try{AndroidConfigBackup.create(new JSONObject(),"https://user:secret@nas.example/","user","lan");fail();}catch(java.io.IOException expected){}}
    @Test public void fingerprintStableNormalizesUrlSlash()throws Exception{assertEquals(WebDavSettings.fingerprint("https://nas.example","old-inbox"),WebDavSettings.fingerprint("https://nas.example/","old-inbox"));assertNotEquals(WebDavSettings.fingerprint("https://nas.example/","old-inbox"),WebDavSettings.fingerprint("https://nas.example/","another"));}
}
