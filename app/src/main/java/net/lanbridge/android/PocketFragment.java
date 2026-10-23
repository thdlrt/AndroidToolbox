package net.lanbridge.android;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

/**
 * Opens the desktop DSH "手机访问" page on this phone. The desktop server shows its own login form;
 * this page replays the saved LAN PIN into it, so the session cookie is minted by the server itself
 * and the tool never has to forge one.
 */
public final class PocketFragment extends ToolFragment {
    private static final int MAX_LOGINS = 2;
    private static final String PROBE =
        "(function(){try{return document.getElementById('current-password')?'login':'app';}catch(e){return 'app';}})()";

    private FrameLayout stage;
    private WebView web;
    private ProgressBar progress;
    private TextView hostView;
    private TextView failureMessage;
    private ImageButton refreshButton;
    private ImageButton fullscreenButton;
    private ImageButton exitFullscreen;
    private LinearLayout header;
    private LinearLayout failure;
    private WindowInsets lastInsets;
    private String origin = "";
    private String pin = "";
    private String host = "";
    private boolean loaded;
    private boolean fullscreen;
    private int logins;

    @Override protected View createContent(Bundle state) {
        FrameLayout page = new FrameLayout(context());
        page.setBackgroundColor(ToolUi.BG);
        LinearLayout root = ToolUi.column(context());
        root.setBackgroundColor(ToolUi.BG);
        header = header();
        root.addView(header);
        progress = new ProgressBar(context(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ToolUi.BLUE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x00000000));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        stage = new FrameLayout(context());
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        exitFullscreen = exitButton();
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
        exitParams.topMargin = dp(10);
        exitParams.rightMargin = dp(10);
        page.addView(exitFullscreen, exitParams);
        fullscreen = ((MainActivity) context()).pocketFullscreen() && PocketSettings.configured(context());
        applyFullscreenUi(fullscreen);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            lastInsets = insets;
            applyCutout(insets);
            return insets;
        });
        render();
        return page;
    }

    private LinearLayout header() {
        LinearLayout row = new LinearLayout(context());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ToolUi.iconButton(context(), "back", "返回工具箱", this::closePage));
        LinearLayout words = ToolUi.column(context());
        words.setPadding(dp(4), 0, dp(4), 0);
        TextView title = ToolUi.text(context(), "手机访问", 23, ToolUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        words.addView(title);
        hostView = ToolUi.text(context(), "", 11, ToolUi.MUTED);
        hostView.setVisibility(View.GONE);
        words.addView(hostView);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        refreshButton = ToolUi.iconButton(context(), "refresh", "重新加载", () -> {
            logins = 0;
            hideFailure();
            reload();
        });
        row.addView(refreshButton);
        fullscreenButton = ToolUi.iconButton(context(), "expand", "全屏查看", () -> ((MainActivity) context()).setPocketFullscreen(true));
        row.addView(fullscreenButton);
        row.addView(ToolUi.iconButton(context(), "settings", "连接设置", this::editSettings));
        row.setPadding(0, 0, 0, dp(4));
        return row;
    }

    private ImageButton exitButton() {
        ImageButton button = new ImageButton(context());
        button.setImageDrawable(new ToolUi.Icon("contract", 0xffffffff));
        button.setContentDescription("退出全屏");
        button.setTooltipText("退出全屏");
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0x99101828);
        button.setBackground(background);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setElevation(dp(4));
        button.setVisibility(View.GONE);
        button.setOnClickListener(v -> ((MainActivity) context()).setPocketFullscreen(false));
        return button;
    }

    /** Keeps the page chrome and the WebView padding in step with immersive mode. */
    void applyFullscreenUi(boolean on) {
        fullscreen = on;
        if (header != null) header.setVisibility(on ? View.GONE : View.VISIBLE);
        if (exitFullscreen != null) exitFullscreen.setVisibility(on ? View.VISIBLE : View.GONE);
        View root = getView();
        if (root != null) {
            root.requestLayout();
            root.requestApplyInsets();
        }
    }

    /** A foldable's inner screen or a camera cutout must not cover the page or the exit button. */
    private void applyCutout(WindowInsets insets) {
        if (insets == null) return;
        DisplayCutout cut = insets.getDisplayCutout();
        int left = cut == null ? 0 : cut.getSafeInsetLeft();
        int top = cut == null ? 0 : cut.getSafeInsetTop();
        int right = cut == null ? 0 : cut.getSafeInsetRight();
        int bottom = cut == null ? 0 : cut.getSafeInsetBottom();
        if (exitFullscreen != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) exitFullscreen.getLayoutParams();
            int marginTop = dp(10) + top, marginRight = dp(10) + right;
            if (params.topMargin != marginTop || params.rightMargin != marginRight) {
                params.topMargin = marginTop;
                params.rightMargin = marginRight;
                exitFullscreen.setLayoutParams(params);
            }
        }
        if (web != null) {
            int l = fullscreen ? left : 0, t = fullscreen ? top : 0, r = fullscreen ? right : 0, b = fullscreen ? bottom : 0;
            if (web.getPaddingLeft() != l || web.getPaddingTop() != t || web.getPaddingRight() != r || web.getPaddingBottom() != b)
                web.setPadding(l, t, r, b);
        }
    }

    @Override boolean fullBleed() {
        return fullscreen;
    }

    private void render() {
        if (stage == null) return;
        stage.removeAllViews();
        origin = PocketSettings.origin(context());
        pin = PocketSettings.pin(context());
        host = Uri.parse(origin).getHost() == null ? "" : Uri.parse(origin).getHost();
        boolean ready = PocketSettings.configured(context());
        if (fullscreenButton != null) fullscreenButton.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) {
            hostView.setText(PocketSettings.display(origin));
            hostView.setVisibility(View.VISIBLE);
            refreshButton.setVisibility(View.VISIBLE);
            ensureBrowser();
        } else {
            hostView.setVisibility(View.GONE);
            refreshButton.setVisibility(View.GONE);
            stage.addView(setupForm());
            if (((MainActivity) context()).pocketFullscreen()) ((MainActivity) context()).setPocketFullscreen(false);
        }
    }

    /** First-run page: the two values the desktop shows next to its QR code. */
    private View setupForm() {
        ScrollView scroll = new ScrollView(context());
        scroll.setFillViewport(true);
        LinearLayout page = ToolUi.column(context());
        int p = dp(20);
        page.setPadding(p, dp(4), p, dp(24));
        TextView lead = ToolUi.text(context(), "在手机上打开电脑里的 DSH", 20, ToolUi.INK);
        lead.setTypeface(null, Typeface.BOLD);
        page.addView(lead);
        TextView sub = ToolUi.text(context(), "登录一次后保持会话，电脑和手机需要连同一个 Wi-Fi。", 13, ToolUi.MUTED);
        sub.setPadding(0, dp(6), 0, dp(16));
        page.addView(sub);

        LinearLayout card = ToolUi.card(context());
        EditText address = ToolUi.field(context(), card, "电脑地址", origin, false);
        address.setHint("192.168.100.110:3081");
        EditText pass = ToolUi.field(context(), card, "访问密码", pin, true);
        pass.setHint("电脑上显示的 8 位数字");
        TextView error = ToolUi.text(context(), "", 12, 0xffc0392b);
        error.setVisibility(View.GONE);
        error.setPadding(0, dp(8), 0, 0);
        card.addView(error);
        card.addView(ToolUi.button(context(), "打开", true, () -> {
            try {
                PocketSettings.save(context(), address.getText().toString(), pass.getText().toString());
                error.setVisibility(View.GONE);
                render();
                reload();
            } catch (Exception e) {
                error.setText(e.getMessage());
                error.setVisibility(View.VISIBLE);
            }
        }));
        page.addView(card);

        TextView paste = ToolUi.text(context(), "从剪贴板填入地址", 13, ToolUi.BLUE);
        paste.setPadding(dp(4), dp(14), dp(4), dp(14));
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) { toast("剪贴板是空的"); return; }
            address.setText(clip.getItemAt(0).coerceToText(context()).toString().trim());
        });
        page.addView(paste);

        TextView hint = ToolUi.text(context(),
            "地址和密码在电脑端 设置 → 手机访问 里：打开「局域网访问」后，二维码下面那行就是地址。", 12, ToolUi.MUTED);
        hint.setPadding(dp(4), 0, dp(4), 0);
        page.addView(hint);
        scroll.addView(page, new ViewGroup.LayoutParams(-1, -2));
        return scroll;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureBrowser() {
        if (web == null) {
            web = new WebView(context());
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);
            // DSH ships a desktop-shaped layout; pinch zoom is the escape hatch when something is cut off.
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setTextZoom(100);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(web, true);
            web.setBackgroundColor(ToolUi.BG);
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onProgressChanged(WebView view, int value) {
                    progress.setProgress(value);
                    progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                }
            });
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) { probeLogin(); }

                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri url = request.getUrl();
                    String scheme = url.getScheme() == null ? "" : url.getScheme();
                    if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
                    if (url.getHost() != null && url.getHost().equals(host)) return false;
                    try { startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (Exception ignored) { }
                    return true;
                }

                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (!request.isForMainFrame()) return;
                    CharSequence text = error.getDescription();
                    showFailure("连不上电脑界面\n" + (text == null ? "" : text) + "\n手机和电脑要在同一个 Wi-Fi 里，电脑端也要打开「局域网访问」。");
                }
            });
        }
        if (stage.indexOfChild(web) < 0) stage.addView(web, new FrameLayout.LayoutParams(-1, -1));
        if (lastInsets != null) applyCutout(lastInsets);
        if (!loaded) {
            loaded = true;
            reload();
        }
    }

    private void reload() {
        if (web == null || origin.isEmpty()) return;
        hideFailure();
        web.loadUrl(PocketSettings.startUrl(origin));
    }

    /** The desktop login page shows its own form; fill it with the stored PIN and submit natively. */
    private void probeLogin() {
        if (web == null) return;
        if (pin.isEmpty()) {
            showFailure("电脑端开启了访问密码\n请点右上角设置，填入手机访问密码。");
            return;
        }
        web.evaluateJavascript(PROBE, value -> {
            if (web == null || value == null) return;
            if (value.contains("login")) {
                signIn();
            } else {
                logins = 0;
                hideFailure();
                CookieManager.getInstance().flush();
            }
        });
    }

    private void signIn() {
        if (logins >= MAX_LOGINS) {
            showFailure("密码不对，打不开电脑界面\n请在设置里重新填写手机访问密码。");
            return;
        }
        logins++;
        final String script = "(function(){var p=document.getElementById('current-password');"
            + "var f=p&&p.form;if(!p||!f)return '0';p.value=" + jsString(pin) + ";f.submit();return '1';})()";
        // A stale DSH cookie from an earlier attempt makes the proxy refuse the handshake, so every
        // attempt starts from an empty jar. Then submit the page's own form: a native postUrl misses
        // the Referer/Sec-Fetch headers the proxy checks, and the handshake never completes.
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(removed -> {
            cookies.flush();
            if (web == null) return;
            web.evaluateJavascript(script, value -> {
                if (web == null) return;
                if (value == null || value.contains("0")) {
                    try { web.postUrl(PocketSettings.loginUrl(origin), PocketSettings.formBody(pin).getBytes("UTF-8")); }
                    catch (Exception e) { showFailure("登录提交失败\n" + e.getMessage()); }
                }
            });
        });
    }

    private static String jsString(String value) {
        StringBuilder b = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('\'').toString();
    }

    private void showFailure(String message) {
        if (stage == null) return;
        if (failure == null) {
            failure = ToolUi.column(context());
            failure.setBackgroundColor(ToolUi.BG);
            failure.setGravity(Gravity.CENTER);
            int p = dp(28);
            failure.setPadding(p, p, p, p);
            TextView head = ToolUi.text(context(), "打不开电脑界面", 17, ToolUi.INK);
            head.setTypeface(null, Typeface.BOLD);
            head.setGravity(Gravity.CENTER);
            failure.addView(head);
            failureMessage = ToolUi.text(context(), "", 13, ToolUi.MUTED);
            failureMessage.setGravity(Gravity.CENTER);
            failureMessage.setPadding(0, dp(10), 0, dp(6));
            failure.addView(failureMessage);
            failure.addView(ToolUi.button(context(), "重试", true, () -> {
                logins = 0;
                hideFailure();
                reload();
            }));
            failure.addView(ToolUi.button(context(), "连接设置", false, this::editSettings));
        }
        failureMessage.setText(message);
        if (stage.indexOfChild(failure) < 0) stage.addView(failure, new FrameLayout.LayoutParams(-1, -1));
        stage.removeView(web);
        progress.setVisibility(View.GONE);
    }

    private void hideFailure() {
        if (failure != null && stage != null) stage.removeView(failure);
        if (web != null && stage != null && stage.indexOfChild(web) < 0)
            stage.addView(web, new FrameLayout.LayoutParams(-1, -1));
    }

    private void editSettings() {
        LinearLayout form = ToolUi.column(context());
        int p = dp(20);
        form.setPadding(p, dp(4), p, 0);
        EditText address = ToolUi.field(context(), form, "电脑地址", origin, false);
        address.setHint("192.168.100.110:3081");
        EditText pass = ToolUi.field(context(), form, "访问密码", "", true);
        pass.setHint(pin.isEmpty() ? "电脑上显示的数字" : "已保存，留空保持不变");
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context())
            .setTitle("连接设置")
            .setView(form)
            .setNegativeButton("取消", null)
            .setNeutralButton("清除登录", (d, w) -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                logins = 0;
                toast("已清除登录状态");
                reload();
            })
            .setPositiveButton("保存", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String previous = PocketSettings.save(context(), address.getText().toString(), pass.getText().toString());
                boolean changed = !previous.equals(PocketSettings.origin(context()));
                if (changed) {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    if (web != null) { stage.removeView(web); web.destroy(); web = null; }
                    loaded = false;
                }
                logins = 0;
                dialog.dismiss();
                render();
                if (web != null && changed) reload();
            } catch (Exception e) {
                toast(e.getMessage());
            }
        }));
        dialog.show();
    }

    @Override boolean back() {
        if (((MainActivity) context()).pocketFullscreen()) {
            ((MainActivity) context()).setPocketFullscreen(false);
            return true;
        }
        if (web != null && web.canGoBack()) { web.goBack(); return true; }
        return false;
    }

    @Override protected void onPageResume() {
        if (web == null) return;
        web.onResume();
        if (!loaded && PocketSettings.configured(context())) reload();
    }

    @Override protected void onPagePause() {
        if (web != null) web.onPause();
    }

    @Override public void onDestroyView() {
        if (web != null) {
            if (stage != null) stage.removeView(web);
            web.destroy();
            web = null;
        }
        loaded = false;
        lastInsets = null;
        super.onDestroyView();
    }

    private void toast(String message) { Toast.makeText(context(), message, Toast.LENGTH_SHORT).show(); }
    private int dp(int n) { return ToolUi.dp(context(), n); }
}
