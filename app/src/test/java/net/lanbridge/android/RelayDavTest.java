package net.lanbridge.android;
import org.junit.*;
import static org.junit.Assert.*;
import okhttp3.mockwebserver.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class RelayDavTest {
    MockWebServer server; RelayDav dav;
    @Before public void setup()throws Exception{server=new MockWebServer();server.start();dav=new RelayDav(server.url("/").toString(),"shared/inbox","user","fixture-password");}
    @After public void close()throws Exception{dav.close();server.shutdown();}
    String row(String path,boolean folder,String extra){return "<d:response><d:href>/shared/inbox/"+path+"</d:href><d:propstat><d:prop><d:resourcetype>"+(folder?"<d:collection/>":"")+"</d:resourcetype>"+extra+"</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><d:getcontentlength/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>";}
    MockResponse xml(String rows){return new MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\">"+rows+"</d:multistatus>");}
    String props(){return "<d:getcontentlength>7</d:getcontentlength><d:getetag>&quot;v1&quot;</d:getetag><d:getlastmodified>Thu, 01 Jan 2026 00:00:00 GMT</d:getlastmodified>";}
    @Test public void scopedUnicodeListingWithSplitProperties()throws Exception{
        server.enqueue(xml(row("",true,"")+row("%E4%B8%AD%E6%96%87.txt",false,props())));
        List<RelayDav.Entry> entries=dav.list("");assertEquals(1,entries.size());assertEquals("中文.txt",entries.get(0).name);assertEquals(7,entries.get(0).size);
        RecordedRequest r=server.takeRequest();assertEquals("PROPFIND",r.getMethod());assertEquals("1",r.getHeader("Depth"));assertNotNull(r.getHeader("Authorization"));
    }
    @Test public void rejectsTraversalCredentialsAndXmlEntities()throws Exception{
        for(String p:new String[]{"../escape","a/../../escape","a\\b","/absolute","CON","a//b"})try{RelayDav.path(p,false);fail(p);}catch(IOException expected){}
        try{RelayDav.endpoint("https://user:secret@example.com/");fail();}catch(IOException expected){}
        server.enqueue(new MockResponse().setResponseCode(207).setBody("<!DOCTYPE d [<!ENTITY x SYSTEM 'file:///secret'>]><d/>") );
        try{dav.list("");fail();}catch(IOException expected){}
    }
    @Test public void doesNotFollowCredentialRedirect()throws Exception{
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","https://unrelated.invalid/"));
        try{dav.list("");fail();}catch(IOException expected){}assertEquals(1,server.getRequestCount());
    }
    @Test public void fnosMoveFallbackNeverOverwrites()throws Exception{
        server.enqueue(xml(row("",true,"")));server.enqueue(new MockResponse().setResponseCode(404));server.enqueue(new MockResponse().setResponseCode(201));server.enqueue(new MockResponse().setResponseCode(502));server.enqueue(new MockResponse().setResponseCode(201));server.enqueue(xml(row("test.txt",false,props())));server.enqueue(new MockResponse().setResponseCode(404));
        File file=File.createTempFile("relay-test", ".txt");Files.write(file.toPath(),"fixture".getBytes());
        try{dav.upload(file,"test.txt",(n,t)->{});}finally{file.delete();}
        List<RecordedRequest> requests=new ArrayList<>();for(int i=0;i<7;i++)requests.add(server.takeRequest());
        assertEquals("*",requests.get(2).getHeader("If-None-Match"));assertEquals("fixture",requests.get(2).getBody().readUtf8());
        assertEquals("F",requests.get(3).getHeader("Overwrite"));assertEquals("/shared/inbox/test.txt",requests.get(4).getHeader("Destination"));assertEquals("F",requests.get(4).getHeader("Overwrite"));
    }
    @Test public void cleanupSkipsChangedFiles()throws Exception{
        server.enqueue(xml(row("",true,"")+row("old.txt",false,props())+row("weak.txt",false,props().replace("&quot;v1&quot;","W/&quot;v1&quot;"))));
        List<RelayDav.Entry> preview=dav.cleanupPreview(7);assertEquals(1,preview.size());
        server.enqueue(xml(row("old.txt",false,props().replace("v1","v2"))));assertFalse(dav.deleteUnchanged(preview.get(0)));assertEquals(2,server.getRequestCount());
    }
    @Test public void downloadsWithVersionCheck()throws Exception{
        RelayDav.Entry entry=new RelayDav.Entry("test.txt",false,7,0,"\"v1\"");server.enqueue(new MockResponse().setBody("fixture"));server.enqueue(xml(row("test.txt",false,"<d:getcontentlength>7</d:getcontentlength><d:getetag>&quot;v1&quot;</d:getetag>")));
        File output=File.createTempFile("relay-download", ".txt");try{dav.download(entry,output,(n,t)->{});assertEquals("fixture",new String(Files.readAllBytes(output.toPath()), java.nio.charset.StandardCharsets.UTF_8));}finally{output.delete();}
        assertEquals("\"v1\"",server.takeRequest().getHeader("If-Match"));
    }
}
