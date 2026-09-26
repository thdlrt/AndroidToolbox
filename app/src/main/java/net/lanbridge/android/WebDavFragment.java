package net.lanbridge.android;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.Gravity;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Sole WebDAV editor and manual configuration backup/restore surface. */
public final class WebDavFragment extends ToolFragment {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private TextView error,syncStatus,migration;private LinearLayout backups;private volatile boolean busy;private String listedIdentity="";
    private final Handler handler=new Handler(Looper.getMainLooper());private final Runnable refresh=new Runnable(){public void run(){if(syncStatus!=null){syncStatus.setText(LedgerSync.get(context()).status);migration.setText(WebDavSettings.migrationStatus);}handler.postDelayed(this,700);}};
    @Override protected android.view.View createContent(Bundle state){
        try{WebDavSettings.migrate(context());}catch(Exception e){Toast.makeText(context(),e.getMessage(),1).show();}
        LoginStore store=WebDavSettings.store(context());LinearLayout page=ToolUi.column(context());page.setPadding(ToolUi.dp(context(),24),ToolUi.dp(context(),16),ToolUi.dp(context(),24),ToolUi.dp(context(),32));
        LinearLayout bar=new LinearLayout(context());bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(ToolUi.iconButton(context(),"back","返回",this::closePage));bar.addView(ToolUi.text(context(),"WebDAV 与备份",24,ToolUi.INK));page.addView(bar);
        page.addView(ToolUi.text(context(),"所有工具共用一个连接和根目录。记账与诊断配置自动合并同步；文件中转和配置备份自动使用各自目录。",14,ToolUi.MUTED));
        EditText address=ToolUi.field(context(),page,"WebDAV 地址",store.origin(),false),user=ToolUi.field(context(),page,"用户名",store.username(),false),password=ToolUi.field(context(),page,"密码","",true),root=ToolUi.field(context(),page,"统一根目录",WebDavSettings.root(context()),false);
        address.setId(201);user.setId(202);root.setId(203);password.setHint(store.hasPassword()?"已加密保存，留空保留":"输入密码");
        error=ToolUi.text(context(),"",14,0xffb33c3c);page.addView(error);
        page.addView(ToolUi.button(context(),"保存连接",true,()->{try{
            String url=RelayDav.endpoint(address.getText().toString()).toString(),name=user.getText().toString().trim(),pass=password.getText().toString(),dir=root.getText().toString().trim();RelayDav.path(dir,false);
            if(pass.isEmpty())pass=store.password(url,name);if(name.isEmpty()||pass.isEmpty())throw new java.io.IOException("请填写用户名和密码；更换地址或账号需重新输入密码");
            WebDavSettings.save(context(),url,name,pass,dir);password.setText("");error.setText("连接已保存，正在自动创建目录并同步");LedgerSync.get(context()).sync();
        }catch(Exception e){error.setText(e.getMessage());}}));
        page.addView(ToolUi.text(context(),"自动同步",20,ToolUi.INK));syncStatus=ToolUi.text(context(),LedgerSync.get(context()).status,14,ToolUi.MUTED);page.addView(syncStatus);migration=ToolUi.text(context(),WebDavSettings.migrationStatus,13,ToolUi.MUTED);page.addView(migration);page.addView(ToolUi.button(context(),"立即同步 / 重试迁移",false,()->LedgerSync.get(context()).sync()));
        page.addView(ToolUi.text(context(),"配置备份",20,ToolUi.INK));page.addView(ToolUi.text(context(),"手动备份首页设置、NAS 地址、用户名和 VPN 模式。密码保留在本机；备份不包含记账、附件或中转文件。恢复只覆盖这些配置，当前 WebDAV 连接与根目录保持不变。",14,ToolUi.MUTED));
        page.addView(ToolUi.button(context(),"备份当前配置",false,()->work(()->{String name=ConfigBackupService.upload(context());ui(()->error.setText("已备份："+name+"，点击查看配置备份可恢复"));})));
        page.addView(ToolUi.button(context(),"查看配置备份",false,this::loadBackups));backups=ToolUi.column(context());page.addView(backups);
        ScrollView scroll=new ScrollView(context());scroll.setFillViewport(true);scroll.addView(page);return scroll;
    }
    private interface Work {void run()throws Exception;}
    private void ui(Runnable action){runOnUiThread(()->{if(isAdded()&&getView()!=null)action.run();});}
    private void work(Work work){if(busy){error.setText("正在处理，请稍候");return;}busy=true;error.setText("正在处理…");worker.execute(()->{try{work.run();}catch(Exception e){ui(()->error.setText(e.getMessage()));}finally{busy=false;}});}
    private void loadBackups(){work(()->{String identity=WebDavSettings.identity(context());List<RelayDav.Entry> list=ConfigBackupService.list(context());ui(()->{listedIdentity=identity;backups.removeAllViews();error.setText("找到 "+list.size()+" 份 Android 配置备份");for(RelayDav.Entry entry:list)backups.addView(ToolUi.button(context(),entry.name,false,()->preview(entry)));if(list.isEmpty())backups.addView(ToolUi.text(context(),"暂无配置备份",14,ToolUi.MUTED));});});}
    private void preview(RelayDav.Entry entry){work(()->{if(!listedIdentity.equals(WebDavSettings.identity(context())))throw new java.io.IOException("连接已更改，请重新查看备份列表");JSONObject value=ConfigBackupService.download(context(),entry);JSONObject nas=value.getJSONObject("preferences").getJSONObject("nas");String summary="将覆盖首页设置、NAS 地址/用户名及 VPN 模式。NAS 账号变化后需重新输入密码。\n\nNAS："+nas.getString("origin")+"\n用户名："+nas.getString("username")+"\n\n记账、附件、中转文件和当前 WebDAV 连接均保留；恢复前会保存本机配置恢复副本。";ui(()->new AlertDialog.Builder(context()).setTitle("确认覆盖本机配置？").setMessage(summary).setNegativeButton("取消",null).setPositiveButton("恢复配置",(dialog,which)->work(()->{ConfigBackupService.restore(context(),value);ui(()->error.setText("配置已恢复；NAS 账号变化时请重新填写密码"));})).show());});}
    @Override protected void onPageResume(){handler.post(refresh);}
    @Override protected void onPagePause(){handler.removeCallbacks(refresh);}
}
