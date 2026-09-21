package net.lanbridge.android;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import okhttp3.*;

/** Application-scoped jobs survive page changes. No NAS cookies enter this client. */
public final class AppUpdater {
    public static final String REPOSITORY="https://github.com/thdlrt/AndroidToolbox";
    private static AppUpdater instance;
    public static synchronized AppUpdater get(Context c){if(instance==null)instance=new AppUpdater(c.getApplicationContext());return instance;}
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(10,TimeUnit.MINUTES).followSslRedirects(false).build();
    public volatile String message="尚未检查更新";
    public volatile boolean busy;
    private volatile ReleaseInfo release;
    private volatile boolean verified,waitingPermission;
    private AppUpdater(Context context){this.context=context;}
    public ReleaseInfo available(){return release;}
    private File apk(){return new File(context.getCacheDir(),"updates/update.apk");}
    public boolean ready(){return verified&&apk().isFile();}
    private synchronized boolean start(){if(busy)return false;busy=true;return true;}
    private Response request(String url)throws IOException{Response r=client.newCall(new Request.Builder().url(url).header("User-Agent","AndroidToolbox/"+BuildConfig.VERSION_NAME).build()).execute();if(!r.isSuccessful()){int code=r.code();r.close();throw new IOException("更新服务器返回 "+code);}return r;}
    private String small(String url,int max)throws IOException{try(Response r=request(url)){if(r.body()==null)throw new IOException("响应为空");try(InputStream in=r.body().byteStream()){return Streams.text(in,max);}}}
    public void check(){if(!start())return;message="正在检查 GitHub 正式版…";worker.execute(()->{try{release=ReleaseInfo.parse(small("https://api.github.com/repos/thdlrt/AndroidToolbox/releases/latest",1048576),BuildConfig.VERSION_NAME);message=release==null?"已是最新正式版 · "+BuildConfig.VERSION_NAME:"发现 "+release.version+"\n"+release.notes;}catch(Exception e){message="检查失败："+e.getMessage();}finally{busy=false;}});}
    public void download(Activity activity){ReleaseInfo selected=release;if(selected==null||!start())return;verified=false;message="正在下载 "+selected.version+"…";
        worker.execute(()->{File partial=new File(context.getCacheDir(),"updates/download.part");try{
            if(!partial.getParentFile().isDirectory()&&!partial.getParentFile().mkdirs())throw new IOException("无法创建更新目录");
            String expected=ReleaseInfo.checksum(small(selected.hashUrl,1024));MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;
            try(Response r=request(selected.apkUrl);InputStream in=r.body().byteStream();OutputStream out=new FileOutputStream(partial)){
                long size=r.body().contentLength();byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){total+=n;if(total>150L*1024*1024)throw new IOException("安装包超过大小限制");hash.update(block,0,n);out.write(block,0,n);message="正在下载 · "+(total/1024)+" KiB"+(size>0?" / "+(size/1024)+" KiB":"");}
            }
            StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!expected.equals(hex.toString()))throw new IOException("安装包 SHA-256 不匹配");
            validateApk(partial,selected.version);
            java.nio.file.Files.move(partial.toPath(),apk().toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);verified=true;message="下载和校验完成，准备安装";
            activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())install(activity);});
        }catch(Exception e){partial.delete();message="更新失败："+e.getMessage();}finally{busy=false;}});
    }
    private void validateApk(File file,String version)throws Exception{
        PackageManager pm=context.getPackageManager();PackageInfo next=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNING_CERTIFICATES),current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(next==null||!context.getPackageName().equals(next.packageName)||next.getLongVersionCode()<=current.getLongVersionCode()||!version.equals(next.versionName))throw new IOException("安装包身份或版本不正确");
        if(next.signingInfo==null||current.signingInfo==null)throw new IOException("安装包缺少签名");
        java.util.Set<Signature> a=new java.util.HashSet<>(java.util.Arrays.asList(next.signingInfo.getApkContentsSigners())),b=new java.util.HashSet<>(java.util.Arrays.asList(current.signingInfo.getApkContentsSigners()));
        if(!a.equals(b))throw new IOException("安装包签名与当前应用不一致");
    }
    public void install(Activity a){if(!ready())return;try{
        if(!context.getPackageManager().canRequestPackageInstalls()){waitingPermission=true;message="请允许此应用安装更新，返回后继续";a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())));return;}
        waitingPermission=false;Uri uri=Uri.parse("content://"+context.getPackageName()+".updates/update.apk");Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("APK",uri));a.startActivity(i);message="请在系统安装界面确认；取消后可重新点击安装更新";
    }catch(Exception e){message="无法打开安装器："+e.getMessage();}}
    public void resumeInstall(Activity a){if(waitingPermission&&context.getPackageManager().canRequestPackageInstalls())install(a);}
}
