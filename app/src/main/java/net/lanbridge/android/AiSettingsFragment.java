package net.lanbridge.android;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AiSettingsFragment extends ToolFragment {
    private EditText endpoint,name,key,vision;
    private Spinner providerSelect,kindSelect,visionProvider;
    private TextView status,keyHint;
    private Button save,saveProvider,removeProvider;private String loadedRevision="";
    private org.json.JSONArray providers=new org.json.JSONArray();
    private final java.util.List<String> providerIds=new java.util.ArrayList<>(),roleIds=new java.util.ArrayList<>();
    private boolean busy,loading;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override protected View createContent(Bundle state){
        ScrollView scroll=new ScrollView(context());LinearLayout body=ToolUi.column(context());int pad=ToolUi.dp(context(),20);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        body.addView(ToolUi.header(context(),"AI 设置",this::closePage));
        LinearLayout root=body;ToolUi.Grid sections=new ToolUi.Grid(context(),300,2,16);root.addView(sections);
        body=ToolUi.card(context());sections.addView(body);title(body,"供应商设置");providerSelect=selector(body,"供应商");
        providerSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!loading)loadProvider();}public void onNothingSelected(AdapterView<?> p){}});
        name=ToolUi.field(context(),body,"服务名称","",false);kindSelect=selector(body,"接口类型");kindSelect.setAdapter(adapter(java.util.Arrays.asList("OpenAI 兼容","DashScope","Gemini 原生")));
        endpoint=ToolUi.field(context(),body,"API 地址","",false);endpoint.setHint("https://api.example.com/v1");key=ToolUi.field(context(),body,"API Key","",true);keyHint=ToolUi.text(context(),"",13,ToolUi.MUTED);body.addView(keyHint);
        saveProvider=ToolUi.button(context(),"保存供应商",true,this::saveProvider);body.addView(saveProvider);
        removeProvider=ToolUi.button(context(),"移除供应商",false,this::removeProvider);body.addView(removeProvider);
        body=ToolUi.card(context());sections.addView(body,0);title(body,"多模态模型");
        visionProvider=selector(body,"模型供应商");vision=ToolUi.field(context(),body,"模型 ID","",false);vision.setHint("支持文字和图片输入的模型 ID");
        body.addView(ToolUi.text(context(),"短信和图片使用同一个模型。添加内容时会发送给所选供应商分析。",13,ToolUi.MUTED));
        save=ToolUi.button(context(),"保存功能模型",true,this::save);body.addView(save);
        status=ToolUi.text(context(),"",14,ToolUi.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);load(true,"");return scroll;
    }
    private void title(LinearLayout body,String label){TextView v=ToolUi.text(context(),label,20,ToolUi.INK);v.setTypeface(null,android.graphics.Typeface.BOLD);v.setPadding(0,0,0,ToolUi.dp(context(),12));body.addView(v);}
    private Spinner selector(LinearLayout body,String label){ToolUi.space(body,ToolUi.text(context(),label,13,ToolUi.MUTED));Spinner v=ToolUi.spinner(context());v.setContentDescription(label);body.addView(v,new LinearLayout.LayoutParams(-1,ToolUi.dp(context(),52)));return v;}
    private ArrayAdapter<String> adapter(java.util.List<String> values){ArrayAdapter<String> a=ToolUi.choices(context(),values);return a;}
    private String selected(Spinner spinner,java.util.List<String> ids){int p=spinner.getSelectedItemPosition();return p>=0&&p<ids.size()?ids.get(p):"";}
    private void select(Spinner spinner,java.util.List<String> ids,String id){spinner.setSelection(Math.max(0,ids.indexOf(id)));}
    private void load(boolean roles,String chosen){try{
        String visionId=selected(visionProvider,roleIds);JSONObject value=new AiSettings(context()).profiles();providers=value.getJSONArray("providers");loading=true;
        providerIds.clear();roleIds.clear();providerIds.add("");roleIds.add("");java.util.List<String> names=new java.util.ArrayList<>(),compatible=new java.util.ArrayList<>();names.add("新增供应商");compatible.add("请选择供应商");
        for(int i=0;i<providers.length();i++){JSONObject p=providers.getJSONObject(i);String id=p.getString("id"),label=p.optString("name",id);providerIds.add(id);names.add(label);if(!"gemini".equals(p.optString("kind"))){roleIds.add(id);compatible.add(label);}}
        providerSelect.setAdapter(adapter(names));select(providerSelect,providerIds,chosen);visionProvider.setAdapter(adapter(compatible));
        if(roles){JSONObject r=value.getJSONObject("roles"),image=r.optJSONObject("parcel_vision");if(image==null)image=r.optJSONObject("vision");if(image==null)image=r.optJSONObject("parcel");if(image==null)image=r.optJSONObject("chat");visionId=image==null?"":image.optString("provider_id");vision.setText(image==null?"":image.optString("model"));}
        select(visionProvider,roleIds,visionId);loading=false;loadProvider();loadedRevision=new AiSettings(context()).revision();
    }catch(Exception e){loading=false;showError(e);}}
    private void loadProvider(){String id=selected(providerSelect,providerIds);JSONObject p=new JSONObject();for(int i=0;i<providers.length();i++){JSONObject item=providers.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))p=item;}name.setText(p.optString("name"));endpoint.setText(p.optString("base_url"));key.setText("");kindSelect.setSelection("dashscope".equals(p.optString("kind"))?1:"gemini".equals(p.optString("kind"))?2:0);keyHint.setText(p.optBoolean("has_key")?"密钥已保存，留空保留。更改地址需重新填写密钥。":"密钥仅在本机加密保存。");removeProvider.setEnabled(!busy&&!id.isEmpty());}
    private void saveProvider(){if(busy)return;try{String id=new AiSettings(context()).saveProvider(selected(providerSelect,providerIds),name.getText().toString(),new String[]{"openai","dashscope","gemini"}[kindSelect.getSelectedItemPosition()],endpoint.getText().toString(),key.getText().toString());load(false,id);status.setTextColor(ToolUi.MUTED);status.setText("供应商已保存");}catch(Exception e){showError(e);}}
    private void removeProvider(){String id=selected(providerSelect,providerIds);if(busy||id.isEmpty())return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("移除供应商？").setMessage("使用中的供应商需要先更改功能模型配置。").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{try{new AiSettings(context()).removeProvider(id);load(false,"");status.setText("供应商已移除");}catch(Exception e){showError(e);}}).show();}
    private void setBusy(boolean value,String message){busy=value;for(View v:new View[]{save,saveProvider,endpoint,name,key,vision,providerSelect,kindSelect,visionProvider})v.setEnabled(!value);removeProvider.setEnabled(!value&&!selected(providerSelect,providerIds).isEmpty());status.setTextColor(ToolUi.MUTED);status.setText(message);}
    private void save(){if(busy)return;try{new AiSettings(context()).saveMultimodal(selected(visionProvider,roleIds),vision.getText().toString());load(true,selected(providerSelect,providerIds));status.setTextColor(ToolUi.MUTED);status.setText("功能模型已保存");}catch(Exception e){showError(e);}}
    private void showError(Exception e){if(status!=null){status.setText(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());status.setTextColor(0xffa33d32);}}
    private void ui(Runnable action){runOnUiThread(()->{if(isAdded()&&getView()!=null)action.run();});}
    @Override protected void onPageResume(){if(!loadedRevision.equals(new AiSettings(context()).revision()))load(true,selected(providerSelect,providerIds));}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
