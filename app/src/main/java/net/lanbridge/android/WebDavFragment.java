package net.lanbridge.android;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import android.view.Gravity;

public final class WebDavFragment extends ToolFragment {
    @Override protected android.view.View createContent(Bundle state){
        try{WebDavSettings.migrate(context());}catch(Exception e){Toast.makeText(context(),e.getMessage(),1).show();}
        LoginStore store=WebDavSettings.store(context());LinearLayout page=ToolUi.column(context());page.setPadding(ToolUi.dp(context(),24),ToolUi.dp(context(),16),ToolUi.dp(context(),24),ToolUi.dp(context(),32));
        LinearLayout bar=new LinearLayout(context());bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(ToolUi.iconButton(context(),"back","返回",this::closePage));bar.addView(ToolUi.text(context(),"WebDAV 连接",24,ToolUi.INK));page.addView(bar);
        TextView hint=ToolUi.text(context(),"所有工具共用此连接，远端目录在各工具中设置。",14,ToolUi.MUTED);hint.setPadding(0,ToolUi.dp(context(),16),0,0);page.addView(hint);
        EditText address=ToolUi.field(context(),page,"WebDAV 地址",store.origin(),false),user=ToolUi.field(context(),page,"用户名",store.username(),false),password=ToolUi.field(context(),page,"密码","",true);
        address.setId(201);user.setId(202);password.setHint(store.hasPassword()?"已加密保存，留空保留":"输入密码");
        TextView error=ToolUi.text(context(),"",14,0xffb33c3c);error.setPadding(0,ToolUi.dp(context(),12),0,ToolUi.dp(context(),12));page.addView(error);
        page.addView(ToolUi.button(context(),"保存连接",true,()->{try{
            String url=RelayDav.endpoint(address.getText().toString()).toString(),name=user.getText().toString().trim(),pass=password.getText().toString();
            if(pass.isEmpty())pass=store.password(url,name);
            if(name.isEmpty()||pass.isEmpty())throw new java.io.IOException("请填写用户名和密码；更换地址或账号需重新输入密码");
            store.save(url,name,pass,"");LedgerSync.get(context()).sync();password.setText("");Toast.makeText(context(),"WebDAV 连接已保存",0).show();closePage();
        }catch(Exception e){error.setText(e.getMessage());}}),new LinearLayout.LayoutParams(-1,ToolUi.dp(context(),50)));
        ScrollView scroll=new ScrollView(context());scroll.setFillViewport(true);scroll.addView(page);return scroll;
    }
}
