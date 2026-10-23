package net.lanbridge.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DiagnosticsFragment extends ToolFragment {
    private static final int OK=0xff127a4b,WARN=0xffb54708,ERROR=0xffb42318;
    private EditText target,port,timeout,attempts;
    private TextView output;
    private ScrollView scroll;
    private LinearLayout resultPanel,selfResult;
    private Button selfRun,run;
    private LedgerSync sync;
    private JSONObject selected,lastReport;
    private boolean selfMode;

    void inspect(String address){selected=null;selfMode=false;target.setText(address);port.setText("");attempts.setText("1");run();}

    @Override protected View createContent(Bundle state){
        sync=LedgerSync.get(context());
        scroll=new ScrollView(context());
        LinearLayout page=ToolUi.column(context());
        page.setPadding(ToolUi.dp(context(),20),ToolUi.dp(context(),16),ToolUi.dp(context(),20),ToolUi.dp(context(),24));
        scroll.addView(page);
        LinearLayout heading=ToolUi.header(context(),"网络诊断",this::closePage);
        heading.addView(ToolUi.iconButton(context(),"folder","诊断配置",this::profileActions));
        page.addView(heading);
        ToolUi.Grid sections=new ToolUi.Grid(context(),300,2,16);
        page.addView(sections);
        sections.addView(selfCard());
        sections.addView(detailCard());
        resultPanel=resultCard();
        LinearLayout.LayoutParams resultParams=new LinearLayout.LayoutParams(-1,-2);
        resultParams.topMargin=ToolUi.dp(context(),16);
        page.addView(resultPanel,resultParams);
        loadLast();
        return scroll;
    }

    private View selfCard(){
        LinearLayout card=ToolUi.card(context());
        TextView title=ToolUi.text(context(),"一键自检",20,ToolUi.INK);
        title.setTypeface(null,Typeface.BOLD);
        card.addView(title);
        TextView description=ToolUi.text(context(),"自动检查设备联网、DNS 解析、IP 直连和网页访问，不需要填写地址。",14,ToolUi.MUTED);
        description.setLineSpacing(ToolUi.dp(context(),3),1);
        ToolUi.space(card,description);
        selfRun=ToolUi.button(context(),"开始自检",true,this::runSelfCheck);
        card.addView(selfRun);
        return card;
    }

    private View detailCard(){
        LinearLayout card=ToolUi.card(context());
        TextView title=ToolUi.text(context(),"指定地址诊断",20,ToolUi.INK);
        title.setTypeface(null,Typeface.BOLD);
        card.addView(title);
        TextView description=ToolUi.text(context(),"检查 DNS、TCP、TLS 和 HTTP。配置支持跨端同步，报告只保存在本机。",14,ToolUi.MUTED);
        description.setLineSpacing(ToolUi.dp(context(),3),1);
        ToolUi.space(card,description);
        target=ToolUi.field(context(),card,"主机 / HTTP(S) URL","",false);
        LinearLayout advanced=ToolUi.column(context());
        advanced.setVisibility(View.GONE);
        card.addView(ToolUi.button(context(),"诊断参数",false,()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));
        card.addView(advanced);
        port=ToolUi.field(context(),advanced,"端口（可选）","",false);
        timeout=ToolUi.field(context(),advanced,"超时（毫秒）","5000",false);
        attempts=ToolUi.field(context(),advanced,"TCP 尝试次数","3",false);
        run=ToolUi.button(context(),"开始诊断",false,this::run);
        card.addView(run);
        return card;
    }

    private LinearLayout resultCard(){
        LinearLayout card=ToolUi.card(context());
        LinearLayout heading=new LinearLayout(context());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(ToolUi.text(context(),"检查结果",20,ToolUi.INK),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(ToolUi.iconButton(context(),"copy","复制报告",this::copyReport));
        card.addView(heading);
        selfResult=ToolUi.column(context());
        selfResult.setVisibility(View.GONE);
        card.addView(selfResult);
        output=ToolUi.text(context(),"点「开始自检」快速检查网络，或填写地址做详细诊断。",14,ToolUi.MUTED);
        output.setLineSpacing(ToolUi.dp(context(),5),1);
        output.setTextIsSelectable(true);
        ToolUi.space(card,output);
        return card;
    }

    private void loadLast(){
        try{
            File last=new File(context().getFilesDir(),"diagnostic-last.json");
            if(!last.isFile())return;
            JSONObject report=LedgerStore.read(last);
            if("self-check".equals(report.optString("mode")))showSelf(report);
            else show(report);
        }catch(Exception ignored){}
    }

    private void runSelfCheck(){
        if(selfRun==null)return;
        buttons(false);
        selfMode=true;
        output.setVisibility(View.GONE);
        selfResult.setVisibility(View.VISIBLE);
        selfResult.removeAllViews();
        TextView progress=ToolUi.text(context(),"正在自检，通常几秒，最长约 15 秒…",14,ToolUi.MUTED);
        progress.setPadding(0,ToolUi.dp(context(),12),0,0);
        selfResult.addView(progress);
        revealResult();
        Context app=context().getApplicationContext();
        new Thread(()->{
            try{
                JSONObject report=NetworkDiagnostics.selfCheck(app,2500);
                try{LedgerStore.write(new File(app.getFilesDir(),"diagnostic-last.json"),report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
                ui(()->{showSelf(report);buttons(true);revealResult();});
            }catch(Exception e){
                ui(()->{
                    selfResult.removeAllViews();
                    TextView error=ToolUi.text(context(),"自检失败："+message(e),14,ERROR);
                    error.setPadding(0,ToolUi.dp(context(),12),0,0);
                    selfResult.addView(error);
                    buttons(true);
                });
            }
        },"network-self-check").start();
    }

    private void showSelf(JSONObject report){
        lastReport=report;
        selfMode=true;
        output.setVisibility(View.GONE);
        selfResult.setVisibility(View.VISIBLE);
        selfResult.removeAllViews();
        JSONArray steps=report.optJSONArray("steps");
        int errors=0,warns=0;
        if(steps!=null)for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step==null)continue;
            String status=step.optString("status");
            if("error".equals(status))errors++;
            else if("warn".equals(status))warns++;
        }
        int color=errors>0?ERROR:warns>0?WARN:OK;
        int tint=errors>0?0xfffdecea:warns>0?0xfffff4e5:0xffeaf7f0;
        TextView summary=ToolUi.text(context(),report.optString("summary","自检完成"),17,color);
        summary.setTypeface(null,Typeface.BOLD);
        summary.setPadding(ToolUi.dp(context(),12),ToolUi.dp(context(),12),ToolUi.dp(context(),12),ToolUi.dp(context(),12));
        summary.setBackground(ToolUi.box(context(),tint,12));
        LinearLayout.LayoutParams summaryParams=new LinearLayout.LayoutParams(-1,-2);
        summaryParams.topMargin=ToolUi.dp(context(),8);
        selfResult.addView(summary,summaryParams);
        if(steps!=null)for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step!=null)selfResult.addView(selfRow(step));
        }
    }

    private View selfRow(JSONObject step){
        LinearLayout row=new LinearLayout(context());
        row.setGravity(Gravity.TOP);
        row.setPadding(0,ToolUi.dp(context(),14),0,0);
        String status=step.optString("status","skipped");
        int color=statusColor(status);
        TextView mark=ToolUi.text(context(),statusMark(status),18,color);
        mark.setTypeface(null,Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark,new LinearLayout.LayoutParams(ToolUi.dp(context(),26),ToolUi.dp(context(),26)));
        LinearLayout info=ToolUi.column(context());
        LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(0,-2,1);
        infoParams.leftMargin=ToolUi.dp(context(),8);
        row.addView(info,infoParams);
        TextView title=ToolUi.text(context(),step.optString("title",step.optString("name")),15,ToolUi.INK);
        title.setTypeface(null,Typeface.BOLD);
        info.addView(title);
        TextView detail=ToolUi.text(context(),step.optString("detail",""),13,ToolUi.MUTED);
        detail.setLineSpacing(ToolUi.dp(context(),3),1);
        info.addView(detail);
        String advice=step.optString("advice","");
        if(!advice.isEmpty()){
            TextView hint=ToolUi.text(context(),advice,12,color);
            hint.setLineSpacing(ToolUi.dp(context(),2),1);
            hint.setPadding(0,ToolUi.dp(context(),3),0,0);
            info.addView(hint);
        }
        return row;
    }

    private void run(){
        try{
            JSONObject profile=value();
            buttons(false);
            selfMode=false;
            selfResult.setVisibility(View.GONE);
            output.setVisibility(View.VISIBLE);
            output.setText("正在检查…");
            revealResult();
            Context app=context().getApplicationContext();
            new Thread(()->{
                try{
                    JSONObject report=NetworkDiagnostics.run(profile.getString("target"),profile.optInt("port",0),profile.getInt("timeout_ms"),profile.getInt("attempts"));
                    LedgerStore.write(new File(app.getFilesDir(),"diagnostic-last.json"),report.toString().getBytes(StandardCharsets.UTF_8));
                    ui(()->{show(report);buttons(true);revealResult();});
                }catch(Exception e){ui(()->{error(e);buttons(true);});}
            },"network-diagnostics").start();
        }catch(Exception e){error(e);}
    }

    private void show(JSONObject report){
        lastReport=report;
        selfMode=false;
        selfResult.setVisibility(View.GONE);
        output.setVisibility(View.VISIBLE);
        try{
            StringBuilder text=new StringBuilder(report.optString("target")+"\n"+report.optString("started_at")+"\n\n");
            JSONArray steps=report.optJSONArray("steps");
            if(steps!=null)for(int i=0;i<steps.length();i++){
                JSONObject step=steps.getJSONObject(i);
                text.append(step.optString("title",step.optString("name").toUpperCase(Locale.ROOT))).append(" · ").append(statusLabel(step.optString("status"))).append(" · ").append(step.optLong("duration_ms")).append(" ms\n").append(step.optString("detail")).append("\n\n");
            }
            text.append(report.optString("summary"));
            output.setText(text);
        }catch(Exception e){error(e);}
    }

    private void copyReport(){
        String text=selfMode&&lastReport!=null?selfText():output.getText().toString();
        ((ClipboardManager)context().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("网络诊断",text));
        Toast.makeText(context(),"已复制",0).show();
    }

    private String selfText(){
        StringBuilder text=new StringBuilder("网络诊断 · 一键自检\n").append(lastReport.optString("started_at")).append("\n\n");
        JSONArray steps=lastReport.optJSONArray("steps");
        if(steps!=null)for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step==null)continue;
            text.append(step.optString("title",step.optString("name"))).append(" · ").append(statusLabel(step.optString("status"))).append(" · ").append(step.optLong("duration_ms")).append(" ms\n").append(step.optString("detail")).append("\n");
            String advice=step.optString("advice","");
            if(!advice.isEmpty())text.append(advice).append("\n");
            text.append("\n");
        }
        return text.append(lastReport.optString("summary")).toString();
    }

    private void revealResult(){
        if(scroll==null||resultPanel==null)return;
        scroll.postDelayed(()->{if(!isAdded())return;scroll.smoothScrollTo(0,Math.max(0,resultPanel.getTop()-ToolUi.dp(context(),8)));},80);
    }

    private void buttons(boolean enabled){
        if(selfRun!=null)selfRun.setEnabled(enabled);
        if(run!=null)run.setEnabled(enabled);
    }

    private void ui(Runnable action){
        if(!isAdded())return;
        runOnUiThread(()->{if(isAdded())action.run();});
    }

    private static String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static String statusMark(String status){switch(status){case "ok":return "✓";case "warn":return "!";case "error":return "×";default:return "–";}}
    private static int statusColor(String status){switch(status){case "ok":return OK;case "warn":return WARN;case "error":return ERROR;default:return ToolUi.MUTED;}}
    private static String statusLabel(String status){switch(status){case "ok":return "正常";case "warn":return "注意";case "error":return "异常";case "skipped":return "跳过";default:return status;}}

    private void profileActions(){
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("诊断配置").setItems(selected==null?new String[]{"读取共享配置","保存当前配置","使用 WebDAV 地址"}:new String[]{"读取共享配置","保存当前配置","使用 WebDAV 地址","新建配置","处理配置冲突","删除当前配置"},(dialog,index)->{switch(index){case 0:profiles();break;case 1:save();break;case 2:target.setText(WebDavSettings.store(context()).origin());break;case 3:selected=null;target.setText("");port.setText("");timeout.setText("5000");attempts.setText("3");break;case 4:LedgerConflicts.show(context(),sync,"network_profile",selected.optString("id"),()->{try{applyProfile(sync.store().entity("network_profile",selected.getString("id")));}catch(Exception e){error(e);}});break;case 5:new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("删除共享配置？").setMessage("删除会同步到其他设备，历史报告保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{sync.store().patch("network_profile",selected.getString("id"),new JSONObject(),true);sync.changed();selected=null;}catch(Exception e){error(e);}}).show();break;}}).show();
    }

    private void error(Exception e){Toast.makeText(context(),e.getMessage(),1).show();}

    private JSONObject value()throws Exception{
        String url=target.getText().toString().trim();
        NetworkDiagnostics.target(url);
        String p=port.getText().toString().trim();
        int t=Integer.parseInt(timeout.getText().toString().trim()),a=Integer.parseInt(attempts.getText().toString().trim()),n=p.isEmpty()?0:Integer.parseInt(p);
        if(t<1000||t>15000||a<1||a>5||n<0||n>65535)throw new IllegalArgumentException("请检查端口、超时和次数范围");
        return new JSONObject().put("target",url).put("port",n==0?JSONObject.NULL:n).put("timeout_ms",t).put("attempts",a);
    }

    private void save(){
        try{
            JSONObject values=value();
            EditText name=new EditText(context());
            name.setHint("配置名称");
            name.setText(selected==null?"":selected.optString("name"));
            final JSONObject base=selected;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("保存共享诊断配置").setView(name).setPositiveButton("保存",(d,w)->{try{String n=name.getText().toString().trim();if(n.isEmpty())throw new IllegalArgumentException("请输入名称");values.put("name",n);JSONObject changes=new JSONObject();for(String key:LedgerStore.keys(values))if(base==null||!LedgerStore.equivalent(values.opt(key),base.opt(key)))changes.put(key,values.get(key));String id=base==null?LedgerStore.id():base.getString("id");if(changes.length()>0)sync.store().patch("network_profile",id,changes,false,base==null?new JSONArray():base.getJSONArray("_heads"));selected=sync.store().entity("network_profile",id);sync.changed();Toast.makeText(context(),"已保存，将自动同步",0).show();}catch(Exception e){error(e);}}).setNegativeButton("取消",null).show();
        }catch(Exception e){error(e);}
    }

    private void profiles(){
        try{
            JSONArray profiles=sync.store().list("network_profile");
            String[] names=new String[profiles.length()];
            for(int i=0;i<names.length;i++)names[i]=profiles.getJSONObject(i).optString("name")+(profiles.getJSONObject(i).getJSONObject("_conflicts").length()>0?" ⚠ 冲突":"");
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("共享诊断配置").setItems(names,(d,index)->{try{applyProfile(profiles.getJSONObject(index));}catch(Exception e){error(e);}}).setNegativeButton("关闭",null).show();
        }catch(Exception e){error(e);}
    }

    private void applyProfile(JSONObject p)throws Exception{
        selected=p;
        target.setText(p.getString("target"));
        port.setText(p.isNull("port")?"":p.optString("port"));
        timeout.setText(p.optString("timeout_ms","5000"));
        attempts.setText(p.optString("attempts","3"));
    }
}
