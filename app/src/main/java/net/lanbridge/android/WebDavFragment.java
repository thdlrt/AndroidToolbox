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
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private TextView error;private LinearLayout backups;private volatile boolean busy;private String listedIdentity="";
    @Override protected android.view.View createContent(Bundle state){
        try{WebDavSettings.migrate(context());}catch(Exception e){Toast.makeText(context(),e.getMessage(),1).show();}
        LoginStore store=WebDavSettings.store(context());LinearLayout page=ToolUi.column(context());page.setPadding(ToolUi.dp(context(),24),ToolUi.dp(context(),16),ToolUi.dp(context(),24),ToolUi.dp(context(),32));
        page.addView(ToolUi.header(context(),"WebDAV 与备份",this::closePage));
        LinearLayout rootPage=page;ToolUi.Grid sections=new ToolUi.Grid(context(),300,2,16);rootPage.addView(sections);page=ToolUi.card(context());sections.addView(page);
        page.addView(ToolUi.text(context(),"所有工具共用一个连接和根目录。记账与诊断配置自动合并同步；文件中转和配置备份自动使用各自目录。",14,ToolUi.MUTED));
        EditText address=ToolUi.field(context(),page,"WebDAV 地址",store.origin(),false),user=ToolUi.field(context(),page,"用户名",store.username(),false),password=ToolUi.field(context(),page,"密码","",true),root=ToolUi.field(context(),page,"统一根目录",WebDavSettings.root(context()),false);
        address.setId(201);user.setId(202);root.setId(203);password.setHint(store.hasPassword()?"已加密保存，留空保留":"输入密码");
        error=ToolUi.text(context(),"",14,0xffb33c3c);page.addView(error);
        page.addView(ToolUi.button(context(),"保存连接",true,()->{try{
            String url=RelayDav.endpoint(address.getText().toString()).toString(),name=user.getText().toString().trim(),pass=password.getText().toString(),dir=root.getText().toString().trim();RelayDav.path(dir,false);
            if(pass.isEmpty())pass=store.password(url,name);if(name.isEmpty()||pass.isEmpty())throw new java.io.IOException("请填写用户名和密码；更换地址或账号需重新输入密码");
            WebDavSettings.save(context(),url,name,pass,dir);password.setText("");error.setText("连接已保存，正在自动创建目录并同步");LedgerSync.get(context()).sync();
        }catch(Exception e){error.setText(e.getMessage());}}));
        page=ToolUi.card(context());sections.addView(page);
        page.addView(ToolUi.text(context(),"配置备份",20,ToolUi.INK));page.addView(ToolUi.text(context(),"统一备份本机设置及 AI 服务、模型和密钥，使用当前 WebDAV 密码加密。支持查看电脑与手机备份；异平台仅恢复共享 AI 配置。业务数据由全局同步图标管理。",14,ToolUi.MUTED));
        page.addView(ToolUi.button(context(),"备份当前配置",false,()->work(()->{String name=ConfigBackupService.upload(context());ui(()->error.setText("已备份："+name+"，点击查看配置备份可恢复"));})));
        page.addView(ToolUi.button(context(),"查看配置备份",false,this::loadBackups));backups=ToolUi.column(context());page.addView(backups);
        ScrollView scroll=new ScrollView(context());scroll.setFillViewport(true);scroll.addView(rootPage);return scroll;
    }
    private interface Work {void run()throws Exception;}
    private void ui(Runnable action){runOnUiThread(()->{if(isAdded()&&getView()!=null)action.run();});}
    private void work(Work work){if(busy){error.setText("正在处理，请稍候");return;}busy=true;error.setText("正在处理…");worker.execute(()->{try{work.run();}catch(Exception e){ui(()->error.setText(e.getMessage()));}finally{busy=false;}});}
    private void loadBackups(){work(()->{String identity=WebDavSettings.identity(context());List<RelayDav.Entry> list=ConfigBackupService.list(context());ui(()->{listedIdentity=identity;backups.removeAllViews();error.setText("找到 "+list.size()+" 份配置备份");for(RelayDav.Entry entry:list)backups.addView(ToolUi.button(context(),ConfigBackupService.label(entry),false,()->preview(entry)));if(list.isEmpty())backups.addView(ToolUi.text(context(),"暂无配置备份",14,ToolUi.MUTED));});});}
    private void preview(RelayDav.Entry entry){work(()->{if(!listedIdentity.equals(WebDavSettings.identity(context())))throw new java.io.IOException("连接已更改，请重新查看备份列表");ConfigBackupService.Preview prepared=ConfigBackupService.preview(context(),entry);ui(()->new AlertDialog.Builder(context()).setTitle("恢复配置 · "+prepared.platform).setMessage(prepared.summary).setNegativeButton("取消",null).setPositiveButton("确认恢复",(dialog,which)->work(()->{ConfigBackupService.restore(context(),prepared);ui(()->error.setText("配置已恢复"));})).show());});}
}
