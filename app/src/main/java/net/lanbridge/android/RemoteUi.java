package net.lanbridge.android;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Both backends share one catalog entry and retain their own browser and login. */
final class RemoteUi {
    static LinearLayout tabs(Activity activity, String current, Runnable computerSessions) {
        return tabs(activity,current,"电脑会话",computerSessions);
    }
    static LinearLayout tabs(Activity activity, String current, String actionLabel, Runnable computerSessions) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        for(String[] item:new String[][]{{"pocket","DSH"},{"hapi","Codex"}}){
            TextView tab=ToolUi.text(activity,item[1],14,item[0].equals(current)?ToolUi.BLUE:ToolUi.MUTED);
            tab.setGravity(Gravity.CENTER);tab.setMinHeight(ToolUi.dp(activity,44));tab.setContentDescription("切换到"+item[1]);
            tab.setSelected(item[0].equals(current));ToolUi.ripple(tab,item[0].equals(current)?0xffe5edff:ToolUi.BG,10);
            tab.setOnClickListener(v->((MainActivity)activity).show(item[0]));row.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(computerSessions!=null){
            TextView button=ToolUi.text(activity,actionLabel,14,ToolUi.BLUE);button.setGravity(Gravity.CENTER);button.setMinHeight(ToolUi.dp(activity,44));
            button.setPadding(ToolUi.dp(activity,14),0,ToolUi.dp(activity,14),0);button.setOnClickListener(v->computerSessions.run());row.addView(button);
        }
        return row;
    }
}
