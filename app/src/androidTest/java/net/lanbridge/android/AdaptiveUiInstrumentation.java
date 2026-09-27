package net.lanbridge.android;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Layout acceptance against an explicitly selected emulator; does not initiate AI, VPN or file upload actions. */
public final class AdaptiveUiInstrumentation extends Instrumentation {
    private MainActivity activity; private int assertions; private Throwable failure; private String mode;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","phone");start();}
    private void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private void main(Runnable task){runOnMainSync(()->{try{task.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);waitForIdleSync();}
    private void geometry(View view){
        if(view.getVisibility()==View.GONE)return;
        if(view instanceof ToolUi.Grid){ViewGroup grid=(ViewGroup)view;for(int i=0;i<grid.getChildCount();i++){View child=grid.getChildAt(i);if(child.getVisibility()==View.GONE)continue;check(child.getWidth()>0&&child.getRight()<=grid.getWidth(),"grid child exceeds pane");for(int j=0;j<i;j++){View other=grid.getChildAt(j);check(child.getRight()<=other.getLeft()||child.getLeft()>=other.getRight()||child.getBottom()<=other.getTop()||child.getTop()>=other.getBottom(),"grid children overlap");}}}
        if(view instanceof Button&&view.isShown()){Button b=(Button)view;check(b.getWidth()>=ToolUi.dp(activity,48),"button too narrow: "+b.getText());if(b.getLayout()!=null)check(b.getLayout().getHeight()<=b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom()+2,"button text clipped: "+b.getText());}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)geometry(group.getChildAt(i));}
    }
    private EditText field(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){EditText f=field(g.getChildAt(i));if(f!=null)return f;}}return null;}
    private void capture(String page)throws Exception{SystemClock.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),mode+"-"+page+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
        java.util.Set<String> icons=new java.util.HashSet<>();for(ToolRegistry.Tool t:ToolRegistry.TOOLS)check(icons.add(ToolUi.toolIcon(t.id)),"duplicate function icon");
        for(String page:new String[]{"home","tools","settings","parcel","ai-settings","ledger","diagnostics","webdav","vpn","relay"}){
            main(()->activity.show(page));SystemClock.sleep(200);main(()->geometry(activity.getFragmentManager().findFragmentByTag(page).getView()));capture(page);
        }
        main(()->activity.show("ai-settings"));final View[] original=new View[1];final EditText[] draft=new EditText[1];main(()->{original[0]=activity.getFragmentManager().findFragmentByTag("ai-settings").getView();draft[0]=field(original[0]);draft[0].setText("unsaved-layout-draft");activity.show("home");activity.show("ai-settings");check(original[0]==activity.getFragmentManager().findFragmentByTag("ai-settings").getView(),"navigation replaced form");check(draft[0].getText().toString().equals("unsaved-layout-draft"),"lost unsaved draft");});
        if(mode.equals("fold")){
            try(ParcelFileDescriptor command=getUiAutomation().executeShellCommand("wm size 900x1800")){try(FileInputStream input=new FileInputStream(command.getFileDescriptor())){while(input.read()!=-1){}}}SystemClock.sleep(1200);waitForIdleSync();
            main(()->{check(original[0]==activity.getFragmentManager().findFragmentByTag("ai-settings").getView(),"fold recreated form");check(draft[0].getText().toString().equals("unsaved-layout-draft"),"fold discarded draft");geometry(original[0]);});capture("folded-draft");
        }
        result.putString("stream","PASS: "+assertions+" adaptive layout assertions ("+mode+")\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
