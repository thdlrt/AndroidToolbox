package net.lanbridge.android;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Run only against the explicitly selected emulator and debug package. */
public final class ParcelUiInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Throwable failure;
    private int assertions;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private void main(Runnable action){runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError("Parcel UI",failure);waitForIdleSync();}
    private View find(View root,String text){if(root==null)return null;if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private EditText field(View root,String label){if(root instanceof EditText&&label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return (EditText)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){EditText found=field(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private Spinner spinner(View root,String label){if(root instanceof Spinner&&label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return (Spinner)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Spinner found=spinner(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private View parcel(){return activity.getFragmentManager().findFragmentByTag("parcel").getView();}
    private View ai(){return activity.getFragmentManager().findFragmentByTag("ai-settings").getView();}
    private boolean windowText(String text){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void setWindowField(String label,String value){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo> nodes=new java.util.ArrayDeque<>();nodes.add(root);while(!nodes.isEmpty()){android.view.accessibility.AccessibilityNodeInfo node=nodes.remove();if("android.widget.EditText".contentEquals(node.getClassName())&&label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())){Bundle b=new Bundle();b.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,b),"set field");waitForIdleSync();return;}for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);if(child!=null)nodes.add(child);}}throw new AssertionError("Missing field "+label);}
    private void clickWindow(String text){for(int i=0;i<20;i++){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(!bounds.isEmpty()){long now=android.os.SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),true);getUiAutomation().injectInputEvent(android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0),true);android.os.SystemClock.sleep(250);waitForIdleSync();return;}}android.os.SystemClock.sleep(100);}throw new AssertionError("Missing dialog action "+text);}
    private void screenshot(String name)throws Exception{android.os.SystemClock.sleep(250);android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"refined-"+name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();JSONObject old=null;boolean enabled=false;try{
        AiSettings settings=new AiSettings(getTargetContext());old=settings.exportSnapshot();enabled=settings.enabled();settings.importSnapshot(new JSONObject().put("format",AiConfigCodec.FORMAT).put("version",1).put("providers",new org.json.JSONArray()).put("roles",new JSONObject()).put("secrets",new JSONObject()),settings.revision());getTargetContext().getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",false).commit();
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("page","parcel"));
        main(()->{
            check(parcel()!=null,"parcel route exists");check(find(parcel(),"添加取件信息")!=null,"add action exists");check(find(parcel(),"扫描所选日期短信")==null,"imports should be secondary");
            activity.show("ai-settings");
        });
        main(()->{
            check(ai()!=null,"AI settings route exists");check(find(ai(),"启用取件 AI 分析")==null,"no opt-in switch");check(find(ai(),"文本模型")==null,"no separate text model");
            for(String label:new String[]{"供应商","服务名称","API 地址","模型 ID","模型供应商","API Key"})check(find(ai(),label)!=null,"missing control "+label);
            field(ai(),"服务名称").setText("多模态测试");field(ai(),"API 地址").setText("https://vision.fixture.invalid/v1");field(ai(),"API Key").setText("vision-secret");find(ai(),"保存供应商").performClick();
            check(field(ai(),"API Key").getText().length()==0,"key cleared after save");spinner(ai(),"模型供应商").setSelection(1);field(ai(),"模型 ID").setText("vision-fixture");
        });
        main(()->{
            find(ai(),"保存功能模型").performClick();check(find(ai(),"功能模型已保存")!=null,"multimodal role saved");
            try{AiSettings configured=new AiSettings(getTargetContext());ParcelAi.Config text=configured.parcelConfig(false),image=configured.parcelConfig(true);check(text.endpoint.equals(image.endpoint)&&text.model.equals(image.model)&&text.key.equals(image.key),"text and image share one model");check(!configured.profiles().toString().contains("vision-secret"),"safe profiles exclude key");}catch(Exception e){throw new RuntimeException(e);}
            activity.onBackPressed();check(!activity.getFragmentManager().findFragmentByTag("parcel").isHidden(),"back returns to parcel");
            find(parcel(),"添加取件信息").performClick();
        });
        screenshot("add-menu");clickWindow("扫描短信");check(windowText("扫描并分析"),"scan secondary sheet");clickWindow("7 天");check(windowText("起始日期："+java.time.LocalDate.now().minusDays(6)),"date preset uses calendar days");screenshot("scan-dialog");clickWindow("取消");
        main(()->find(parcel(),"添加取件信息").performClick());clickWindow("粘贴短信");check(windowText("分析并添加"),"single explicit paste action");screenshot("paste-dialog");clickWindow("取消");
        check(android.os.Build.VERSION.SDK_INT<33?Intent.ACTION_PICK.equals(ToolUi.photoPicker().getAction()):android.provider.MediaStore.ACTION_PICK_IMAGES.equals(ToolUi.photoPicker().getAction()),"gallery picker, not document browser");
        main(()->{
            activity.getPreferences(0).edit().putBoolean("favorite.vpn",false).commit();activity.refreshFavorites();View rail=activity.getWindow().getDecorView().findViewWithTag("toolbox-navigation");check(find(rail,"回家 VPN").getVisibility()==View.GONE,"nonfavorite rail hidden");activity.getPreferences(0).edit().remove("favorite.vpn").commit();activity.refreshFavorites();
            activity.show("ledger");View ledger=activity.getFragmentManager().findFragmentByTag("ledger").getView();check(find(ledger,"立即同步")==null&&find(ledger,"WebDAV 与备份")==null,"no per-tool sync settings");check(find(ledger,"近3月")!=null&&find(ledger,"半年")!=null,"date shortcuts retained");find(ledger,"新建记账").performClick();
        });
        check(windowText("新增记账"),"ledger editor opens");screenshot("ledger-editor");clickWindow("取消");
        main(()->find(activity.getFragmentManager().findFragmentByTag("ledger").getView(),"新建记账").performClick());
        main(()->{try{LedgerFragment ledger=(LedgerFragment)activity.getFragmentManager().findFragmentByTag("ledger");java.lang.reflect.Field title=LedgerFragment.class.getDeclaredField("title"),amount=LedgerFragment.class.getDeclaredField("amount"),button=LedgerFragment.class.getDeclaredField("commitButton"),draft=LedgerFragment.class.getDeclaredField("draft"),id=LedgerFragment.class.getDeclaredField("editingId");for(java.lang.reflect.Field f:new java.lang.reflect.Field[]{title,amount,button,draft,id})f.setAccessible(true);((EditText)title.get(ledger)).setText("UI 保存验证");((EditText)amount.get(ledger)).setText("18.50");((Button)button.get(ledger)).performClick();check(draft.get(ledger)==null,"successful save closes draft");LedgerStore store=LedgerSync.get(getTargetContext()).store();check(store.entity("entry",(String)id.get(ledger)).getString("title").equals("UI 保存验证"),"entry saved to store");store.patch("entry",(String)id.get(ledger),new JSONObject(),true);LedgerSync.get(getTargetContext()).changed();}catch(Exception e){throw new RuntimeException(e);}});
        check(!windowText("新增记账"),"successful save dismisses editor window");
        main(()->activity.show("ai-settings"));main(()->spinner(ai(),"模型供应商").performClick());screenshot("provider-dropdown");clickWindow("请选择供应商");main(()->check(spinner(ai(),"模型供应商").getSelectedItemPosition()==0,"popup selection not applied"));
        main(()->{activity.show("parcel");find(parcel(),"取件设置").performClick();});check(windowText("打开软件自动读取短信"),"auto-read setting missing");screenshot("auto-settings");setWindowField("过去天数（含今天，1–93）","94");clickWindow("保存");check(windowText("取件设置"),"invalid days dismissed form");setWindowField("过去天数（含今天，1–93）","7");clickWindow("保存");check(ParcelAutoReader.days(getTargetContext())==7,"days not saved");ParcelAutoReader.configure(getTargetContext(),false,3);
        result.putString("stream","PASS: "+assertions+" parcel/AI UI assertions; simplified imports, Material dialogs, shared multimodal role, favorites and ledger controls\n");
        finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{try{if(old!=null)new AiSettings(getTargetContext()).importSnapshot(old,new AiSettings(getTargetContext()).revision());getTargetContext().getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",enabled).commit();}catch(Exception ignored){}}}
}
