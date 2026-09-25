package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Toolbox shell. Feature activities own their UI; services own background work. */
public final class MainActivity extends Activity {
    private LinearLayout content, navigation;
    private ToolUi.Shell adaptive;
    private String page="home";
    private TextView vpnState, updateState;
    private Button updateButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(vpnState!=null) ToolUi.update(vpnState,"connected".equals(BridgeVpnService.phase)?"已连接 · "+BridgeVpnService.message:"connecting".equals(BridgeVpnService.phase)?"正在连接":"未连接");
        if(updateState!=null) ToolUi.update(updateState,AppUpdater.get(MainActivity.this).message);
        if(updateButton!=null) { AppUpdater u=AppUpdater.get(MainActivity.this);updateButton.setEnabled(!u.busy);ToolUi.update(updateButton,u.ready()?"安装更新":u.available()!=null?"下载并更新":"检查更新"); }
        handler.postDelayed(this,600);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(32,46,68));t.setPadding(0,dp(6),0,dp(6));return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);content.addView(c,lp);return c;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=ToolUi.button(this,label,false,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(12);parent.addView(b,lp);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);page=state!=null?state.getString("page","home"):getIntent().getStringExtra("page");if(page==null)page="home";
        LinearLayout shell=column();shell.setBackgroundColor(Color.rgb(243,246,252));
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(dp(20),dp(20),dp(20),dp(16));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=new LinearLayout(this);navigation.setBackgroundColor(Color.WHITE);shell.addView(navigation);adaptive=new ToolUi.Shell(this,shell,page);adaptive.bottom(navigation);setContentView(adaptive);show(page);
    }
    void show(String id){
        page=id;content.removeAllViews();navigation.removeAllViews();vpnState=null;updateState=null;updateButton=null;
        adaptive.selected(id);
        String[][] items={{"home","工具首页"},{"tools","全部工具"},{"settings","设置"}};
        for(String[] item:items){LinearLayout b=column();b.setGravity(Gravity.CENTER);boolean on=id.equals(item[0]);ToolUi.ripple(b,on?0xffedf2ff:Color.WHITE,16);b.addView(ToolUi.icon(this,item[0].equals("tools")?"grid":item[0],on?ToolUi.BLUE:ToolUi.MUTED),new LinearLayout.LayoutParams(dp(23),dp(23)));TextView label=ToolUi.text(this,item[1],12,on?ToolUi.BLUE:ToolUi.MUTED);label.setPadding(0,dp(5),0,0);label.setGravity(Gravity.CENTER);b.addView(label);navigation.addView(b,new LinearLayout.LayoutParams(0,dp(68),1));b.setContentDescription(item[1]);b.setFocusable(true);b.setOnClickListener(v->show(item[0]));}
        TextView title=text(id.equals("settings")?"设置":id.equals("tools")?"全部工具":"工具首页",26);title.setTypeface(null,Typeface.BOLD);content.addView(title);
        if(id.equals("settings")){settings();return;}
        for(ToolRegistry.Tool tool:ToolRegistry.TOOLS){
            boolean favorite=getPreferences(0).getBoolean("favorite."+tool.id,true);
            if(id.equals("home")&&!favorite)continue;
            LinearLayout c=card();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=ToolUi.icon(this,tool.id.equals("vpn")?"shield":"folder",ToolUi.BLUE);icon.setPadding(dp(12),dp(12),dp(12),dp(12));icon.setBackground(ToolUi.box(this,0xffedf2ff,16));heading.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));TextView name=text(tool.name,21);name.setPadding(dp(14),dp(6),0,dp(6));name.setTypeface(null,Typeface.BOLD);heading.addView(name);c.addView(heading);TextView description=text(tool.description,14);description.setTextColor(ToolUi.MUTED);description.setPadding(0,dp(16),0,dp(8));c.addView(description);if(tool.id.equals("vpn")){vpnState=text("未连接",14);c.addView(vpnState);}
            ToolUi.ripple(c,Color.WHITE,18);c.setFocusable(true);c.setContentDescription("打开"+tool.name);c.setOnClickListener(v->startActivity(new Intent(this,tool.activity)));
            if(id.equals("tools")){CheckBox pin=new CheckBox(this);pin.setText("显示在工具首页");pin.setChecked(favorite);c.addView(pin);pin.setOnCheckedChangeListener((v,on)->getPreferences(0).edit().putBoolean("favorite."+tool.id,on).apply());}
        }
        if(content.getChildCount()==1){LinearLayout empty=card();empty.addView(text("暂无常用工具",18));button(empty,"选择常用工具",()->show("tools"));}
    }
    private void settings(){
        LinearLayout connection=card();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(ToolUi.icon(this,"cloud",ToolUi.BLUE),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView label=text("WebDAV 连接",20);label.setPadding(dp(12),dp(6),0,dp(6));heading.addView(label);connection.addView(heading);
        try{WebDavSettings.migrate(this);}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}
        LoginStore shared=WebDavSettings.store(this);TextView summary=text(shared.hasPassword()?"已配置 · 文件中转站共用":"配置一次，供文件中转站使用",14);summary.setTextColor(ToolUi.MUTED);connection.addView(summary);button(connection,shared.hasPassword()?"管理连接":"配置连接",()->startActivity(new Intent(this,WebDavActivity.class)));
        LinearLayout c=card();c.addView(text("应用更新",20));c.addView(text("安卓工具箱 "+BuildConfig.VERSION_NAME,15));
        updateState=text(AppUpdater.get(this).message,14);c.addView(updateState);
        updateButton=button(c,"检查更新",()->{AppUpdater u=AppUpdater.get(this);if(u.ready()) u.install(this);else if(u.available()!=null) u.download(this);else u.check();});
        c.addView(text("从 GitHub Releases 获取正式版。下载后校验 SHA-256、包名和签名，再由系统确认安装。",13));
        LinearLayout links=card();button(links,"项目源码与发行版",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AppUpdater.REPOSITORY))));
        button(links,"第三方许可",()->{
            try{String[] names=getAssets().list("licenses");new AlertDialog.Builder(this).setTitle("第三方许可").setItems(names,(dialog,index)->{
                try(java.io.InputStream in=getAssets().open("licenses/"+names[index])){String body=Streams.text(in,1048576);new AlertDialog.Builder(this).setTitle(names[index]).setMessage(body).setPositiveButton("关闭",null).show();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("关闭",null).show();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);}
    @Override protected void onResume(){super.onResume();if(page.equals("settings"))show(page);handler.post(refresh);AppUpdater.get(this).resumeInstall(this);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String next=intent.getStringExtra("page");if(next!=null)show(next);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onBackPressed(){if(!page.equals("home"))show("home");else super.onBackPressed();}
}
