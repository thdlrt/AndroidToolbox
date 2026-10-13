package net.lanbridge.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Explicit emulator/debug acceptance with a local mock endpoint and uniquely identified fixture rows. */
public final class ClipboardHomeInstrumentation extends Instrumentation {
    private Bundle args;private MainActivity activity;private Throwable failure;private int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private void main(Runnable action){runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);waitForIdleSync();}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private ToolUi.Grid grid(View root){if(root instanceof ToolUi.Grid)return (ToolUi.Grid)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){ToolUi.Grid found=grid(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    private static Field field(String name)throws Exception{Field field=ParcelFragment.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private void textFits(View root){if(root instanceof TextView){TextView t=(TextView)root;if(t.getLayout()!=null){check(t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom()+2,"text height clipped: "+t.getText());for(int i=0;i<t.getLineCount();i++)check(t.getLayout().getLineWidth(i)<=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+2,"text width clipped: "+t.getText());}}if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)textFits(((ViewGroup)root).getChildAt(i));}
    private void capture(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"clipboard-home-"+name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void clickClipboard(){
        View parcel=activity.getFragmentManager().findFragmentByTag("parcel").getView();main(()->find(parcel,"添加取件信息").performClick());
        for(int attempt=0;attempt<30;attempt++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("从剪贴板粘贴")){if(!"从剪贴板粘贴".contentEquals(node.getText()))continue;while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}}SystemClock.sleep(100);}
        throw new AssertionError("Missing clipboard menu action");
    }
    private void feedback(String message){View parcel=activity.getFragmentManager().findFragmentByTag("parcel").getView();main(()->check(find(parcel,message)!=null,"Missing feedback: "+message));}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();AiSettings ai=new AiSettings(context);JSONObject originalAi=null;ParcelStore fixture=null;String code=args.getString("code","CLIP-606"),body="CLIPBOARD_FIXTURE 取件码 "+code+"，请到北门驿站A柜取件。";SharedPreferences auto=ParcelAutoReader.prefs(context);boolean autoEnabled=auto.getBoolean("enabled",false);boolean aiEnabled=ai.enabled();Map<String,Object> favorites=new HashMap<>();ClipData[] originalClip={null};boolean[] clipSaved={false};
        try{
            check(context.getPackageName().endsWith(".debug"),"Debug target required");auto.edit().putBoolean("enabled",false).commit();
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{SharedPreferences prefs=activity.getPreferences(0);SharedPreferences.Editor edit=prefs.edit();for(ToolRegistry.Tool tool:ToolRegistry.TOOLS){String key="favorite."+tool.id;favorites.put(key,prefs.getAll().get(key));edit.putBoolean(key,true);}edit.commit();activity.show("home");});
            main(()->{ToolUi.Grid grid=grid(activity.getFragmentManager().findFragmentByTag("home").getView());check(grid!=null&&grid.getChildCount()==ToolRegistry.TOOLS.size(),"Home tools missing");int columns=Integer.parseInt(args.getString("columns","2"));View first=grid.getChildAt(0);check(grid.getChildAt(1).getTop()==first.getTop()&&grid.getChildAt(1).getLeft()>first.getLeft(),"Home not two columns on phone");check(grid.getChildAt(columns).getTop()>first.getTop(),"Unexpected grid column count");for(int i=0;i<grid.getChildCount();i++){View card=grid.getChildAt(i);check(card.getWidth()>=ToolUi.dp(context,48)&&card.getRight()<=grid.getWidth(),"Home card outside screen");textFits(card);}});
            capture(args.getString("mode","phone"));
            if("true".equals(args.getString("clipboard","false"))){
                originalAi=ai.exportSnapshot();String provider=ai.saveProvider("clipboard-fixture","Clipboard fixture","openai",args.getString("endpoint"),"fixture-key");ai.saveMultimodal(provider,"clipboard-fixture-model");
                main(()->activity.show("parcel"));ParcelFragment fragment=(ParcelFragment)activity.getFragmentManager().findFragmentByTag("parcel");ExecutorService worker=(ExecutorService)field("worker").get(fragment);worker.submit(()->{}).get(10,TimeUnit.SECONDS);fixture=new ParcelStore(context);
                ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                main(()->{originalClip[0]=clipboard.getPrimaryClip();clipSaved[0]=true;clipboard.clearPrimaryClip();});clickClipboard();feedback("剪贴板没有文字，请先复制短信内容");
                main(()->clipboard.setPrimaryClip(ClipData.newPlainText("fixture"," \n ")));clickClipboard();feedback("剪贴板没有文字，请先复制短信内容");
                main(()->clipboard.setPrimaryClip(ClipData.newRawUri("fixture",android.net.Uri.parse("content://fixture.invalid/image"))));clickClipboard();feedback("剪贴板内容不是文字，请先复制短信内容");
                main(()->clipboard.setPrimaryClip(ClipData.newPlainText("fixture",String.join("",Collections.nCopies(200001,"x")))));clickClipboard();feedback("剪贴板内容过长，请分批复制（最多 200000 字符）");
                main(()->clipboard.setPrimaryClip(ClipData.newPlainText("fixture","  "+body+" \n")));clickClipboard();
                main(()->{try{check((Boolean)field("busy").get(fragment),"Clipboard did not start analysis immediately");check(!((View)field("add").get(fragment)).isEnabled(),"Import control stays active during job");Method paste=ParcelFragment.class.getDeclaredMethod("paste");paste.setAccessible(true);paste.invoke(fragment);}catch(Exception e){throw new RuntimeException(e);}});
                long until=System.currentTimeMillis()+20000;List<ParcelStore.Parcel> parcels=new ArrayList<>();while(System.currentTimeMillis()<until){parcels.clear();for(ParcelStore.Parcel p:fixture.list(false,"address"))if(p.code.equals(code))parcels.add(p);if(!parcels.isEmpty())break;SystemClock.sleep(50);}
                check(parcels.size()==1,"Clipboard did not generate parcel automatically");check(parcels.get(0).sources.size()==1&&parcels.get(0).sources.get(0).sender.equals("剪贴板粘贴"),"Clipboard source missing");check(parcels.get(0).sources.get(0).body.equals(body),"Clipboard body changed");
                worker.submit(()->{}).get(10,TimeUnit.SECONDS);waitForIdleSync();capture("imported");
            }
            result.putString("stream","PASS: "+checks+" clipboard/home checks ("+args.getString("mode","phone")+"): grid geometry and text; explicit clipboard action, invalid input, direct AI import and busy guard\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally{try{failure=null;if(activity!=null){main(()->{ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);if(clipSaved[0]){if(originalClip[0]==null)clipboard.clearPrimaryClip();else clipboard.setPrimaryClip(originalClip[0]);}SharedPreferences.Editor edit=activity.getPreferences(0).edit();for(Map.Entry<String,Object> entry:favorites.entrySet()){if(entry.getValue()==null)edit.remove(entry.getKey());else edit.putBoolean(entry.getKey(),(Boolean)entry.getValue());}edit.commit();activity.finish();});}if(fixture!=null){android.database.sqlite.SQLiteDatabase database=fixture.getWritableDatabase();database.beginTransaction();try{for(ParcelStore.Parcel p:fixture.list(false,"address"))if(p.code.equals(code)&&p.sources.size()==1&&p.sources.get(0).body.equals(body)){database.delete("parcels","id=?",new String[]{p.id});database.delete("processed","fingerprint=?",new String[]{p.sources.get(0).fingerprint});database.delete("sources","fingerprint=?",new String[]{p.sources.get(0).fingerprint});}database.setTransactionSuccessful();}finally{database.endTransaction();fixture.close();}}if(originalAi!=null)ai.importSnapshot(originalAi,ai.revision());context.getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",aiEnabled).commit();auto.edit().putBoolean("enabled",autoEnabled).commit();}catch(Exception e){android.util.Log.e("ClipboardHomeTest","Fixture cleanup failed",e);}}
    }
}
