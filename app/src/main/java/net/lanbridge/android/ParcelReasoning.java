package net.lanbridge.android;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Device-local settings; request parameters follow the selected provider's protocol. */
final class ParcelReasoning {
    static final String[] VALUES={"default","off","low","medium","high"};
    static final String[] LABELS={"模型默认","关闭思考","低","中","高"};
    static String validate(String value){if(!Arrays.asList(VALUES).contains(value))throw new IllegalArgumentException("思考强度设置无效");return value;}
    static String load(Context context){return validate(context.getSharedPreferences("parcel-reasoning",0).getString("effort","default"));}
    static void save(Context context,String value){validate(value);if(!context.getSharedPreferences("parcel-reasoning",0).edit().putString("effort",value).commit())throw new IllegalStateException("无法保存思考强度");}
    static String protocol(ParcelAi.Config config){String model=config.model.toLowerCase(Locale.ROOT);String host=okhttp3.HttpUrl.get(config.endpoint).host();boolean bailian="dashscope".equals(config.providerKind)||host.equals("dashscope.aliyuncs.com")||host.endsWith(".maas.aliyuncs.com");if(model.startsWith("deepseek"))return bailian?"bailian-deepseek":"deepseek";if(bailian||model.startsWith("qwen"))return "qwen";if(host.equals("api.deepseek.com"))return "deepseek";return "standard";}
    static void apply(JSONObject payload,ParcelAi.Config config)throws JSONException{
        String effort=validate(config.reasoning);if(effort.equals("default"))return;boolean off=effort.equals("off");String protocol=protocol(config);
        if(protocol.equals("qwen")){
            payload.put("enable_thinking",!off);
            if(!off){String model=config.model.toLowerCase(Locale.ROOT);if(model.startsWith("qwen3.8-omni"))payload.put("reasoning_effort",effort.equals("high")?"xhigh":effort);else payload.put("thinking_budget",effort.equals("low")?4096:effort.equals("medium")?16384:32768);}
        }else if(protocol.equals("deepseek")||protocol.equals("bailian-deepseek")){
            if(protocol.equals("bailian-deepseek"))payload.put("enable_thinking",!off);else payload.put("thinking",new JSONObject().put("type",off?"disabled":"enabled"));if(!off)payload.put("reasoning_effort",effort.equals("low")?"low":effort.equals("medium")?"high":"max");
            payload.put("max_tokens",off?ParcelAi.MAX_OUTPUT_TOKENS:32768);if(!off)payload.remove("temperature");
        }else{
            payload.put("reasoning_effort",off?"none":effort);payload.remove("temperature");payload.remove("max_tokens");payload.put("max_completion_tokens",off?ParcelAi.MAX_OUTPUT_TOKENS:32768);
        }
    }
}
