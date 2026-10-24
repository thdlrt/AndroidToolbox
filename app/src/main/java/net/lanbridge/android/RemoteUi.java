package net.lanbridge.android;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Both backends share one catalog entry and retain their own browser and login. */
final class RemoteUi {
    static LinearLayout tabs(Activity activity, String current) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        for(String[] item:new String[][]{{"pocket","DSH"},{"hapi","Codex"}}){
            TextView tab=ToolUi.text(activity,item[1],14,item[0].equals(current)?ToolUi.BLUE:ToolUi.MUTED);
            tab.setGravity(Gravity.CENTER);tab.setMinHeight(ToolUi.dp(activity,44));tab.setContentDescription("切换到"+item[1]);
            tab.setSelected(item[0].equals(current));ToolUi.ripple(tab,item[0].equals(current)?0xffe5edff:ToolUi.BG,10);
            tab.setOnClickListener(v->((MainActivity)activity).show(item[0]));row.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
        }
        return row;
    }
}
