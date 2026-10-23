package net.lanbridge.android;

import java.io.IOException;
import okhttp3.HttpUrl;

/** Accept the upstream WebUI QR URL while keeping its token out of navigation. */
final class CodexWebConnection {
    final String origin, token;
    private CodexWebConnection(String origin,String token){this.origin=origin;this.token=token;}
    static CodexWebConnection parse(String address,String explicitToken)throws IOException{
        String value=address==null?"":address.trim();
        if(value.isEmpty())throw new IOException("请填写 Codex WebUI 地址，例如 192.168.1.10:9526");
        if(!value.contains("://"))value="http://"+value;
        try{
            HttpUrl url=HttpUrl.get(value);
            if(!url.username().isEmpty()||!url.password().isEmpty()||!url.encodedPath().equals("/"))throw new IOException("请填写服务地址，不要包含账号或会话路径");
            for(String key:url.queryParameterNames())if(!key.equals("token"))throw new IOException("地址只支持 token 参数");
            String fragmentToken="";
            if(url.fragment()!=null){
                HttpUrl fragment=HttpUrl.get("https://local.invalid/?"+url.encodedFragment());
                if(fragment.querySize()!=1||!fragment.queryParameterName(0).equals("token"))throw new IOException("页面片段只支持 token 参数");
                fragmentToken=fragment.queryParameter("token");
            }
            String token=explicitToken==null?"":explicitToken.trim();
            if(token.isEmpty())token=url.queryParameter("token");
            if(token==null||token.isEmpty())token=fragmentToken;
            String origin=url.newBuilder().query(null).fragment(null).build().toString().replaceAll("/+$","");
            return new CodexWebConnection(origin,token==null?"":token.trim());
        }catch(IllegalArgumentException e){throw new IOException("只支持有效的 http 或 https 地址");}
    }
}
