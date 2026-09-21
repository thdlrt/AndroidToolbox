package net.lanbridge.android;
import org.json.*;
import java.net.URI;

/** Pure release parsing shared by the updater and unit tests. */
public final class ReleaseInfo {
    public final String version,apkUrl,hashUrl,notes;
    private ReleaseInfo(String version,String apkUrl,String hashUrl,String notes){this.version=version;this.apkUrl=apkUrl;this.hashUrl=hashUrl;this.notes=notes;}
    public static boolean newer(String candidate,String current){
        String[] a=normalized(candidate).split("\\."),b=normalized(current).split("\\.");
        for(int i=0;i<3;i++){int x=Integer.parseInt(a[i]),y=Integer.parseInt(b[i]);if(x!=y)return x>y;}return false;
    }
    private static String normalized(String v){String n=v.startsWith("v")?v.substring(1):v;if(!n.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}"))throw new IllegalArgumentException("无效的正式版本号");return n;}
    public static ReleaseInfo parse(String json,String current){
        try {
        JSONObject o=new JSONObject(json);if(o.optBoolean("draft")||o.optBoolean("prerelease"))return null;
        String v=normalized(o.getString("tag_name"));if(!newer(v,current))return null;
        String file="AndroidToolbox-"+v+".apk",apk=null,hash=null;JSONArray assets=o.getJSONArray("assets");
        for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String n=a.getString("name");if(n.equals(file)){apk=a.getString("browser_download_url");validateAsset(apk,v,n);}if(n.equals(file+".sha256")){hash=a.getString("browser_download_url");validateAsset(hash,v,n);}}
        if(apk==null||hash==null)throw new IllegalArgumentException("发行版缺少 APK 或校验文件");
        return new ReleaseInfo(v,apk,hash,o.optString("body",""));
        } catch(JSONException e) { throw new IllegalArgumentException("发行版数据格式无效",e); }
    }
    private static void validateAsset(String url,String version,String name){
        URI u=URI.create(url);String expected="/thdlrt/AndroidToolbox/releases/download/v"+version+"/"+name;
        if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||!expected.equals(u.getPath())||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPort()!=-1)throw new IllegalArgumentException("更新文件来源不正确");
    }
    public static String checksum(String value){String h=value.trim().split("\\s+")[0];if(!h.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("SHA-256 校验文件格式无效");return h.toLowerCase(java.util.Locale.ROOT);}
}
