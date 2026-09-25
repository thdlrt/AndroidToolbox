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
    private String page="home";
    private TextView vpnState, updateState;
    private Button updateButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(vpnState!=null) vpnState.setText("connected".equals(BridgeVpnService.phase)?"已连接 · "+BridgeVpnService.message:"connecting".equals(BridgeVpnService.phase)?"正在连接":"未连接");
        if(updateState!=null) updateState.setText(AppUpdater.get(MainActivity.this).message);
        if(updateButton!=null) { AppUpdater u=AppUpdater.get(MainActivity.this);updateButton.setEnabled(!u.busy);updateButton.setText(u.ready()?"安装更新":u.available()!=null?"下载并更新":"检查更新"); }
        handler.postDelayed(this,600);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(32,46,68));t.setPadding(0,dp(6),0,dp(6));return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);content.addView(c,lp);return c;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(Color.rgb(56,108,244));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(238,243,255));bg.setCornerRadius(dp(12));b.setBackground(bg);b.setStateListAnimator(null);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null) page=state.getString("page","home");
        LinearLayout shell=column();shell.setBackgroundColor(Color.rgb(243,246,252));
        shell.setOnApplyWindowInsetsListener((v,i)->{shell.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(dp(20),dp(20),dp(20),dp(16));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=new LinearLayout(this);navigation.setBackgroundColor(Color.WHITE);shell.addView(navigation);setContentView(shell);show(page);
    }
    private void show(String id){
        page=id;content.removeAllViews();navigation.removeAllViews();vpnState=null;updateState=null;updateButton=null;
        String[][] items={{"home","工具首页"},{"tools","全部工具"},{"settings","设置"}};
        for(String[] item:items){Button b=new Button(this);b.setText(item[1]);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.parseColor(id.equals(item[0])?"#386CF4":"#64748B"));b.setBackgroundColor(Color.parseColor(id.equals(item[0])?"#EEF3FF":"#FFFFFF"));b.setStateListAnimator(null);navigation.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));b.setOnClickListener(v->show(item[0]));}
        TextView title=text(id.equals("settings")?"设置":id.equals("tools")?"全部工具":"工具首页",26);title.setTypeface(null,Typeface.BOLD);content.addView(title);
        if(id.equals("settings")){settings();return;}
        for(ToolRegistry.Tool tool:ToolRegistry.TOOLS){
            boolean favorite=getPreferences(0).getBoolean("favorite."+tool.id,true);
            if(id.equals("home")&&!favorite)continue;
            LinearLayout c=card();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_bridge);heading.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView name=text(tool.name,21);name.setPadding(dp(12),dp(6),0,dp(6));name.setTypeface(null,Typeface.BOLD);heading.addView(name);c.addView(heading);c.addView(text(tool.description,14));if(tool.id.equals("vpn")){vpnState=text("未连接",14);c.addView(vpnState);}
            button(c,"打开",()->startActivity(new Intent(this,tool.activity)));
            if(id.equals("tools")){CheckBox pin=new CheckBox(this);pin.setText("显示在工具首页");pin.setChecked(favorite);c.addView(pin);pin.setOnCheckedChangeListener((v,on)->getPreferences(0).edit().putBoolean("favorite."+tool.id,on).apply());}
        }
        if(content.getChildCount()==1){LinearLayout empty=card();empty.addView(text("暂无常用工具",18));button(empty,"选择常用工具",()->show("tools"));}
    }
    private void settings(){
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
    @Override protected void onResume(){super.onResume();handler.post(refresh);AppUpdater.get(this).resumeInstall(this);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onBackPressed(){if(!page.equals("home"))show("home");else super.onBackPressed();}
}
