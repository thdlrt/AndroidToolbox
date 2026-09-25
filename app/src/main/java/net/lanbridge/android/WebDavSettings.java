package net.lanbridge.android;

import android.content.Context;
import android.content.SharedPreferences;

/** One encrypted connection shared by tools; each tool owns its remote path. */
final class WebDavSettings {
    static LoginStore store(Context c){return new LoginStore(c,"webdav-profile","webdav-login-v1");}
    static void migrate(Context c)throws Exception{
        SharedPreferences p=c.getSharedPreferences("relay",0);
        if(p.getBoolean("shared_migrated",false))return;
        LoginStore old=new LoginStore(c,"relay-profile","relay-login-v1"),shared=store(c);
        if(old.hasPassword()&&!shared.hasPassword()){
            String password=old.password(old.origin(),old.username());
            if(!password.isEmpty())shared.save(old.origin(),old.username(),password,"");
        }
        SharedPreferences.Editor edit=p.edit().putBoolean("shared_migrated",true);
        if(!p.contains("remote_path"))edit.putString("remote_path",old.scope().equals("lan")?"文件中转站":old.scope());
        if(!edit.commit())throw new java.io.IOException("无法迁移中转设置");
    }
    static String path(Context c){return c.getSharedPreferences("relay",0).getString("remote_path","文件中转站");}
    static String identity(Context c){LoginStore s=store(c);return s.origin()+"\n"+s.username()+"\n"+path(c)+"\n"+s.revision();}
}
