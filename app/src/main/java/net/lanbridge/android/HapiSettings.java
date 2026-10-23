package net.lanbridge.android;

import android.content.Context;
import java.io.IOException;

final class HapiSettings {
    private static final String USER="hapi";
    static LoginStore store(Context c){return new LoginStore(c,"hapi-profile","hapi-login-v1");}
    static String origin(Context c){return store(c).origin();}
    static String token(Context c){try{LoginStore s=store(c);return s.password(s.origin(),USER);}catch(Exception e){return "";}}
    static boolean configured(Context c){return !origin(c).isEmpty()&&store(c).hasPassword();}
    static void save(Context c,String address,String token)throws Exception{
        HapiConnection v=HapiConnection.parse(address,token);
        // A blank field keeps a token only for the exact same server.
        String secret=v.token;
        if(secret.isEmpty()&&v.origin.equals(origin(c)))secret=token(c);
        if(secret.isEmpty())throw new IOException("请填写电脑 HAPI 显示的访问令牌");
        store(c).save(v.origin,USER,secret,"");
    }
    static void clear(Context c){LoginStore s=store(c);s.forget();s.config("",USER,"");}
}
