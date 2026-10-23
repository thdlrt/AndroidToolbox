package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class CodexWebConnectionTest {
    @Test public void explicitWebuiPortHasNoTranslation()throws Exception{assertEquals("http://192.168.1.10:9526",CodexWebConnection.parse("192.168.1.10:9526", "x").origin);}
    @Test public void acceptsFragmentQrWithoutKeepingSecretInOrigin()throws Exception{CodexWebConnection v=CodexWebConnection.parse("https://host/#token=cw_a%2Bb", "");assertEquals("https://host",v.origin);assertEquals("cw_a+b",v.token);}
    @Test public void explicitTokenOverridesUrl()throws Exception{assertEquals("new",CodexWebConnection.parse("http://host/?token=old", " new ").token);}
    @Test public void rejectsOtherPathsAndFragments(){for(String s:new String[]{"javascript:alert(1)","https://user:pw@host","https://host/chat/123","https://host/#x=y","https://host/?redirect=elsewhere",""})try{CodexWebConnection.parse(s,"");fail(s);}catch(java.io.IOException expected){}}
    @Test public void supportsIpv6AndHttps()throws Exception{assertEquals("http://[::1]:9526",CodexWebConnection.parse("http://[::1]:9526/", "x").origin);assertEquals("https://host",CodexWebConnection.parse("https://host:443", "x").origin);}
}
