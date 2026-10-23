package net.lanbridge.android;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Verifies the pocket page's immersive mode without a desktop server: a loopback fixture page keeps
 * the WebView alive while the test drives the real expand, exit and system-back paths.
 */
final class PocketFullscreenInstrumentation {
    private static int assertions;

    static String run(Instrumentation instrumentation, Context context) throws Exception {
        assertions = 0;
        try {
        int port = startFixtureServer();
        PocketSettings.save(context, "127.0.0.1:" + port, "12345678");
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        final PocketFragment[] pocket = new PocketFragment[1];
        instrumentation.runOnMainSync(() -> {
            activity.show("pocket");
            pocket[0] = (PocketFragment) activity.getFragmentManager().findFragmentByTag("pocket");
            check(pocket[0] != null, "手机访问页面没有创建");
            check(pocket[0].getView() != null, "手机访问页面没有视图");
            check(label(pocket[0].getView(), "全屏查看"), "标题栏缺少全屏按钮");
        });
        instrumentation.waitForIdleSync();
        waitForPage(instrumentation, pocket[0]);
        // Start from a phone-shaped window; the live resize below models a foldable opening.
        shell(instrumentation, "wm size 1080x1920");
        SystemClock.sleep(900);
        instrumentation.waitForIdleSync();

        View decor = activity.getWindow().getDecorView();
        View rail = decor.findViewWithTag("toolbox-navigation");
        check(rail != null, "找不到工具箱导航栏");
        View railPanel = rail.getParent() instanceof View ? (View) rail.getParent() : rail;
        final Object webBefore = field(pocket[0], "web");
        check(webBefore instanceof WebView, "WebView 没有创建");
        check(((WebView) webBefore).getParent() != null, "WebView 没有挂到页面上");
        capture(instrumentation, context, "pocket-normal.png");

        click(instrumentation, decor, "全屏查看");
        SystemClock.sleep(600);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            check(activity.pocketFullscreen(), "点击全屏后状态没有切换");
            check(((View) field(pocket[0], "header")).getVisibility() == View.GONE, "全屏后标题栏仍然可见");
            check(((View) field(pocket[0], "exitFullscreen")).getVisibility() == View.VISIBLE, "全屏后退出按钮不可见");
            check(railPanel.getVisibility() == View.GONE, "全屏后左侧导航仍然可见");
            check(((View) field(activity, "bottom")).getVisibility() == View.GONE, "全屏后底部导航仍然可见");
            check(((View) field(activity, "globalBar")).getVisibility() == View.GONE, "全屏后同步栏仍然可见");
            check(activity.getWindow().getAttributes().layoutInDisplayCutoutMode
                == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, "全屏没有启用刘海/折叠屏布局");
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsets insets = decor.getRootWindowInsets();
                check(insets != null && !insets.isVisible(WindowInsets.Type.statusBars()), "全屏后状态栏没有隐藏");
                check(!insets.isVisible(WindowInsets.Type.navigationBars()), "全屏后导航栏没有隐藏");
            }
            check(field(pocket[0], "web") == webBefore, "切换全屏时 WebView 被重建");
            check(((WebView) webBefore).getParent() != null, "全屏时 WebView 被移出页面");
            check(((WebView) webBefore).getUrl() != null, "全屏时 WebView 被重新加载");
        });
        capture(instrumentation, context, "pocket-fullscreen.png");

        // Opening a foldable into its inner screen must not bring the toolbox chrome back.
        shell(instrumentation, "wm size 2200x2480");
        SystemClock.sleep(1200);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            check(activity.pocketFullscreen(), "折叠屏展开后全屏状态丢失");
            check(((View) field(pocket[0], "header")).getVisibility() == View.GONE, "折叠屏展开后标题栏重新出现");
            check(railPanel.getVisibility() == View.GONE, "折叠屏展开后左侧导航重新出现");
            check(field(pocket[0], "web") == webBefore, "折叠屏展开时 WebView 被重建");
        });
        capture(instrumentation, context, "pocket-fullscreen-fold-live.png");
        shell(instrumentation, "wm size reset");
        SystemClock.sleep(1200);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            check(activity.pocketFullscreen(), "折叠屏合上后全屏状态丢失");
            check(((View) field(pocket[0], "header")).getVisibility() == View.GONE, "折叠屏合上后标题栏重新出现");
            check(railPanel.getVisibility() == View.GONE, "折叠屏合上后左侧导航重新出现");
        });

        // The system back button leaves fullscreen before it leaves the page.
        instrumentation.runOnMainSync(activity::onBackPressed);
        SystemClock.sleep(400);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            check(!activity.pocketFullscreen(), "返回键没有退出全屏");
            check(((View) field(pocket[0], "header")).getVisibility() == View.VISIBLE, "退出全屏后标题栏没有恢复");
            check(((View) field(pocket[0], "exitFullscreen")).getVisibility() == View.GONE, "退出全屏后退出按钮仍然显示");
            int widthDp = (int) (decor.getWidth() / context.getResources().getDisplayMetrics().density);
            int expectedRail = widthDp >= 600 ? View.VISIBLE : View.GONE;
            check(railPanel.getVisibility() == expectedRail, "退出全屏后导航栏可见性与屏幕宽度不符(" + widthDp + "dp)");
            check(field(pocket[0], "web") == webBefore, "退出全屏时 WebView 被重建");
        });

        // Leaving the page while fullscreen must restore the toolbox chrome too.
        click(instrumentation, decor, "全屏查看");
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            check(activity.pocketFullscreen(), "第二次进入全屏失败");
            activity.show("home");
            check(!activity.pocketFullscreen(), "离开手机访问页后仍然处于全屏");
            check(((View) field(pocket[0], "header")).getVisibility() == View.VISIBLE, "离开页面后标题栏没有恢复");
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(activity::finish);
        return "PASS: " + assertions + " pocket fullscreen assertions";
        } finally {
            try { shell(instrumentation, "wm size reset"); } catch (Exception ignored) { }
            closeFixtureServer();
        }
    }

    /** Runs a shell command through UiAutomation and waits for it to finish. */
    private static void shell(Instrumentation instrumentation, String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
             InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) >= 0) { /* drain */ }
        }
    }

    private static void waitForPage(Instrumentation instrumentation, PocketFragment fragment) {
        for (int i = 0; i < 50; i++) {
            final boolean[] finished = {false};
            instrumentation.runOnMainSync(() -> {
                Object web = null;
                try { web = field(fragment, "web"); } catch (Exception ignored) { }
                finished[0] = web instanceof WebView && ((WebView) web).getUrl() != null;
            });
            if (finished[0]) return;
            SystemClock.sleep(100);
        }
    }

    private static void click(Instrumentation instrumentation, View root, String description) {
        instrumentation.runOnMainSync(() -> {
            View target = find(root, description);
            check(target != null, "找不到按钮: " + description);
            check(target.performClick(), "按钮没有响应: " + description);
        });
    }

    private static View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean label(View view, String text) {
        return find(view, text) != null;
    }

    private static Object field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            throw new AssertionError("读取字段失败: " + name, e);
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void capture(Instrumentation instrumentation, Context context, String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        if (bitmap == null) return;
        File directory = context.getExternalFilesDir(null);
        if (directory == null) return;
        File output = new File(directory, name);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            bitmap.recycle();
        }
    }

    private static ServerSocket server;

    private static int startFixtureServer() throws Exception {
        ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        server = socket;
        Thread thread = new Thread(() -> {
            byte[] body = ("<!doctype html><html><head><style>"
                + "html,body{margin:0;height:100%;font:16px sans-serif;background:#fff}"
                + "body{display:flex}#side{width:32%;background:#e8eef8;padding:20px;box-sizing:border-box}"
                + "#main{flex:1;padding:20px}h1{margin:0 0 12px;color:#1d2b41;font-size:22px}"
                + "</style></head><body><div id=\"side\"><h1>pocket fixture</h1>"
                + "<p id=\"app\">remote DSH page</p></div><div id=\"main\"><p>content area</p></div>"
                + "</body></html>").getBytes(StandardCharsets.UTF_8);
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { /* headers */ }
                    OutputStream out = client.getOutputStream();
                    String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n";
                    out.write(headers.getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) { }
            }
        }, "pocket-fixture");
        thread.setDaemon(true);
        thread.start();
        return socket.getLocalPort();
    }

    private static void closeFixtureServer() {
        if (server != null) {
            try { server.close(); } catch (Exception ignored) { }
            server = null;
        }
    }
}
