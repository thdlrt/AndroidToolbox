package net.lanbridge.android;

import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class HapiApiTest {
    @Test public void listsAllDirectoriesAndLoadsTheChosenThread()throws Exception{
        try(MockWebServer hub=new MockWebServer()){
            hub.enqueue(new MockResponse().setBody("{\"token\":\"private-bearer\"}"));
            hub.enqueue(new MockResponse().setBody("{\"success\":true,\"machineId\":\"pc\",\"sessions\":[{\"id\":\"existing\",\"title\":\"Desktop chat\",\"cwd\":\"C:\\\\project\"}]}"));
            hub.enqueue(new MockResponse().setBody("{\"success\":true,\"hapiSessionIds\":[\"history-id\"]}"));
            HapiApi api=new HapiApi(hub.url("/").toString().replaceFirst("/$",""),"local-secret");
            java.util.List<HapiApi.Session> sessions=api.sessions();assertEquals("Desktop chat",sessions.get(0).title);assertEquals("/sessions/history-id",api.load(sessions.get(0)));
            RecordedRequest auth=hub.takeRequest(),list=hub.takeRequest(),load=hub.takeRequest();
            assertEquals("/api/auth",auth.getPath());assertEquals("local-secret",new JSONObject(auth.getBody().readUtf8()).getString("accessToken"));
            assertEquals("/api/codex/sessions",list.getPath());assertEquals("Bearer private-bearer",list.getHeader("Authorization"));
            JSONObject body=new JSONObject(load.getBody().readUtf8());assertEquals("pc",body.getString("machineId"));assertEquals("existing",body.getJSONArray("sessionIds").getString(0));assertFalse(body.has("yolo"));api.close();
        }
    }
    @Test public void authRedirectDoesNotForwardToken()throws Exception{
        try(MockWebServer hub=new MockWebServer();MockWebServer other=new MockWebServer()){
            hub.enqueue(new MockResponse().setResponseCode(307).addHeader("Location",other.url("/capture")));
            HapiApi api=new HapiApi(hub.url("/").toString().replaceFirst("/$",""),"secret");
            try{api.sessions();fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("307"));}finally{api.close();}
            assertEquals(0,other.getRequestCount());
        }
    }
    @Test public void offlineMachineReturnsActionableError()throws Exception{
        try(MockWebServer hub=new MockWebServer()){
            hub.enqueue(new MockResponse().setBody("{\"token\":\"private\"}"));hub.enqueue(new MockResponse().setBody("{\"success\":false,\"error\":\"No online machine available\"}"));
            HapiApi api=new HapiApi(hub.url("/").toString().replaceFirst("/$",""),"secret");try{api.sessions();fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("online machine"));}finally{api.close();}
        }
    }
}
