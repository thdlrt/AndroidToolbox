package net.lanbridge.android;

import android.app.Activity;
import android.app.Fragment;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import java.io.File;

/** A tool owns its content; MainActivity alone owns the window and navigation. */
abstract class ToolFragment extends Fragment {
    private Activity host;
    private boolean active;
    static final int RESULT_OK = Activity.RESULT_OK;
    static final String INPUT_METHOD_SERVICE = Context.INPUT_METHOD_SERVICE;
    @Override public void onAttach(Context context) { super.onAttach(context); host = (Activity) context; }
    final Activity context() { return host; }
    protected abstract View createContent(Bundle state);
    @Override public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) { return createContent(state); }
    // Native Fragment hide/show does not pause a fragment. Poll only the visible page.
    private void updateActive() {
        boolean next = isResumed() && !isHidden() && getView() != null;
        if (next == active) return;
        active = next;
        if (active) onPageResume(); else onPagePause();
    }
    @Override public final void onResume() { super.onResume(); updateActive(); }
    @Override public final void onPause() { if(active){active=false;onPagePause();}super.onPause(); }
    @Override public final void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); updateActive(); }
    protected void onPageResume() {}
    protected void onPagePause() {}
    boolean back() { return false; }
    final void navigate(String page) { ((MainActivity)host).show(page); }
    final void closePage() { ((MainActivity)host).closePage(); }
    final Intent getIntent() { Intent intent=getArguments()==null?null:getArguments().getParcelable("incoming");return intent==null?new Intent():intent; }
    final SharedPreferences getSharedPreferences(String name,int mode) { return host.getSharedPreferences(name,mode); }
    final SharedPreferences getPreferences(int mode) { return host.getPreferences(mode); }
    final ContentResolver getContentResolver() { return host.getContentResolver(); }
    final File getCacheDir() { return host.getCacheDir(); }
    final String getPackageName() { return host.getPackageName(); }
    final android.content.res.AssetManager getAssets() { return host.getAssets(); }
    final Object getSystemService(String name) { return host.getSystemService(name); }
    final int checkSelfPermission(String name) { return host.checkSelfPermission(name); }
    final void runOnUiThread(Runnable action) { host.runOnUiThread(action); }
    final ComponentName startService(Intent intent) { return host.startService(intent); }
    final ComponentName startForegroundService(Intent intent) { return host.startForegroundService(intent); }
    @Override public void startActivity(Intent intent) {
        String page = MainActivity.pageFor(intent.getComponent());
        if (page != null) navigate(page); else super.startActivity(intent);
    }
}
