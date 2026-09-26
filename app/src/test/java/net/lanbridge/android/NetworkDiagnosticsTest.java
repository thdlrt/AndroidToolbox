package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import okhttp3.mockwebserver.*;
public class NetworkDiagnosticsTest {
    @Test public void rejectsSecretsAndAcceptsHosts(){assertEquals("example.com",NetworkDiagnostics.target("example.com").host());for(String target:new String[]{"https://user:pass@example.com","https://example.com/?token=x","https://example.com/#secret","ftp://example.com"})try{NetworkDiagnostics.target(target);fail(target);}catch(IllegalArgumentException expected){}}
    @Test public void reportsLocalServerAndHeadFallback()throws Exception{try(MockWebServer server=new MockWebServer()){server.enqueue(new MockResponse().setResponseCode(405));server.enqueue(new MockResponse().setResponseCode(200));server.start();JSONObject report=NetworkDiagnostics.run(server.url("/").toString(),0,1000,2);JSONArray steps=report.getJSONArray("steps");assertEquals("ok",steps.getJSONObject(0).getString("status"));assertEquals(2,steps.getJSONObject(1).getInt("success_count"));assertEquals("skipped",steps.getJSONObject(2).getString("status"));assertEquals(200,steps.getJSONObject(3).getInt("http_status"));assertEquals("HEAD",server.takeRequest().getMethod());assertEquals("GET",server.takeRequest().getMethod());}}
}
