package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.*;

public class RecoveringDnsTest {
    @Test public void successfulSystemLookupDoesNotRetry()throws Exception{
        InetAddress ip=InetAddress.getByName("127.0.0.1");
        assertEquals(ip,new RecoveringDns(h->Collections.singletonList(ip),h->{throw new AssertionError("Unexpected fresh query");}).lookup("fixture.test").get(0));
    }
    @Test public void negativeCacheRecoversOnceOnSameHostname()throws Exception{
        AtomicInteger calls=new AtomicInteger();
        RecoveringDns dns=new RecoveringDns(h->{throw new UnknownHostException(h);},h->{assertEquals("fixture.test",h);calls.incrementAndGet();return Collections.singletonList(InetAddress.getByName("127.0.0.1"));});
        assertEquals("127.0.0.1",dns.lookup("fixture.test").get(0).getHostAddress());assertEquals(1,calls.get());
    }
    @Test public void repeatedFailureRemainsAnErrorWithoutLooping()throws Exception{
        AtomicInteger calls=new AtomicInteger();
        try{new RecoveringDns(h->{throw new UnknownHostException("cached");},h->{calls.incrementAndGet();throw new UnknownHostException("fresh");}).lookup("fixture.test");fail();}
        catch(UnknownHostException error){assertEquals("cached",error.getMessage());assertEquals(1,error.getSuppressed().length);}assertEquals(1,calls.get());
    }
    @Test public void cancellationSkipsRecovery()throws Exception{
        Thread.currentThread().interrupt();try{new RecoveringDns(h->{throw new UnknownHostException(h);},h->{throw new AssertionError("Recovery after cancellation");}).lookup("fixture.test");fail();}catch(UnknownHostException expected){}finally{Thread.interrupted();}
    }
    @Test public void recoveredWebDavKeepsHostAndAuthentication()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/shared/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"));
            RecoveringDns dns=new RecoveringDns(h->{throw new UnknownHostException(h);},h->Collections.singletonList(InetAddress.getByName("127.0.0.1")));
            try(RelayDav dav=new RelayDav("http://relay.fixture.test:"+server.getPort(),"shared","user","secret",dns)){assertTrue(dav.list("").isEmpty());}
            RecordedRequest request=server.takeRequest();assertEquals("relay.fixture.test:"+server.getPort(),request.getHeader("Host"));assertEquals(okhttp3.Credentials.basic("user","secret"),request.getHeader("Authorization"));assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void unresolvedWebDavReportsHostWithoutCredentials()throws Exception{
        try(RelayDav dav=new RelayDav("https://relay.fixture.test","shared","user","secret",h->{throw new UnknownHostException(h);})){dav.list("");fail();}
        catch(java.io.IOException error){assertTrue(error.getMessage().contains("relay.fixture.test"));assertTrue(error.getMessage().contains("DNS"));assertFalse(error.getMessage().contains("secret"));}
    }
}
