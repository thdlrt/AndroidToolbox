package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class HapiConnectionTest {
    @Test public void separatesPastedTokenFromOrigin()throws Exception{
        HapiConnection c=HapiConnection.parse(" 192.168.1.10:3006/?token=a%2Bb%3Ans ","");
        assertEquals("http://192.168.1.10:3006",c.origin);assertEquals("a+b:ns",c.token);
        assertFalse(c.origin.contains("token"));
    }
    @Test public void explicitTokenWins()throws Exception{assertEquals("chosen",HapiConnection.parse("https://host/?token=old"," chosen ").token);}
    @Test public void recognizesExactOrigin(){assertTrue(HapiConnection.sameOrigin("https://host","https://host/sessions/123"));assertFalse(HapiConnection.sameOrigin("https://host","http://host/"));assertFalse(HapiConnection.sameOrigin("http://host:3006","http://host:3007"));assertFalse(HapiConnection.sameOrigin("https://host","https://host.evil/"));}
    @Test public void rejectsCredentialAndRedirectUrls(){for(String v:new String[]{"javascript:alert(1)","https://user:pass@host","https://host/?hub=https://other","https://host/sessions/abc","https://host/#secret",""}){try{HapiConnection.parse(v,"");fail(v);}catch(java.io.IOException expected){}}}
    @Test public void supportsIpv6AndHttps()throws Exception{assertEquals("http://[::1]:3006",HapiConnection.parse("http://[::1]:3006/","x").origin);assertEquals("https://example.test",HapiConnection.parse("https://example.test:443/","x").origin);}
    @Test public void desktopUsesAdjacentPortAndKeepsTransport(){assertEquals("http://192.168.1.10:3007",HapiConnection.desktopOrigin("http://192.168.1.10:3006"));assertEquals("https://example.test:444",HapiConnection.desktopOrigin("https://example.test"));assertEquals("http://[::1]:4001",HapiConnection.desktopOrigin("http://[::1]:4000"));}
    @Test(expected=IllegalArgumentException.class) public void desktopRejectsOverflowPort(){HapiConnection.desktopOrigin("http://host:65535");}
}
