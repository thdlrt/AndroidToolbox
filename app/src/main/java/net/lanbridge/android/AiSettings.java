package net.lanbridge.android;

import android.content.Context;
import org.json.*;
import java.io.IOException;

/** Whole profile snapshots are encrypted using this device's Keystore. */
final class AiSettings {
    private static final Object LOCK=ConfigBackupService.CONFIG_LOCK;
    private final Context context;
    private final LoginStore store;
    AiSettings(Context c){context=c.getApplicationContext();store=new LoginStore(context,"ai-config","toolbox-ai-config-v1");}
    private JSONObject empty()throws Exception{return new JSONObject().put("format",AiConfigCodec.FORMAT).put("version",1).put("providers",new JSONArray()).put("roles",new JSONObject()).put("secrets",new JSONObject());}
    JSONObject exportSnapshot()throws Exception{synchronized(LOCK){String text=store.password("ai-config","shared");JSONObject value=text.isEmpty()?empty():new JSONObject(text);AiConfigCodec.validate(value);return value;}}
    String revision(){return store.revision();}
    boolean enabled(){return context.getSharedPreferences("parcel-local",0).getBoolean("ai_enabled",false);}
    void setEnabled(boolean enabled)throws Exception{synchronized(LOCK){if(enabled){parcelConfig(false);parcelConfig(true);}if(!context.getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",enabled).commit())throw new IOException("无法保存取件 AI 开关");}}
    JSONObject profiles()throws Exception{synchronized(LOCK){JSONObject value=exportSnapshot();JSONArray safe=new JSONArray(value.getJSONArray("providers").toString());for(int i=0;i<safe.length();i++){JSONObject p=safe.getJSONObject(i);p.put("has_key",!value.getJSONObject("secrets").optString(p.getString("id")).isEmpty());}return new JSONObject().put("providers",safe).put("roles",new JSONObject(value.getJSONObject("roles").toString())).put("enabled",enabled()).put("revision",revision());}}
    String saveProvider(String id,String name,String kind,String baseUrl,String apiKey)throws Exception{synchronized(LOCK){
        JSONObject value=exportSnapshot();id=id==null?"":id.trim();if(id.isEmpty())id=java.util.UUID.randomUUID().toString();name=name.trim();baseUrl=AiConfigCodec.endpoint(baseUrl.trim());apiKey=apiKey.trim();
        if(name.isEmpty()||name.length()>200||!id.matches("[A-Za-z0-9._-]{1,100}")||apiKey.length()>8192||!java.util.Arrays.asList("openai","dashscope","gemini").contains(kind))throw new IOException("供应商字段无效");
        JSONObject p=provider(value,id);if(p.has("base_url")&&apiKey.isEmpty()&&!baseUrl.equals(AiConfigCodec.endpoint(p.getString("base_url"))))throw new IOException("服务地址已更改，请重新填写 API Key");
        if(p.length()==0){p=new JSONObject().put("id",id);value.getJSONArray("providers").put(p);}p.put("name",name).put("kind",kind).put("base_url",baseUrl);if(!apiKey.isEmpty())value.getJSONObject("secrets").put(id,apiKey);
        AiConfigCodec.validate(value);store.save("ai-config","shared",value.toString(),"");return id;
    }}
    void removeProvider(String id)throws Exception{synchronized(LOCK){JSONObject value=exportSnapshot();JSONObject roles=value.getJSONObject("roles");java.util.Iterator<String> keys=roles.keys();while(keys.hasNext())if(id.equals(roles.getJSONObject(keys.next()).optString("provider_id")))throw new IOException("该供应商仍被功能使用，请先更改功能模型配置");JSONArray next=new JSONArray(),old=value.getJSONArray("providers");for(int i=0;i<old.length();i++)if(!id.equals(old.getJSONObject(i).getString("id")))next.put(old.getJSONObject(i));value.put("providers",next);value.getJSONObject("secrets").remove(id);AiConfigCodec.validate(value);store.save("ai-config","shared",value.toString(),"");}}
    void saveRoles(String textProvider,String textModel,String visionProvider,String visionModel,boolean enabled)throws Exception{synchronized(LOCK){
        JSONObject value=exportSnapshot();textModel=textModel.trim();visionModel=visionModel.trim();if(visionProvider==null||visionProvider.isEmpty())visionProvider=textProvider;if(visionModel.isEmpty()&&visionProvider.equals(textProvider))visionModel=textModel;
        String[] ids={textProvider,visionProvider},models={textModel,visionModel},names={"parcel","parcel_vision"};for(int i=0;i<2;i++){JSONObject p=provider(value,ids[i]);if(p.length()==0||"gemini".equals(p.optString("kind")))throw new IOException("取件功能请选择 OpenAI 兼容供应商");if(models[i].isEmpty()||models[i].length()>200)throw new IOException("请填写文本和图片模型 ID");value.getJSONObject("roles").put(names[i],new JSONObject().put("provider_id",ids[i]).put("model",models[i]));}
        AiConfigCodec.validate(value);store.save("ai-config","shared",value.toString(),"");if(!context.getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",enabled).commit())throw new IOException("无法保存取件 AI 开关");
    }}
    void saveMultimodal(String provider,String model)throws Exception{saveRoles(provider,model,provider,model,true);}
    private JSONObject multimodalRole(JSONObject value)throws Exception{JSONObject roles=value.getJSONObject("roles"),r=roles.optJSONObject("parcel_vision");if(r==null)r=roles.optJSONObject("vision");if(r==null)r=role(value);return r;}
    private JSONObject role(JSONObject value)throws Exception{JSONObject roles=value.getJSONObject("roles");JSONObject r=roles.optJSONObject("parcel");if(r==null)r=roles.optJSONObject("chat");return r==null?new JSONObject():r;}
    private JSONObject provider(JSONObject value,String id)throws Exception{JSONArray list=value.getJSONArray("providers");for(int i=0;i<list.length();i++)if(id.equals(list.getJSONObject(i).getString("id")))return list.getJSONObject(i);return new JSONObject();}
    JSONObject get()throws Exception{synchronized(LOCK){JSONObject value=exportSnapshot(),r=role(value),p=provider(value,r.optString("provider_id"));JSONObject vision=value.getJSONObject("roles").optJSONObject("parcel_vision");if(vision==null)vision=value.getJSONObject("roles").optJSONObject("vision");return new JSONObject().put("provider_id",p.optString("id","android-ai")).put("base_url",p.optString("base_url")).put("model",r.optString("model")).put("vision_model",vision==null?r.optString("model"):vision.optString("model")).put("has_key",!value.getJSONObject("secrets").optString(p.optString("id")).isEmpty()).put("enabled",enabled()).put("revision",revision());}}
    void save(String baseUrl,String model,String visionModel,String apiKey,boolean enabled)throws Exception{synchronized(LOCK){
        baseUrl=AiConfigCodec.endpoint(baseUrl.trim());model=model.trim();visionModel=visionModel.trim();if(model.isEmpty()||model.length()>200||visionModel.length()>200||apiKey.length()>8192)throw new IOException("请填写有效的模型 ID 和密钥");
        JSONObject value=exportSnapshot(),r=role(value),p=provider(value,r.optString("provider_id"));String id=p.optString("id","android-ai");
        if(apiKey.isEmpty()&&p.has("base_url")&&!baseUrl.equals(AiConfigCodec.endpoint(p.getString("base_url"))))throw new IOException("服务地址已更改，请重新填写 API Key");
        if(p.length()==0){p=new JSONObject().put("id",id).put("name","移动 AI");value.getJSONArray("providers").put(p);}
        if("gemini".equals(p.optString("kind")))throw new IOException("此页面支持 OpenAI 兼容接口，请使用兼容服务地址");
        p.put("base_url",baseUrl).put("kind",p.optString("kind","openai"));value.getJSONObject("roles").put("parcel",new JSONObject().put("provider_id",id).put("model",model));
        value.getJSONObject("roles").put("parcel_vision",new JSONObject().put("provider_id",id).put("model",visionModel.isEmpty()?model:visionModel));
        if(!apiKey.isEmpty())value.getJSONObject("secrets").put(id,apiKey.trim());AiConfigCodec.validate(value);store.save("ai-config","shared",value.toString(),"");
        if(!context.getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",enabled).commit())throw new IOException("无法保存取件 AI 开关");
    }}
    void importSnapshot(JSONObject value,String expectedRevision)throws Exception{synchronized(LOCK){AiConfigCodec.validate(value);if(!revision().equals(expectedRevision))throw new IOException("本机 AI 设置已变化，请重新下载确认");store.save("ai-config","shared",value.toString(),"");}}
    ParcelAi.Config parcelConfig(boolean vision)throws Exception{synchronized(LOCK){JSONObject value=exportSnapshot(),r=multimodalRole(value);JSONObject p=provider(value,r.optString("provider_id"));if("gemini".equals(p.optString("kind")))throw new IOException("取件助手需要 OpenAI 兼容的 AI 服务");String key=value.getJSONObject("secrets").optString(p.optString("id")),model=r.optString("model");if(key.isEmpty()||model.isEmpty()||!p.has("base_url"))throw new IOException("请先配置 AI 服务、模型和 API Key");return new ParcelAi.Config(p.getString("base_url"),key,model);}}
}
