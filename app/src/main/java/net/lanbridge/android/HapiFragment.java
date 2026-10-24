package net.lanbridge.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONObject;

/** Codex WebUI only. Historical Fragment name and route preserve saved-state upgrades. */
public final class HapiFragment extends ToolFragment {
    private static final int PICK=6421;
    private FrameLayout stage;
    private LinearLayout header;
    private LinearLayout tabs;
    private ImageButton exit;
    private ProgressBar progress;
    private WebView web;
    private TextView host;
    private String origin="", token="";
    private boolean installed;
    private String browserOrigin="";
    private ValueCallback<Uri[]> picker;

    @Override protected View createContent(Bundle state){
        FrameLayout page=new FrameLayout(context());
        LinearLayout root=ToolUi.column(context());
        header=new LinearLayout(context());header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ToolUi.iconButton(context(),"back","返回工具箱",this::closePage));
        LinearLayout words=ToolUi.column(context());
        TextView title=ToolUi.text(context(),"手机访问",23,ToolUi.INK);title.setTypeface(null,Typeface.BOLD);words.addView(title);
        host=ToolUi.text(context(),"",11,ToolUi.MUTED);words.addView(host);
        header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(ToolUi.iconButton(context(),"refresh","重新加载",()->{if(web!=null)web.reload();}));
        header.addView(ToolUi.iconButton(context(),"expand","全屏查看",()->{if(configured())((MainActivity)context()).setPocketFullscreen(true);}));
        header.addView(ToolUi.iconButton(context(),"settings","连接设置",this::editSettings));root.addView(header);
        tabs=new LinearLayout(context());root.addView(tabs);renderTabs();
        progress=new ProgressBar(context(),null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        stage=new FrameLayout(context());root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));page.addView(root,new FrameLayout.LayoutParams(-1,-1));
        exit=new ImageButton(context());exit.setImageDrawable(new ToolUi.Icon("contract",0xffffffff));exit.setContentDescription("退出全屏");exit.setTooltipText("退出全屏");
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(0x99101828);exit.setBackground(bg);exit.setPadding(dp(10),dp(10),dp(10),dp(10));
        exit.setOnClickListener(v->((MainActivity)context()).setPocketFullscreen(false));
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.TOP|Gravity.END);ep.topMargin=dp(10);ep.rightMargin=dp(10);page.addView(exit,ep);
        page.setOnApplyWindowInsetsListener((v,i)->{
            DisplayCutout c=i.getDisplayCutout();boolean full=((MainActivity)context()).pocketFullscreen();
            int l=full&&c!=null?c.getSafeInsetLeft():0,t=full&&c!=null?c.getSafeInsetTop():0,r=full&&c!=null?c.getSafeInsetRight():0,b=full&&c!=null?c.getSafeInsetBottom():0;
            if(web!=null)web.setPadding(l,t,r,b);
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)exit.getLayoutParams();p.topMargin=dp(10)+t;p.rightMargin=dp(10)+r;exit.setLayoutParams(p);return i;
        });
        render();applyFullscreenUi(((MainActivity)context()).pocketFullscreen());return page;
    }
    private void render(){
        origin=CodexWebSettings.origin(context());token=CodexWebSettings.token(context());host.setText(PocketSettings.display(origin));host.setVisibility(origin.isEmpty()?View.GONE:View.VISIBLE);
        stage.removeAllViews();
        if(configured())ensureBrowser();else stage.addView(form(false));
    }
    private boolean configured(){return CodexWebSettings.configured(context());}
    private View form(boolean dialog){
        ScrollView scroll=new ScrollView(context());LinearLayout body=ToolUi.column(context());body.setPadding(dp(20),dp(4),dp(20),dp(24));
        EditText address=ToolUi.field(context(),body,"电脑地址",origin,false);address.setHint("192.168.1.10:9526 或完整连接链接");
        EditText secret=ToolUi.field(context(),body,"访问令牌","",true);secret.setHint(token.isEmpty()?"电脑服务的访问令牌":"已保存，同一地址留空保持");
        TextView error=ToolUi.text(context(),"",12,0xffc0392b);body.addView(error);
        body.addView(ToolUi.button(context(),dialog?"保存并连接":"连接",true,()->{
            try{CodexWebSettings.save(context(),address.getText().toString(),secret.getText().toString());destroyBrowser();if(settingsDialog!=null)settingsDialog.dismiss();render();}
            catch(Exception e){error.setText(e.getMessage());}
        }));
        if(dialog)body.addView(ToolUi.button(context(),"清除登录",false,()->{
            WebStorage.getInstance().deleteOrigin(origin);
            CodexWebSettings.clear(context());destroyBrowser();settingsDialog.dismiss();((MainActivity)context()).setPocketFullscreen(false);render();
        }));
        TextView hint=ToolUi.text(context(),"电脑安装并启用 Codex WebUI 桌面桥接后，填写其地址（默认 9526）和访问令牌。与桌面共用会话，支持流式输出、引导和队列。使用局域网或已连接的 VPN。",12,ToolUi.MUTED);hint.setPadding(0,dp(12),0,0);body.addView(hint);
        scroll.addView(body);return scroll;
    }
    private AlertDialog settingsDialog;
    private void renderTabs(){
        tabs.removeAllViews();
        tabs.addView(RemoteUi.tabs(context(),"hapi"),new LinearLayout.LayoutParams(-1,-2));
    }
    private void editSettings(){settingsDialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("连接设置").setView(form(true)).setNegativeButton("取消",null).create();settingsDialog.show();}
    @SuppressLint("SetJavaScriptEnabled") private void ensureBrowser(){
        if(web==null){
            browserOrigin=origin;
            host.setText(PocketSettings.display(browserOrigin)+" · Codex WebUI");
            installed=false;web=new WebView(context());WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);
            web.setWebChromeClient(new WebChromeClient(){
                @Override public void onProgressChanged(WebView v,int n){progress.setProgress(n);progress.setVisibility(n<100?View.VISIBLE:View.GONE);}
                @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
                    if(!CodexWebConnection.sameOrigin(browserOrigin,v.getUrl()))return false;
                    if(picker!=null)picker.onReceiveValue(null);picker=cb;
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,p.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                    try{startActivityForResult(intent,PICK);}catch(Exception e){picker.onReceiveValue(null);picker=null;}return true;
                }
            });
            web.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){View error=stage.findViewWithTag("hapi-error");if(error!=null)stage.removeView(error);}
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                    if(CodexWebConnection.sameOrigin(browserOrigin,r.getUrl().toString()))return false;
                    if(r.hasGesture()&&("https".equals(r.getUrl().getScheme())||"http".equals(r.getUrl().getScheme())))try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception ignored){}
                    return true;
                }
                @Override public void onPageFinished(WebView v,String url){
                    if(!CodexWebConnection.sameOrigin(browserOrigin,url)||installed)return;
                    installed=true;
                    // Set WebUI's own auth store, then reload once. Never put the token in a URL.
                    String script="(function(){var k='codex-webui-access-token',t="+JSONObject.quote(token)+";if(localStorage.getItem(k)!==t){localStorage.setItem(k,t);location.reload();}})()";
                    v.evaluateJavascript(script,null);
                }
                @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())failure("连不上 Codex WebUI。请确认电脑桌面桥接已启用，服务端口（默认 9526）允许访问，手机与电脑网络互通。点击刷新重试。");}
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())failure("电脑服务返回 HTTP "+e.getStatusCode()+"，请检查服务地址。");}
            });
            web.loadUrl(browserOrigin+"/");
        }
        if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);stage.addView(web,new FrameLayout.LayoutParams(-1,-1));
    }
    private void failure(String text){TextView t=ToolUi.text(context(),text,14,ToolUi.MUTED);t.setPadding(dp(24),dp(24),dp(24),dp(24));t.setBackgroundColor(ToolUi.BG);t.setTag("hapi-error");View old=stage.findViewWithTag("hapi-error");if(old!=null)stage.removeView(old);stage.addView(t,new FrameLayout.LayoutParams(-1,-2));t.setOnClickListener(v->{stage.removeView(t);if(web!=null)web.reload();});}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK&&picker!=null){picker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));picker=null;}}
    void applyFullscreenUi(boolean on){if(header!=null)header.setVisibility(on?View.GONE:View.VISIBLE);if(tabs!=null)tabs.setVisibility(on?View.GONE:View.VISIBLE);if(exit!=null)exit.setVisibility(on?View.VISIBLE:View.GONE);if(getView()!=null){getView().requestLayout();getView().requestApplyInsets();}}
    @Override boolean fullBleed(){return ((MainActivity)context()).pocketFullscreen();}
    @Override boolean back(){if(((MainActivity)context()).pocketFullscreen()){((MainActivity)context()).setPocketFullscreen(false);return true;}if(web!=null&&web.canGoBack()){web.goBack();return true;}return false;}
    @Override protected void onPageResume(){if(web!=null)web.onResume();}
    @Override protected void onPagePause(){if(web!=null)web.onPause();}
    private void destroyBrowser(){if(picker!=null){picker.onReceiveValue(null);picker=null;}if(web!=null){if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}installed=false;}
    @Override public void onDestroyView(){destroyBrowser();if(settingsDialog!=null)settingsDialog.dismiss();super.onDestroyView();}
    private int dp(int n){return ToolUi.dp(context(),n);}
}
