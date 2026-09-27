package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Home, catalog and settings content inside the shared toolbox window. */
public final class HomeFragment extends ToolFragment {
    private LinearLayout content;

    private String page="home";
    private TextView vpnState, updateState;
    private Button updateButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(vpnState!=null) ToolUi.update(vpnState,"connected".equals(BridgeVpnService.phase)?"已连接 · "+BridgeVpnService.message:"connecting".equals(BridgeVpnService.phase)?"正在连接":"未连接");
        if(updateState!=null) ToolUi.update(updateState,AppUpdater.get(getActivity()).message);
        if(updateButton!=null) { AppUpdater u=AppUpdater.get(getActivity());updateButton.setEnabled(!u.busy);ToolUi.update(updateButton,u.ready()?"安装更新":u.available()!=null?"下载并更新":"检查更新"); }
        handler.postDelayed(this,600);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(context());t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(32,46,68));t.setPadding(0,dp(6),0,dp(6));return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(context());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);content.addView(c,lp);return c;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=ToolUi.button(context(),label,false,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(12);parent.addView(b,lp);return b;}
    @Override protected android.view.View createContent(Bundle state){
        page=state!=null?state.getString("page","home"):getArguments().getString("page");if(page==null)page="home";
        LinearLayout shell=column();shell.setBackgroundColor(Color.rgb(243,246,252));
        ScrollView scroll=new ScrollView(context());content=column();content.setPadding(dp(20),dp(20),dp(20),dp(16));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        show(page);return shell;
    }
    void show(String id){
        page=id;content.removeAllViews();vpnState=null;updateState=null;updateButton=null;

        TextView title=text(id.equals("settings")?"设置":id.equals("tools")?"全部工具":"工具首页",26);title.setTypeface(null,Typeface.BOLD);content.addView(title);
        if(id.equals("settings")){settings();return;}
        TextView subtitle=text(id.equals("tools")?"选择常用工具，打造自己的工具箱":"日常所需，随手可用",14);subtitle.setTextColor(ToolUi.MUTED);content.addView(subtitle);
        ToolUi.Grid grid=new ToolUi.Grid(context(),156,3,12);ToolUi.space(content,grid);
        for(ToolRegistry.Tool tool:ToolRegistry.TOOLS){
            boolean favorite=getPreferences(0).getBoolean("favorite."+tool.id,true);
            if(id.equals("home")&&!favorite)continue;
            LinearLayout c=ToolUi.card(context());c.setMinimumHeight(dp(144));c.setPadding(dp(16),dp(16),dp(16),dp(16));int color=ToolUi.toolColor(tool.id);
            ImageView icon=ToolUi.icon(context(),ToolUi.toolIcon(tool.id),color);icon.setPadding(dp(10),dp(10),dp(10),dp(10));icon.setBackground(ToolUi.box(context(),(color&0x00ffffff)|0x14000000,15));c.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView name=text(tool.name,17);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,dp(10),0,dp(6));c.addView(name);
            String description;switch(tool.id){case "vpn":description="安全连接家中网络";break;case "relay":description="跨设备传递文件";break;case "ledger":description="项目收支 · 拍照记账";break;case "diagnostics":description="连接检测与问题排查";break;default:description="智能整理 · 轻松取件";}
            TextView detail=text(description,12);detail.setTextColor(ToolUi.MUTED);detail.setPadding(0,0,0,0);c.addView(detail);
            if(tool.id.equals("vpn")){vpnState=text("未连接",12);vpnState.setTextColor(color);vpnState.setPadding(0,dp(6),0,0);c.addView(vpnState);}
            ToolUi.ripple(c,Color.WHITE,20);c.setFocusable(true);c.setContentDescription("打开"+tool.name);c.setOnClickListener(v->startActivity(new Intent(context(),tool.activity)));
            if(id.equals("tools")){CheckBox pin=new CheckBox(context());pin.setText("常用工具");pin.setTextSize(12);pin.setMinHeight(dp(48));pin.setChecked(favorite);c.addView(pin);pin.setOnCheckedChangeListener((v,on)->{synchronized(ConfigBackupService.CONFIG_LOCK){getPreferences(0).edit().putBoolean("favorite."+tool.id,on).commit();}});}
            grid.addView(c);
        }
        if(grid.getChildCount()==0){LinearLayout empty=card();empty.addView(text("暂无常用工具",18));button(empty,"选择常用工具",()->navigate("tools"));}
    }
    private void settingRow(String icon,String title,String subtitle,Runnable action){
        LinearLayout row=new LinearLayout(context());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(18),dp(12),dp(18));ToolUi.ripple(row,Color.WHITE,18);
        ImageView image=ToolUi.icon(context(),icon,ToolUi.BLUE);image.setPadding(dp(9),dp(9),dp(9),dp(9));image.setBackground(ToolUi.box(context(),0xffedf2ff,14));row.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=column();words.setPadding(dp(14),0,dp(8),0);TextView heading=text(title,17);heading.setTypeface(null,Typeface.BOLD);words.addView(heading);TextView detail=text(subtitle,13);detail.setTextColor(ToolUi.MUTED);words.addView(detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=ToolUi.icon(context(),"back",ToolUi.MUTED);arrow.setRotation(180);row.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(18)));row.setFocusable(true);row.setOnClickListener(v->action.run());ToolUi.space(content,row);
    }

    private void settings(){
        try{WebDavSettings.migrate(context());}catch(Exception e){Toast.makeText(context(),e.getMessage(),1).show();}
        LoginStore shared=WebDavSettings.store(context());
        settingRow("cloud","WebDAV 与备份",shared.hasPassword()?"已配置 · 统一连接与同步":"统一连接、自动同步和配置备份",()->startActivity(new Intent(context(),WebDavActivity.class)));
        settingRow("ai","AI 设置","供应商、功能模型与密钥同步",()->startActivity(new Intent(context(),AiSettingsActivity.class)));
        TextView about=text("关于应用",14);about.setTextColor(ToolUi.MUTED);about.setPadding(0,dp(24),0,dp(12));content.addView(about);
        LinearLayout c=card();c.addView(text("应用更新",20));c.addView(text("安卓工具箱 "+BuildConfig.VERSION_NAME,15));
        updateState=text(AppUpdater.get(context()).message,14);c.addView(updateState);
        updateButton=button(c,"检查更新",()->{AppUpdater u=AppUpdater.get(context());if(u.ready()) u.install(context());else if(u.available()!=null) u.download(context());else u.check();});
        c.addView(text("从 GitHub Releases 获取正式版。下载后校验 SHA-256、包名和签名，再由系统确认安装。",13));
        LinearLayout links=card();button(links,"项目源码与发行版",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AppUpdater.REPOSITORY))));
        button(links,"第三方许可",()->{
            try{String[] names=getAssets().list("licenses");new AlertDialog.Builder(context()).setTitle("第三方许可").setItems(names,(dialog,index)->{
                try(java.io.InputStream in=getAssets().open("licenses/"+names[index])){String body=Streams.text(in,1048576);new AlertDialog.Builder(context()).setTitle(names[index]).setMessage(body).setPositiveButton("关闭",null).show();}catch(Exception e){Toast.makeText(context(),e.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("关闭",null).show();}catch(Exception e){Toast.makeText(context(),e.getMessage(),Toast.LENGTH_LONG).show();}
        });
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);}
    @Override protected void onPageResume(){show(page);handler.post(refresh);}
    @Override protected void onPagePause(){handler.removeCallbacks(refresh);}
}
