package net.lanbridge.android;

import java.io.IOException;
import okhttp3.HttpUrl;

/** Parse a pasted HAPI URL without persisting credentials in URLs or browser history. */
final class HapiConnection {
    final String origin, token;
    private HapiConnection(String origin, String token) { this.origin=origin; this.token=token; }
    static HapiConnection parse(String address, String token) throws IOException {
        String value=address==null?"":address.trim();
        if(value.isEmpty())throw new IOException("请填写电脑地址，例如 192.168.1.10:3006");
        if(!value.contains("://"))value="http://"+value;
        HttpUrl url;
        try { url=HttpUrl.get(value); } catch(IllegalArgumentException e){throw new IOException("只支持有效的 http 或 https 地址");}
        if(!url.username().isEmpty()||!url.password().isEmpty())throw new IOException("地址不能包含账号或密码");
        if(!url.encodedPath().equals("/"))throw new IOException("只填写电脑地址和端口，不要带会话路径");
        for(String key:url.queryParameterNames())if(!key.equals("token"))throw new IOException("地址只支持 HAPI 的 token 参数");
        if(url.fragment()!=null)throw new IOException("请填写 HAPI 服务地址，不要带页面片段");
        String secret=token==null?"":token.trim();
        if(secret.isEmpty()&&url.queryParameter("token")!=null)secret=url.queryParameter("token").trim();
        String origin=url.newBuilder().query(null).fragment(null).build().toString().replaceAll("/+$","");
        return new HapiConnection(origin,secret);
    }
    static boolean sameOrigin(String expected,String candidate){
        try {HttpUrl a=HttpUrl.get(expected),b=HttpUrl.get(candidate);return a.scheme().equals(b.scheme())&&a.host().equals(b.host())&&a.port()==b.port();}
        catch(Exception e){return false;}
    }
    static String desktopOrigin(String origin){
        HttpUrl url=HttpUrl.get(origin);
        if(url.port()==65535)throw new IllegalArgumentException("实时会话需要另一个端口，请更换电脑服务端口");
        return url.newBuilder().port(url.port()+1).build().toString().replaceAll("/+$","");
    }
}
