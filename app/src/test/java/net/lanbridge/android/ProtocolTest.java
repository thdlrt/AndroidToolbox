package net.lanbridge.android;

import org.junit.Test;
import static org.junit.Assert.*;
import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ProtocolTest {
    @Test public void originsAreRestrictedToNas() {
        assertEquals("https://abc.fnos.net",NasSession.origin("https://abc.fnos.net/"));
        assertEquals("http://192.168.50.2:5666",NasSession.origin("http://192.168.50.2:5666"));
        for(String invalid:new String[]{"http://abc.fnos.net","https://example.com","http://127.0.0.1","https://u:p@abc.fnos.net","https://abc.fnos.net/app"}) {
            try { NasSession.origin(invalid);fail(invalid); } catch(IllegalArgumentException expected) {}
        }
    }
    @Test public void cryptoMatchesProtocol() throws Exception {
        byte[] key=new byte[32],iv=new byte[16];Arrays.fill(key,(byte)'a');Arrays.fill(iv,(byte)'b');
        byte[] input="飞牛测试登录".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input,NasSession.aes(NasSession.aes(input,key,iv,Cipher.ENCRYPT_MODE),key,iv,Cipher.DECRYPT_MODE));
        String message="{\"req\":\"user.active\"}";
        assertTrue(NasSession.signed(message,key).endsWith(message));
        assertEquals(44+message.length(),NasSession.signed(message,key).length());
    }
    @Test public void routesArePrivateAndGlobalDnsIsMapped() {
        assertEquals("192.168.100.0/24",VpnConfig.network("192.168.100.0/24"));
        for(String invalid:new String[]{"0.0.0.0/0","192.168.100.1/24","172.0.0.0/8","192.168.0.0/8"}) {
            try { VpnConfig.network(invalid);fail(invalid); } catch(IllegalArgumentException expected) {}
        }
        assertTrue(VpnConfig.nativeConfig(18792,"all").contains("mapdns:"));
        assertFalse(VpnConfig.nativeConfig(18792,"lan").contains("mapdns:"));
    }
    @Test public void cookieReplacementAndPathIsolation() {
        NasSession.MemoryCookies jar=new NasSession.MemoryCookies();okhttp3.HttpUrl url=okhttp3.HttpUrl.get("https://test.fnos.net/");
        jar.saveFromResponse(url,Collections.singletonList(okhttp3.Cookie.parse(url,"ost=first;Path=/;HttpOnly;Secure")));
        jar.saveFromResponse(url,Collections.singletonList(okhttp3.Cookie.parse(url,"ost=second;Path=/;HttpOnly;Secure")));
        assertEquals(1,jar.loadForRequest(url).size());assertEquals("second",jar.loadForRequest(url).get(0).value());
        assertTrue(jar.loadForRequest(okhttp3.HttpUrl.get("https://other.fnos.net/")).isEmpty());
        assertTrue(jar.loadForRequest(okhttp3.HttpUrl.get("http://test.fnos.net/")).isEmpty());
    }
}
