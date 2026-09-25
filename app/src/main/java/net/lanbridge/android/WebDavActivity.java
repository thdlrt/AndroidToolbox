package net.lanbridge.android;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import android.view.Gravity;

public final class WebDavActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        try{WebDavSettings.migrate(this);}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}
        LoginStore store=WebDavSettings.store(this);LinearLayout page=ToolUi.column(this);page.setPadding(ToolUi.dp(this,24),ToolUi.dp(this,16),ToolUi.dp(this,24),ToolUi.dp(this,32));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(ToolUi.iconButton(this,"back","返回",this::finish));bar.addView(ToolUi.text(this,"WebDAV 连接",24,ToolUi.INK));page.addView(bar);
        TextView hint=ToolUi.text(this,"所有工具共用此连接，远端目录在各工具中设置。",14,ToolUi.MUTED);hint.setPadding(0,ToolUi.dp(this,16),0,0);page.addView(hint);
        EditText address=ToolUi.field(this,page,"WebDAV 地址",store.origin(),false),user=ToolUi.field(this,page,"用户名",store.username(),false),password=ToolUi.field(this,page,"密码","",true);
        address.setId(201);user.setId(202);password.setHint(store.hasPassword()?"已加密保存，留空保留":"输入密码");
        TextView error=ToolUi.text(this,"",14,0xffb33c3c);error.setPadding(0,ToolUi.dp(this,12),0,ToolUi.dp(this,12));page.addView(error);
        page.addView(ToolUi.button(this,"保存连接",true,()->{try{
            String url=RelayDav.endpoint(address.getText().toString()).toString(),name=user.getText().toString().trim(),pass=password.getText().toString();
            if(pass.isEmpty())pass=store.password(url,name);
            if(name.isEmpty()||pass.isEmpty())throw new java.io.IOException("请填写用户名和密码；更换地址或账号需重新输入密码");
            store.save(url,name,pass,"");Toast.makeText(this,"WebDAV 连接已保存",0).show();finish();
        }catch(Exception e){error.setText(e.getMessage());}}),new LinearLayout.LayoutParams(-1,ToolUi.dp(this,50)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(new ToolUi.Shell(this,scroll,"settings"));
    }
}
