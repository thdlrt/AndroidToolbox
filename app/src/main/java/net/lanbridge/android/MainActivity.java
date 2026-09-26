package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** One window and one navigation rail, with independently retained tool panes. */
public final class MainActivity extends Activity {
    private static final int CONTENT_ID = 0x00ad001;
    private ToolUi.Shell shell;
    private LinearLayout bottom;
    private String page = "home", connectionReturn = "settings";

    static String pageFor(ComponentName component) {
        if (component == null) return null;
        String name = component.getClassName();
        if (name.equals(RelayActivity.class.getName())) return "relay";
        if (name.equals(VpnActivity.class.getName())) return "vpn";
        if (name.equals(WebDavActivity.class.getName())) return "webdav";
        if (name.equals(LedgerActivity.class.getName())) return "ledger";
        if (name.equals(DiagnosticsActivity.class.getName())) return "diagnostics";
        return null;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout body = ToolUi.column(this);
        FrameLayout pane = new FrameLayout(this);
        pane.setId(CONTENT_ID);
        body.addView(pane,new LinearLayout.LayoutParams(-1,0,1));
        bottom = new LinearLayout(this);
        body.addView(bottom);
        shell = new ToolUi.Shell(this,body,"home");
        shell.bottom(bottom);
        setContentView(shell);
        if (state != null) {
            page = state.getString("page","home");
            connectionReturn = state.getString("connectionReturn","settings");
            show(page,false,null);
        } else handleIntent(getIntent());
    }
    void show(String id) { show(id,true,null); }
    private void show(String id,boolean animate,Intent incoming) {
        if (!java.util.Arrays.asList("home","tools","settings","relay","vpn","webdav","ledger","diagnostics").contains(id)) id="home";
        FragmentManager manager = getFragmentManager();
        ToolFragment next = (ToolFragment)manager.findFragmentByTag(id);
        boolean changed = !page.equals(id);
        if (id.equals("webdav") && changed) connectionReturn = page;
        if (changed) {
            View focus = getCurrentFocus();
            if (focus != null) {
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);
                focus.clearFocus();
            }
        }
        FragmentTransaction transaction = manager.beginTransaction();
        // Only the content fragment animates; the rail remains in the same window.
        if (animate && changed) transaction.setCustomAnimations(R.animator.pane_enter,0);
        for (Fragment fragment : manager.getFragments())
            if (fragment != next && !fragment.isHidden()) transaction.hide(fragment);
        if (next == null) {
            next = id.equals("ledger") ? new LedgerFragment() : id.equals("diagnostics") ? new DiagnosticsFragment() : id.equals("relay") ? new RelayFragment() : id.equals("vpn") ? new VpnFragment() : id.equals("webdav") ? new WebDavFragment() : new HomeFragment();
            Bundle arguments = new Bundle();arguments.putString("page",id);
            arguments.putParcelable("incoming",incoming == null ? new Intent() : new Intent(incoming));
            next.setArguments(arguments);
            transaction.add(CONTENT_ID,next,id);
        } else {
            transaction.show(next);
            if (incoming != null && next instanceof RelayFragment) ((RelayFragment)next).receiveIntent(new Intent(incoming));
        }
        page = id;
        transaction.commitNow();
        shell.selected(page.equals("webdav") ? "settings" : page);
        renderBottom();
    }
    private void renderBottom() {
        bottom.removeAllViews();
        shell.bottomEnabled(page.equals("home") || page.equals("tools") || page.equals("settings"));
        String[][] items={{"home","工具首页","home"},{"tools","全部工具","grid"},{"settings","设置","settings"}};
        for (String[] item:items) {
            LinearLayout button=ToolUi.column(this);button.setGravity(Gravity.CENTER);
            boolean selected=page.equals(item[0]);
            ToolUi.ripple(button,selected?0xffedf2ff:android.graphics.Color.WHITE,16);
            button.addView(ToolUi.icon(this,item[2],selected?ToolUi.BLUE:ToolUi.MUTED),new LinearLayout.LayoutParams(ToolUi.dp(this,23),ToolUi.dp(this,23)));
            TextView label=ToolUi.text(this,item[1],12,selected?ToolUi.BLUE:ToolUi.MUTED);label.setPadding(0,ToolUi.dp(this,5),0,0);button.addView(label);
            button.setContentDescription(item[1]);button.setFocusable(true);button.setOnClickListener(v->show(item[0]));
            bottom.addView(button,new LinearLayout.LayoutParams(0,ToolUi.dp(this,68),1));
        }
    }
    private void handleIntent(Intent intent) {
        String target=intent.getStringExtra("page");
        boolean shared=Intent.ACTION_VIEW.equals(intent.getAction())||Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction());
        show(shared?"relay":target==null?"home":target,false,shared?intent:null);
        intent.setAction(null);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent); }
    @Override protected void onResume() { super.onResume();AppUpdater.get(this).resumeInstall(this);LedgerSync.get(this).sync(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state);state.putString("page",page);state.putString("connectionReturn",connectionReturn); }
    void closePage() { show(page.equals("webdav")?connectionReturn:"home"); }
    @Override public void onBackPressed() {
        ToolFragment active=(ToolFragment)getFragmentManager().findFragmentByTag(page);
        if(active!=null&&active.back())return;
        if(!page.equals("home"))closePage();else super.onBackPressed();
    }
}
