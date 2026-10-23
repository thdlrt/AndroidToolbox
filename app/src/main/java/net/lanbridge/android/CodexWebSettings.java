package net.lanbridge.android;

import android.content.Context;
import java.io.IOException;

/** The shared Desktop bridge has independent credentials from legacy HAPI. */
final class CodexWebSettings {
    private static final String USER="codex-webui";
    static LoginStore store(Context c){return new LoginStore(c,"codex-webui-profile","codex-webui-login-v1");}
    static String origin(Context c){return store(c).origin();}
    static String token(Context c){try{LoginStore s=store(c);return s.password(s.origin(),USER);}catch(Exception e){return "";}}
    static boolean configured(Context c){return !origin(c).isEmpty()&&!token(c).isEmpty();}
    static void save(Context c,String address,String token)throws Exception{
        CodexWebConnection value=CodexWebConnection.parse(address,token);
        String secret=value.token;
        if(secret.isEmpty()&&value.origin.equals(origin(c)))secret=token(c);
        if(secret.isEmpty())throw new IOException("请填写 Codex WebUI 的访问令牌，或粘贴完整连接链接");
        store(c).save(value.origin,USER,secret,"");
    }
    static void clear(Context c){LoginStore s=store(c);s.forget();s.config("",USER,"");}
}
