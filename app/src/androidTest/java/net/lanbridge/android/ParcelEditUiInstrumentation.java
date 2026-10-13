package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public final class ParcelEditUiInstrumentation extends Instrumentation {
    private MainActivity activity;private ParcelFragment fragment;private Throwable failure;private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private void main(Runnable action){runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);waitForIdleSync();}
    private static Field field(String name)throws Exception{Field field=ParcelFragment.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(String label){for(int attempt=0;attempt<30;attempt++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){if(!label.contentEquals(node.getText()))continue;android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(bounds.isEmpty())continue;long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();SystemClock.sleep(150);waitForIdleSync();return;}SystemClock.sleep(100);}throw new AssertionError("Missing action "+label);}
    private void set(String label,String value){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);while(!queue.isEmpty()){AccessibilityNodeInfo node=queue.remove();if("android.widget.EditText".contentEquals(node.getClassName())&&label.contentEquals(node.getContentDescription())){Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments),"Cannot edit "+label);waitForIdleSync();return;}for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}}throw new AssertionError("Missing field "+label);}
    private void menu(){main(()->find(fragment.getView(),"快递操作").performClick());}
    private void idle(ExecutorService worker)throws Exception{worker.submit(()->{}).get(10,TimeUnit.SECONDS);waitForIdleSync();worker.submit(()->{}).get(10,TimeUnit.SECONDS);waitForIdleSync();}
    private void capture(String name)throws Exception{SystemClock.sleep(350);android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"parcel-edit-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();String db="parcel-edit-ui-"+UUID.randomUUID()+".db";ParcelStore store=null;SharedPreferences prefs=ParcelAutoReader.prefs(context);boolean auto=prefs.getBoolean("enabled",false);
        try{
            check(context.getPackageName().endsWith(".debug"),"Debug required");prefs.edit().putBoolean("enabled",false).commit();store=new ParcelStore(context,db);
            ParcelSms.Message source=new ParcelSms.Message("ui-edit",System.currentTimeMillis(),"合成验收","取件码 ORIG-123，运单 TRACK1，北门A柜，明晚截止");store.ingest(List.of(source),List.of(new ParcelAi.Item(List.of(source.id),"ORIG-123","TRACK1","丰巢","北门","A柜",ParcelAi.PENDING,"明晚")));String id=store.list(false,"newest").get(0).id;
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));main(()->activity.show("parcel"));fragment=(ParcelFragment)activity.getFragmentManager().findFragmentByTag("parcel");ExecutorService worker=(ExecutorService)field("worker").get(fragment);idle(worker);ParcelStore fixture=store;
            worker.submit(()->{try{((ParcelStore)field("store").get(fragment)).close();field("store").set(fragment,fixture);}catch(Exception e){throw new RuntimeException(e);}}).get(10,TimeUnit.SECONDS);main(()->{try{Method refresh=ParcelFragment.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(fragment);}catch(Exception e){throw new RuntimeException(e);}});idle(worker);
            menu();click("编辑信息");set("取件码","DISCARD");click("取消");check(store.list(false,"address").get(0).code.equals("ORIG-123"),"Cancel changed record");
            menu();click("编辑信息");set("取件码",String.join("",Collections.nCopies(129,"x")));click("保存");check(store.list(false,"address").get(0).code.equals("ORIG-123"),"Invalid edit saved");
            set("取件码","EDIT-999");set("运单号","TRACK2");set("快递公司","中通");set("地点","南门");set("柜位","室内9号丰巢柜");set("截止提示","后天");capture("form");click("保存");idle(worker);
            ParcelStore.Parcel edited=store.list(false,"address").get(0);check(edited.id.equals(id)&&edited.code.equals("EDIT-999")&&edited.location.equals("南门")&&edited.locker.equals("室内9号丰巢柜")&&edited.carrier.equals("中通")&&edited.trackingNumber.equals("TRACK2")&&edited.deadline.equals("后天"),"UI did not save fields");check(find(fragment.getView(),"取件码：EDIT-999")!=null&&find(fragment.getView(),"南门")!=null,"List/group did not refresh");
            menu();click("查看来源");check(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(source.body).isEmpty(),"Original source lost");click("关闭");capture("saved");
            main(()->find(fragment.getView(),"标记已取").performClick());idle(worker);main(()->{try{((Spinner)field("filter").get(fragment)).setSelection(1);}catch(Exception e){throw new RuntimeException(e);}});idle(worker);menu();click("编辑信息");set("快递公司","韵达");click("保存");idle(worker);ParcelStore.Parcel archived=store.list(true,"address").get(0);check(archived.id.equals(id)&&archived.collected&&archived.manualCollected&&archived.carrier.equals("韵达")&&archived.sources.get(0).body.equals(source.body),"Archived edit changed state/source");capture("archived");
            result.putString("stream","PASS: "+checks+" manual edit UI checks: menu, cancel, invalid fields, save, regroup, original source and archived edit\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally{failure=null;if(activity!=null)main(activity::finish);if(store!=null)store.close();context.deleteDatabase(db);prefs.edit().putBoolean("enabled",auto).commit();}
    }
}
