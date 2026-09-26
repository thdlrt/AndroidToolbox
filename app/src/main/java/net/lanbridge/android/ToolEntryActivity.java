package net.lanbridge.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Preserve published components, share filters and URI grants across upgrades. */
abstract class ToolEntryActivity extends Activity {
    abstract String page();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent forwarded=new Intent(getIntent()).setClass(this,MainActivity.class).putExtra("page",page());
        // Keep ClipData, stream extras and grant flags on the receiving Activity.
        forwarded.setFlags((forwarded.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(forwarded);
        finish();
        overridePendingTransition(0,0);
    }
}
