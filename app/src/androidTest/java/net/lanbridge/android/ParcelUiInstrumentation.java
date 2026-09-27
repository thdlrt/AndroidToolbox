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
    @Override public void onStart(){Bundle result=new Bundle();JSONObject old=null;boolean enabled=false;try{
        AiSettings settings=new AiSettings(getTargetContext());old=settings.exportSnapshot();enabled=settings.enabled();settings.importSnapshot(new JSONObject().put("format",AiConfigCodec.FORMAT).put("version",1).put("providers",new org.json.JSONArray()).put("roles",new JSONObject()).put("secrets",new JSONObject()),settings.revision());getTargetContext().getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",false).commit();
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("page","parcel"));
        main(()->{
            check(parcel()!=null,"parcel route exists");
            for(String label:new String[]{"今天","近 2 天","近 3 天","近 7 天","扫描所选日期短信","粘贴短信","选择图片","AI 设置"})check(find(parcel(),label)!=null,"missing control "+label);
            find(parcel(),"近 7 天").performClick();
            String from="起始日期："+java.time.LocalDate.now().minusDays(6);check(find(parcel(),from)!=null,"date preset uses calendar days");
            find(parcel(),"扫描所选日期短信").performClick();
            check(find(parcel(),"请先在 AI 设置中启用取件 AI 分析")!=null,"scan requires opt-in before reading or sending");
            find(parcel(),"AI 设置").performClick();
        });
        main(()->{
            check(ai()!=null,"AI settings route exists");
            check(!((CheckBox)find(ai(),"启用取件 AI 分析")).isChecked(),"opt-in remains disabled");
            for(String label:new String[]{"供应商","服务名称","API 地址","文本模型","图片模型","API Key"})check(find(ai(),label)!=null,"missing AI control "+label);
            check(find(ai(),"上传已保存的 AI 配置")==null&&find(ai(),"下载 AI 配置")==null,"Independent AI backup panel remains");field(ai(),"服务名称").setText("文本供应商");field(ai(),"API 地址").setText("https://text.fixture.invalid/v1");field(ai(),"API Key").setText("text-secret");
            find(ai(),"保存供应商").performClick();check(find(ai(),"供应商已保存")!=null,"provider saved locally");check(field(ai(),"API Key").getText().length()==0,"key cleared after save");
            spinner(ai(),"供应商").setSelection(0);
        });
        main(()->{
            field(ai(),"服务名称").setText("图片供应商");field(ai(),"API 地址").setText("https://vision.fixture.invalid/v1");field(ai(),"API Key").setText("vision-secret");find(ai(),"保存供应商").performClick();
            spinner(ai(),"取件文本供应商").setSelection(1);spinner(ai(),"取件图片供应商").setSelection(2);
            field(ai(),"文本模型").setText("text-fixture");field(ai(),"图片模型").setText("vision-fixture");find(ai(),"保存功能模型").performClick();
            check(find(ai(),"功能模型已保存")!=null,"independent roles saved");check(field(ai(),"图片模型").getText().toString().equals("vision-fixture"),"vision model survives reload");
            try{AiSettings configured=new AiSettings(getTargetContext());ParcelAi.Config text=configured.parcelConfig(false),image=configured.parcelConfig(true);check(text.endpoint.equals("https://text.fixture.invalid/v1"),"text provider routed");check(image.endpoint.equals("https://vision.fixture.invalid/v1"),"image provider routed");check(text.key.equals("text-secret")&&image.key.equals("vision-secret"),"role credentials isolated");check(!configured.enabled(),"save does not enable analysis without opt-in");check(!configured.profiles().toString().contains("text-secret"),"safe profile excludes secret");}catch(Exception e){throw new RuntimeException(e);}
            ((CheckBox)find(ai(),"启用取件 AI 分析")).setChecked(true);find(ai(),"保存功能模型").performClick();check(new AiSettings(getTargetContext()).enabled(),"explicit opt-in saved");
            field(ai(),"文本模型").setText("");((CheckBox)find(ai(),"启用取件 AI 分析")).setChecked(false);check(!new AiSettings(getTargetContext()).enabled(),"disable works immediately despite incomplete form");
            activity.onBackPressed();
            check(!activity.getFragmentManager().findFragmentByTag("parcel").isHidden(),"back returns to originating parcel page");activity.show("webdav");View backup=activity.getFragmentManager().findFragmentByTag("webdav").getView();check(find(backup,"备份当前配置")!=null&&find(backup,"查看配置备份")!=null,"Unified configuration backup controls missing");
        });
        result.putString("stream","PASS: "+assertions+" parcel/AI UI assertions; explicit opt-in, date range, built-in config, independent providers, safe secret handling and back navigation\n");
        finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{try{if(old!=null)new AiSettings(getTargetContext()).importSnapshot(old,new AiSettings(getTargetContext()).revision());getTargetContext().getSharedPreferences("parcel-local",0).edit().putBoolean("ai_enabled",enabled).commit();}catch(Exception ignored){}}}
}
