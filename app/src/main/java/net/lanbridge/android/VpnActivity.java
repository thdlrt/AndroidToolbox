package net.lanbridge.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.Locale;

public final class VpnActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LoginStore store;
    private EditText origin,user,password;
    private Spinner scope;
    private CheckBox remember;
    private TextView status,details,metrics,error,probeResult;
    private Button connect,probe,forget;
    private BridgeVpnService.Credentials credentials;
    private String lastPhase="";
    private final Runnable refresh=new Runnable() { @Override public void run() { render();handler.postDelayed(this,750); } };
    private int dp(int value) { return (int)(getResources().getDisplayMetrics().density*value+.5f); }
    private GradientDrawable box(String color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(radius));return d; }
    private TextView text(String value,int size,String color) { TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(Color.parseColor(color));v.setLineSpacing(dp(3),1);return v; }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(box("#FFFFFF",20));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);parent.addView(c,p);return c;
    }
    private void gap(LinearLayout v,int h) { View spacer=new View(this);v.addView(spacer,new LinearLayout.LayoutParams(1,dp(h))); }
    private EditText input(LinearLayout card,String label,int type,String value) {
        TextView l=text(label,13,"#52617A");card.addView(l);gap(card,6);
        EditText field=new EditText(this);field.setTextSize(16);field.setSingleLine(true);field.setInputType(type);field.setText(value);field.setPadding(dp(12),0,dp(12),0);
        GradientDrawable bg=box("#F7F9FE",12);bg.setStroke(dp(1),Color.parseColor("#E4EAF5"));field.setBackground(bg);
        card.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));gap(card,16);return field;
    }
    private Button button(String label,boolean primary) {
        return ToolUi.button(this,label,primary,()->{});
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new LoginStore(this);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.parseColor("#F3F6FC"));
        LinearLayout root=column();root.setPadding(dp(20),dp(20),dp(20),dp(24));scroll.addView(root);
        root.addView(ToolUi.iconButton(this,"back","返回工具箱",this::finish));gap(root,16);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=ToolUi.icon(this,"shield",ToolUi.BLUE);heading.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView title=text("回家 VPN",25,"#182640");title.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-2,-2);titleParams.leftMargin=dp(12);heading.addView(title,titleParams);root.addView(heading);gap(root,24);
        LinearLayout stateCard=card(root);status=text("未连接",23,"#1D3157");status.setTypeface(null,Typeface.BOLD);stateCard.addView(status);gap(stateCard,8);details=text("通过 FN Connect 访问家中网络",14,"#637493");stateCard.addView(details);gap(stateCard,14);metrics=text("上传 0 KiB    下载 0 KiB",13,"#637493");stateCard.addView(metrics);
        error=text("",14,"#B34824");error.setPadding(0,0,0,dp(16));root.addView(error);
        LinearLayout login=card(root);
        origin=input(login,"NAS 地址",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,store.origin());origin.setContentDescription("NAS 地址");
        user=input(login,"飞牛管理员",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,store.username());user.setContentDescription("飞牛管理员");
        password=input(login,"密码",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,"");password.setContentDescription("飞牛密码");password.setSaveEnabled(false);
        TextView scopeLabel=text("代理范围",13,"#52617A");login.addView(scopeLabel);gap(login,6);scope=new Spinner(this);scope.setContentDescription("代理范围");
        ArrayAdapter<String> choices=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"仅回家 · 访问局域网","全局 TCP · 使用家中网络"});scope.setAdapter(choices);scope.setSelection(store.scope().equals("all")?1:0);login.addView(scope,new LinearLayout.LayoutParams(-1,dp(48)));
        gap(login,10);remember=new CheckBox(this);remember.setText("记住登录");remember.setTextSize(14);remember.setChecked(true);login.addView(remember);
        remember.setOnCheckedChangeListener((v,checked)-> { if(!checked) { store.forget();render(); } });
        TextView savedHint=text("密码由 Android Keystore 加密，保存在本机。",12,"#73819B");login.addView(savedHint);gap(login,18);
        connect=button("连接",true);login.addView(connect,new LinearLayout.LayoutParams(-1,dp(52)));connect.setOnClickListener(v->connect());
        gap(login,12);forget=button("清除已保存密码",false);login.addView(forget,new LinearLayout.LayoutParams(-1,dp(44)));forget.setOnClickListener(v->{store.forget();password.setText("");render();});
        LinearLayout testing=card(root);probe=button("测试回家连接",false);testing.addView(probe,new LinearLayout.LayoutParams(-1,dp(48)));probe.setOnClickListener(v->startService(new Intent(this,BridgeVpnService.class).setAction(BridgeVpnService.PROBE)));
        gap(testing,12);probeResult=text("",13,"#52617A");testing.addView(probeResult);
        TextView note=text("首次连接需确认 Android VPN 授权。全局模式支持 TCP；普通 UDP、QUIC 和 IPv6 暂不支持。开启后可切换应用，通知栏可直接断开。",13,"#73819B");root.addView(note);gap(root,16);
        TextView version=text(BuildConfig.VERSION_NAME+" · Android 10+",12,"#64748B");root.addView(version);setContentView(new ToolUi.Shell(this,scroll,"vpn"));
    }
    private void showError(String text) { ToolUi.update(error,text);error.setVisibility(View.VISIBLE); }
    private void connect() {
        String phase=BridgeVpnService.phase;
        if(phase.equals("connecting")||phase.equals("connected")) { startService(new Intent(this,BridgeVpnService.class).setAction(BridgeVpnService.DISCONNECT));return; }
        try {
            String o=NasSession.origin(origin.getText().toString()),u=user.getText().toString().trim(),p=password.getText().toString();
            if(u.isEmpty()) throw new IllegalArgumentException("请填写飞牛管理员账号");
            if(p.isEmpty()) p=store.password(o,u);
            if(p.isEmpty()) throw new IllegalArgumentException("请填写密码；成功连接后即可记住登录");
            credentials=new BridgeVpnService.Credentials(o,u,p,scope.getSelectedItemPosition()==1?"all":"lan",remember.isChecked());
            origin.setText(o);ToolUi.update(error,"");
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(),0);
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);return; }
            prepareVpn();
        } catch(Exception e) { showError(e.getMessage()==null?"保存的密码无法读取，请清除后重新输入":e.getMessage()); }
    }
    private void prepareVpn() { Intent approval=VpnService.prepare(this);if(approval!=null) startActivityForResult(approval,10);else begin(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) { super.onRequestPermissionsResult(request,permissions,grants);if(request==7 && credentials!=null) prepareVpn(); }
    private void begin() {
        if(credentials==null) return;
        BridgeVpnService.pending=credentials;credentials=null;startForegroundService(new Intent(this,BridgeVpnService.class).setAction(BridgeVpnService.CONNECT));
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request==10) { if(result==RESULT_OK) begin();else { credentials=null;showError("未获得 VPN 授权，可以重新点击连接"); } }
    }
    private void render() {
        String phase=BridgeVpnService.phase;boolean online=phase.equals("connected"),working=phase.equals("connecting")||phase.equals("stopping");
        ToolUi.update(status,online?"已连接":phase.equals("connecting")?"正在连接":phase.equals("stopping")?"正在断开":phase.equals("error")?"连接失败":"未连接");
        ToolUi.update(details,BridgeVpnService.message);ToolUi.update(metrics,String.format(Locale.CHINA,"上传 %.1f KiB    下载 %.1f KiB    连接 %d",BridgeVpnService.tx/1024.0,BridgeVpnService.rx/1024.0,BridgeVpnService.active));
        ToolUi.update(connect,online?"断开":phase.equals("connecting")?"取消连接":"连接");connect.setEnabled(!phase.equals("stopping"));
        origin.setEnabled(!online&&!working);user.setEnabled(!online&&!working);password.setEnabled(!online&&!working);scope.setEnabled(!online&&!working);remember.setEnabled(!working);
        probe.setEnabled(online);ToolUi.update(probe,"检查隧道会话");ToolUi.update(probeResult,BridgeVpnService.probeResult);
        String hint=store.hasPassword()?"已加密保存，留空即可连接":"请输入飞牛密码";if(!android.text.TextUtils.equals(password.getHint(),hint))password.setHint(hint);forget.setVisibility(store.hasPassword()?View.VISIBLE:View.GONE);forget.setEnabled(!working);
        if(online && !lastPhase.equals(phase)) { password.setText("");ToolUi.update(error,""); }
        lastPhase=phase;error.setVisibility(error.getText().length()==0?View.GONE:View.VISIBLE);
    }
    @Override protected void onResume() { super.onResume();handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh);super.onPause(); }
}
