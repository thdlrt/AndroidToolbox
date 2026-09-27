package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

public class AiConfigCodecTest {
    private JSONObject vector()throws Exception{try(InputStream in=getClass().getResourceAsStream("/ai-config-vector-v1.json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    @Test public void decryptsWindowsUnicodePasswordVector()throws Exception{JSONObject v=vector();JSONObject plain=AiConfigCodec.decrypt(v.getJSONObject("envelope"),v.getString("password"));assertTrue(LedgerStore.equivalent(plain,v.getJSONObject("plaintext")));}
    @Test public void wrongPasswordAndTamperingNeverProduceConfiguration()throws Exception{JSONObject v=vector();try{AiConfigCodec.decrypt(v.getJSONObject("envelope"),"wrong");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("解密"));}JSONObject envelope=new JSONObject(v.getJSONObject("envelope").toString());String data=envelope.getString("ciphertext");envelope.put("ciphertext",(data.charAt(0)=='A'?"B":"A")+data.substring(1));try{AiConfigCodec.decrypt(envelope,v.getString("password"));fail();}catch(IOException expected){}}
    @Test public void freshRandomnessAndRoundTripAndPythonInterop()throws Exception{JSONObject v=vector(),a=AiConfigCodec.encrypt(v.getJSONObject("plaintext"),v.getString("password")),b=AiConfigCodec.encrypt(v.getJSONObject("plaintext"),v.getString("password"));assertNotEquals(a.getString("salt"),b.getString("salt"));assertTrue(LedgerStore.equivalent(AiConfigCodec.decrypt(a,v.getString("password")),v.getJSONObject("plaintext")));System.out.println("AI_INTEROP="+a);}
    @Test public void invalidReferencesAndUrlsRejected()throws Exception{JSONObject p=vector().getJSONObject("plaintext");p.getJSONObject("roles").getJSONObject("chat").put("provider_id","missing");try{AiConfigCodec.validate(p);fail();}catch(IOException expected){}p.getJSONObject("roles").getJSONObject("chat").put("provider_id","local");AiConfigCodec.validate(p);p.getJSONArray("providers").getJSONObject(0).put("base_url","https://secret@example.invalid/v1");try{AiConfigCodec.validate(p);fail();}catch(IOException expected){}}
}
