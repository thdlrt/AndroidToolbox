package net.lanbridge.android;
import org.junit.Test;
import org.json.*;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public final class ParcelReasoningTest {
    private JSONObject request(String kind,String model,String effort)throws Exception{JSONObject p=new JSONObject().put("temperature",0).put("max_tokens",8192);ParcelReasoning.apply(p,new ParcelAi.Config("https://fixture.invalid/v1","test",model,kind,effort));return p;}
    @Test public void modelDefaultPreservesExistingRequest()throws Exception{JSONObject p=request("dashscope","qwen3.8-flash","default");assertEquals(2,p.length());assertEquals(8192,p.getInt("max_tokens"));assertEquals("default",new ParcelAi.Config("https://fixture.invalid","test","m").reasoning);}
    @Test public void qwenSendsBooleanAndBoundedBudgetsWithoutConflictingEffort()throws Exception{
        for(String model:List.of("qwen3.8-flash","qwen3.5-plus","qwen3-vl-plus")){int i=0;for(String effort:List.of("low","medium","high")){JSONObject p=request("openai",model,effort);assertTrue(p.getBoolean("enable_thinking"));assertEquals(new int[]{4096,16384,32768}[i++],p.getInt("thinking_budget"));assertFalse(p.has("reasoning_effort"));}}
        JSONObject off=request("dashscope","qwen3.8-flash","off");assertFalse(off.getBoolean("enable_thinking"));assertFalse(off.has("thinking_budget"));
        JSONObject omni=request("dashscope","qwen3.8-omni-flash","high");assertEquals("xhigh",omni.getString("reasoning_effort"));assertFalse(omni.has("thinking_budget"));
    }
    @Test public void standardUsesCompletionTokenBudgetAndOmitsUnsupportedSampling()throws Exception{for(String effort:List.of("low","medium","high","off")){JSONObject p=request("openai","gpt-reasoning",effort);assertEquals(effort.equals("off")?"none":effort,p.getString("reasoning_effort"));assertFalse(p.has("max_tokens"));assertFalse(p.has("temperature"));assertFalse(p.has("enable_thinking"));assertEquals(effort.equals("off")?8192:32768,p.getInt("max_completion_tokens"));}}
    @Test public void deepseekAndBailianUseTheirOwnToggles()throws Exception{int i=0;for(String effort:List.of("low","medium","high")){JSONObject p=request("openai","deepseek-flash",effort);assertEquals("enabled",p.getJSONObject("thinking").getString("type"));assertEquals(new String[]{"low","high","max"}[i++],p.getString("reasoning_effort"));assertFalse(p.has("temperature"));}
        JSONObject off=request("openai","deepseek-flash","off");assertEquals("disabled",off.getJSONObject("thinking").getString("type"));assertFalse(off.has("reasoning_effort"));JSONObject ali=request("dashscope","deepseek-v4-pro","medium");assertTrue(ali.getBoolean("enable_thinking"));assertFalse(ali.has("thinking"));assertFalse(ali.has("thinking_budget"));assertEquals("high",ali.getString("reasoning_effort"));
    }
    @Test public void effortIsSentOverHttpAndOnlyFinalContentIsParsed()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            JSONObject item=new JSONObject().put("source_ids",new JSONArray().put("test")).put("code","12-345").put("tracking_number","").put("carrier","").put("location","北门").put("locker","").put("status",ParcelAi.PENDING).put("deadline",JSONObject.NULL);
            server.enqueue(new MockResponse().setBody(new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop").put("message",new JSONObject().put("reasoning_content","private reasoning must not enter records").put("content",new JSONObject().put("items",new JSONArray().put(item)).toString())))).toString()));server.start();
            ParcelAi.Item parsed=ParcelAi.parse(new ParcelAi.Config(server.url("/v1").toString(),"test","qwen3.8-flash","dashscope","medium"),List.of(new ParcelSms.Message("test",1,"10600","取件码12-345在北门")),List.of()).get(0);assertEquals("12-345",parsed.code);
            JSONObject body=new JSONObject(server.takeRequest().getBody().readUtf8());assertTrue(body.getBoolean("enable_thinking"));assertEquals(16384,body.getInt("thinking_budget"));assertEquals("json_object",body.getJSONObject("response_format").getString("type"));
        }
    }
    @Test public void unsupportedParamsAreActionableWithoutSilentFallbackOrPrivateLeaks()throws Exception{
        try{new ParcelAi.Config("https://fixture.invalid","test","m","openai","unknown");fail();}catch(IllegalArgumentException expected){}
        try(MockWebServer server=new MockWebServer()){server.enqueue(new MockResponse().setResponseCode(400).setBody("private payload and sk-secret"));server.start();try{ParcelAi.parse(new ParcelAi.Config(server.url("/v1").toString(),"sk-secret","m","openai","high"),List.of(new ParcelSms.Message("test",1,"10600","私密短信")),List.of());fail();}catch(IOException e){assertTrue(e.getMessage().contains("模型默认"));assertFalse(e.getMessage().contains("sk-secret"));assertFalse(e.getMessage().contains("私密短信"));}assertEquals(1,server.getRequestCount());}
    }
}
