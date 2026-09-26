package net.lanbridge.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.EditText;

/** No test framework dependencies. Run only on the explicitly selected emulator. */
public final class NavigationInstrumentation extends LedgerInstrumentation {
    private MainActivity activity;
    private View rail, firstRow;
    private android.os.IBinder window;
    private Throwable failure;
    private int assertions;
    private boolean ledger;
    @Override public void onCreate(Bundle arguments) { ledger=arguments!=null&&arguments.containsKey("action");super.onCreate(arguments); }
    private void check(boolean condition,String message) {
        assertions++;
        if(!condition)throw new AssertionError(message);
    }
    private void main(Runnable action) {
        runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});
        if(failure!=null)throw new AssertionError("UI assertion",failure);
        waitForIdleSync();
    }
    private View find(View view,String label) {
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View child=find(((ViewGroup)view).getChildAt(i),label);if(child!=null)return child;
        }
        return null;
    }
    private void click(String label) {
        View target=find(rail,label);check(target!=null,"Missing rail action "+label);target.performClick();
    }
    @Override public void onStart() {
        if(ledger){super.onStart();return;}
        Bundle result=new Bundle();
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                rail=activity.getWindow().getDecorView().findViewWithTag("toolbox-navigation");
                check(rail!=null&&rail.isShown(),"Run with a window at least 600dp wide");
                firstRow=find(rail,"首页");window=rail.getWindowToken();
                click("回家 VPN");
            });
            final View[] vpnView=new View[1];
            main(()->{
                vpnView[0]=activity.getFragmentManager().findFragmentByTag("vpn").getView();
                ((EditText)find(vpnView[0],"飞牛管理员")).setText("navigation-draft");
            });
            String[] labels={"文件中转站","设置","全部工具","首页","回家 VPN"};
            for(int i=0;i<30;i++) {
                final String label=labels[i%labels.length];
                main(()->{
                    click(label);
                    check(rail==activity.getWindow().getDecorView().findViewWithTag("toolbox-navigation"),"Rail was replaced");
                    check(firstRow==find(rail,"首页"),"Rail row was replaced");
                    check(window==rail.getWindowToken(),"Navigation replaced the window");
                    check(rail.getTranslationX()==0&&rail.getTranslationY()==0&&rail.getAlpha()==1,"Rail animated with content");
                    int visible=0;for(Fragment fragment:activity.getFragmentManager().getFragments())if(!fragment.isHidden())visible++;
                    check(visible==1,"Content panes overlap after navigation");
                });
                SystemClock.sleep(200);
            }
            main(()->{
                click("回家 VPN");
                View current=activity.getFragmentManager().findFragmentByTag("vpn").getView();
                check(current==vpnView[0],"VPN pane was recreated");
                check(((EditText)find(current,"飞牛管理员")).getText().toString().equals("navigation-draft"),"Draft lost after tool switch");
                activity.show("webdav");
                ((EditText)find(activity.getFragmentManager().findFragmentByTag("webdav").getView(),"用户名")).setText("connection-draft");
            });
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());
            Activity recreated=waitForMonitorWithTimeout(monitor,10000);
            check(recreated instanceof MainActivity,"Activity recreation did not complete");activity=(MainActivity)recreated;
            main(()->{
                Fragment editor=activity.getFragmentManager().findFragmentByTag("webdav");
                check(!editor.isHidden(),"Recreation lost selected content pane");
                check(((EditText)find(editor.getView(),"用户名")).getText().toString().equals("connection-draft"),"Recreation lost connection draft");
                activity.onBackPressed();
                check(!activity.getFragmentManager().findFragmentByTag("vpn").isHidden(),"Back from connection editor lost origin tool");
                activity.onBackPressed();
                check(!activity.getFragmentManager().findFragmentByTag("home").isHidden(),"Back from tool did not return home");
            });
            result.putString("stream","PASS: "+assertions+" navigation assertions; same window/rail, 30 switches, draft retention, recreation and back routing\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);
        }
    }
}
