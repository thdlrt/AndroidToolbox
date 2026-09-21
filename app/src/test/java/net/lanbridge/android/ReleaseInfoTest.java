package net.lanbridge.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class ReleaseInfoTest {
    private JSONObject release()throws Exception{String base="https://github.com/thdlrt/AndroidToolbox/releases/download/v0.3.0/AndroidToolbox-0.3.0.apk";return new JSONObject().put("tag_name","v0.3.0").put("assets",new JSONArray().put(new JSONObject().put("name","AndroidToolbox-0.3.0.apk").put("browser_download_url",base)).put(new JSONObject().put("name","AndroidToolbox-0.3.0.apk.sha256").put("browser_download_url",base+".sha256")));}
    @Test public void comparesNumericVersions(){assertTrue(ReleaseInfo.newer("v0.10.0","0.9.0"));assertFalse(ReleaseInfo.newer("0.2.0","0.2.0"));assertFalse(ReleaseInfo.newer("0.1.9","0.2.0"));assertThrows(IllegalArgumentException.class,()->ReleaseInfo.newer("1.0.0-beta","0.2.0"));}
    @Test public void requiresCompleteOfficialRelease()throws Exception{assertEquals("0.3.0",ReleaseInfo.parse(release().toString(),"0.2.0").version);assertNull(ReleaseInfo.parse(release().put("prerelease",true).toString(),"0.2.0"));assertNull(ReleaseInfo.parse(release().toString(),"0.3.0"));assertThrows(IllegalArgumentException.class,()->ReleaseInfo.parse(release().put("assets",new JSONArray()).toString(),"0.2.0"));}
    @Test public void rejectsOtherHostsAndRepositories()throws Exception{for(String url:new String[]{"http://github.com/thdlrt/AndroidToolbox/releases/download/v0.3.0/AndroidToolbox-0.3.0.apk","https://evil.example/a.apk","https://github.com/other/repo/releases/download/v0.3.0/AndroidToolbox-0.3.0.apk"}){JSONObject r=release();r.getJSONArray("assets").getJSONObject(0).put("browser_download_url",url);assertThrows(IllegalArgumentException.class,()->ReleaseInfo.parse(r.toString(),"0.2.0"));}}
    @Test public void rejectsMalformedHashes(){String hash="ab".repeat(32);assertEquals(hash,ReleaseInfo.checksum(hash.toUpperCase()+"  app.apk\n"));assertThrows(IllegalArgumentException.class,()->ReleaseInfo.checksum("invalid"));}
    @Test public void boundsMetadata()throws Exception{assertEquals("hello",Streams.text(new java.io.ByteArrayInputStream("hello".getBytes()),5));assertThrows(java.io.IOException.class,()->Streams.text(new java.io.ByteArrayInputStream(new byte[6]),5));}
}
