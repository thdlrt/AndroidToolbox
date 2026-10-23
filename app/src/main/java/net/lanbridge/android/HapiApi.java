package net.lanbridge.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Uses the hub's authenticated history-import API; never exposes the CLI token in a URL. */
final class HapiApi {
    static final class Session {
        final String id,title,cwd;
        Session(JSONObject value){id=value.optString("id");title=value.optString("title",id);cwd=value.optString("cwd","");}
        @Override public String toString(){return title+"\n"+cwd;}
    }
    private final OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(45,TimeUnit.SECONDS).build();
    private final String origin,accessToken;
    private String bearer="",machineId="";
    HapiApi(String origin,String token){this.origin=origin;accessToken=token;}
    private JSONObject request(String path,JSONObject body)throws Exception{
        Request.Builder builder=new Request.Builder().url(origin+path).header("Cache-Control","no-store");
        if(!bearer.isEmpty())builder.header("Authorization","Bearer "+bearer);
        if(body!=null)builder.post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8")));
        try(Response response=client.newCall(builder.build()).execute()){
            if(!response.isSuccessful())throw new IOException("HAPI HTTP "+response.code()+"，请检查电脑服务及访问令牌。");
            JSONObject result=new JSONObject(response.body().string());
            if(result.has("success")&&!result.optBoolean("success"))throw new IOException(result.optString("error","读取电脑会话失败"));
            return result;
        }
    }
    private void authenticate()throws Exception{
        if(bearer.isEmpty()){bearer=request("/api/auth",new JSONObject().put("accessToken",accessToken)).getString("token");if(bearer.isEmpty())throw new IOException("HAPI 登录失败");}
    }
    List<Session> sessions()throws Exception{
        authenticate();JSONObject result=request("/api/codex/sessions",null);machineId=result.optString("machineId","");
        List<Session> sessions=new ArrayList<>();JSONArray rows=result.getJSONArray("sessions");
        for(int i=0;i<rows.length();i++){Session session=new Session(rows.getJSONObject(i));if(!session.id.isEmpty())sessions.add(session);}return sessions;
    }
    String load(Session session)throws Exception{
        authenticate();JSONObject request=new JSONObject().put("sessionIds",new JSONArray().put(session.id));
        if(!machineId.isEmpty())request.put("machineId",machineId);
        JSONArray imported=request("/api/codex/sync-session",request).getJSONArray("hapiSessionIds");
        if(imported.length()!=1)throw new IOException("电脑会话未载入，请刷新重试");
        String id=imported.getString(0);if(!id.matches("[A-Za-z0-9_-]{1,200}"))throw new IOException("HAPI 会话地址无效");
        return "/sessions/"+id;
    }
    void close(){client.dispatcher().cancelAll();client.connectionPool().evictAll();}
}
