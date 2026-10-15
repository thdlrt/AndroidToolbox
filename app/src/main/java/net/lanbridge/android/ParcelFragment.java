package net.lanbridge.android;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Import actions explicitly select what to analyze; no repeated confirmation steps. */
public final class ParcelFragment extends ToolFragment {
    private static final int SMS_PERMISSION=610, PICK_IMAGE=611, AUTO_PERMISSION=612;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LocalDate start=LocalDate.now().minusDays(2),end=LocalDate.now();
    private Button startButton,endButton;
    private android.widget.ImageButton add;
    private TextView status;
    private LinearLayout rows;
    private Spinner filter,sort;
    private boolean busy;
    private Intent pendingShare;
    private boolean retryUnmatched,checkOnly;
    private int presetDays=3;
    private ParcelStore store;
    private final android.os.Handler autoHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private long autoRevision=-1;
    private long notificationRevision=-1;
    private final Runnable autoRefresh=new Runnable(){public void run(){ParcelAutoReader reader=ParcelAutoReader.get(context());if(!busy&&reader.revision!=autoRevision){autoRevision=reader.revision;if(ParcelAutoReader.enabled(context())){status.setText(reader.status);refresh();}}ParcelNotifications notifications=ParcelNotifications.get(context());if(!busy&&notificationRevision!=notifications.revision){notificationRevision=notifications.revision;if(ParcelNotifications.enabled(context())){if(!notifications.status.isEmpty())status.setText(notifications.status);refresh();}}autoHandler.postDelayed(this,700);}};
    private String filterMode="pending",sortMode="address";
    @Override protected View createContent(Bundle state){
        store=new ParcelStore(context().getApplicationContext());
        if(state!=null)try{start=LocalDate.parse(state.getString("start",start.toString()));end=LocalDate.parse(state.getString("end",end.toString()));presetDays=state.getInt("preset_days",0);}catch(Exception ignored){}
        ScrollView scroll=new ScrollView(context());LinearLayout body=ToolUi.column(context());int pad=ToolUi.dp(context(),20);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        LinearLayout heading=ToolUi.header(context(),"取件助手",this::closePage);body.addView(heading);
        heading.addView(ToolUi.iconButton(context(),"settings","取件设置",this::settings));
        add=ToolUi.iconButton(context(),"plus","添加取件信息",this::addMenu);ToolUi.ripple(add,0xffe5edff,14);heading.addView(add);
        status=ToolUi.text(context(),"",14,ToolUi.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        LinearLayout controls=new LinearLayout(context());filter=spinner(new String[]{"待取件","已归档","全部"},new String[]{"pending","archived","all"},value->{filterMode=value;refresh();});sort=spinner(new String[]{"按地点","按柜 / 站点","按快递公司","按时间"},new String[]{"address","locker","carrier","newest"},value->{sortMode=value;refresh();});filter.setContentDescription("取件状态");sort.setContentDescription("分类排序");controls.addView(filter,new LinearLayout.LayoutParams(0,ToolUi.dp(context(),52),1));LinearLayout.LayoutParams sortLayout=new LinearLayout.LayoutParams(0,ToolUi.dp(context(),52),1);sortLayout.leftMargin=ToolUi.dp(context(),8);controls.addView(sort,sortLayout);body.addView(controls);
        rows=ToolUi.column(context());body.addView(rows);return scroll;
    }
    private void settings(){
        LinearLayout form=ToolUi.column(context());int pad=ToolUi.dp(context(),20);form.setPadding(pad,0,pad,pad);
        com.google.android.material.materialswitch.MaterialSwitch toggle=new com.google.android.material.materialswitch.MaterialSwitch(context());toggle.setText("打开软件自动读取短信");int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};toggle.setThumbTintList(new android.content.res.ColorStateList(states,new int[]{android.graphics.Color.WHITE,ToolUi.MUTED}));toggle.setTrackTintList(new android.content.res.ColorStateList(states,new int[]{ToolUi.BLUE,0xffe6ebf2}));toggle.setTrackDecorationTintList(new android.content.res.ColorStateList(states,new int[]{ToolUi.BLUE,0xffcbd5e1}));toggle.setChecked(ParcelAutoReader.enabled(context()));form.addView(toggle);
        EditText days=ToolUi.field(context(),form,"过去天数（含今天，1–93）",String.valueOf(ParcelAutoReader.days(context())),false);days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ToolUi.space(form,ToolUi.text(context(),"启用后，打开应用会读取范围内未处理的短信，经短信快速过滤后交给 AI 分析。已处理内容自动跳过。",13,ToolUi.MUTED));
        ToolUi.space(form,ToolUi.text(context(),ParcelAutoReader.get(context()).status,13,ToolUi.MUTED));
        Button organize=ToolUi.button(context(),"整理地点",false,()->{});ToolUi.space(form,organize);
        Button keywords=ToolUi.button(context(),"短信快速过滤",false,()->{});ToolUi.space(form,keywords);
        Button notifications=ToolUi.button(context(),"5G消息通知导入",false,()->{});ToolUi.space(form,notifications);
        ScrollView settingsScroll=new ScrollView(context());settingsScroll.addView(form);
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("取件设置").setView(settingsScroll).setNeutralButton("AI 设置",(d,w)->navigate("ai-settings")).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        organize.setOnClickListener(v->{if(busy)return;dialog.dismiss();organizeLocations();});
        keywords.setOnClickListener(v->{if(busy)return;dialog.dismiss();keywordSettings();});
        notifications.setOnClickListener(v->{if(busy)return;dialog.dismiss();ParcelNotificationSettings.show(context(),()->refresh());});
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int range=Integer.parseInt(days.getText().toString().trim());ParcelAutoPolicy.days(range);if(toggle.isChecked())new AiSettings(context()).parcelConfig(true);ParcelAutoReader.configure(context(),toggle.isChecked(),range);dialog.dismiss();if(toggle.isChecked()){if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_SMS},AUTO_PERMISSION);else ParcelAutoReader.get(context()).request(true);}else status.setText("自动读取已关闭");}catch(Exception e){days.setError(e.getMessage()==null?"请填写 1–93 天":e.getMessage());}}));dialog.show();
    }
    private void keywordSettings(){
        ParcelSmsFilter.Rules rules=ParcelSmsFilter.load(context());ScrollView scroll=new ScrollView(context());LinearLayout form=ToolUi.column(context());int pad=ToolUi.dp(context(),20);form.setPadding(pad,0,pad,pad);scroll.addView(form);
        CheckBox toggle=new CheckBox(context());toggle.setText("启用快递关键词过滤");toggle.setChecked(rules.enabled);form.addView(toggle);
        form.addView(ToolUi.text(context(),"正文或发送方包含任一关键词即交给 AI 判断。仅用于自动读取和扫描短信；剪贴板、图片直接分析。关闭后扫描全部短信。",13,ToolUi.MUTED));
        EditText keywords=ToolUi.field(context(),form,"快递关键词（每行一个）",rules.text,false);keywords.setSingleLine(false);keywords.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);keywords.setGravity(android.view.Gravity.TOP);keywords.setMinLines(6);keywords.setMaxLines(8);
        TextView count=ToolUi.text(context(),rules.keywords.size()+" 个关键词",13,ToolUi.MUTED);form.addView(count);
        keywords.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int c){}public void afterTextChanged(android.text.Editable value){keywords.setError(null);try{count.setText(ParcelSmsFilter.parse(value.toString()).size()+" 个关键词");}catch(Exception e){count.setText(e.getMessage());}}});
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("短信快速过滤").setView(scroll).setNeutralButton("恢复默认",null).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-3).setOnClickListener(v->{keywords.setText(ParcelSmsFilter.DEFAULT_KEYWORDS);toggle.setChecked(true);});dialog.getButton(-1).setOnClickListener(v->{try{ParcelSmsFilter.save(context(),toggle.isChecked(),keywords.getText().toString());dialog.dismiss();status.setText("短信过滤设置已保存");}catch(Exception e){keywords.setError(e.getMessage());}});});dialog.show();
    }
    private void organizeLocations(){
        if(busy)return;setBusy(true,"正在读取地点…");
        worker.execute(()->{try{Map<String,Integer> locations=store.locations();ui(()->{setBusy(false,"");if(locations.isEmpty()){status.setText("暂无可整理的地点");return;}locationDialog(locations);});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});
    }
    private void locationDialog(Map<String,Integer> locations){
        LinearLayout form=ToolUi.column(context());int pad=ToolUi.dp(context(),20);form.setPadding(pad,0,pad,pad);
        form.addView(ToolUi.text(context(),"勾选同一站点的不同名称，统一后也用于后续导入。柜位、取件码、来源和归档状态保留；无需再次调用 AI。",13,ToolUi.MUTED));
        EditText target=ToolUi.field(context(),form,"统一地点名称","",false);target.setSingleLine(true);
        ScrollView scroll=new ScrollView(context());LinearLayout options=ToolUi.column(context());scroll.addView(options);form.addView(scroll,new LinearLayout.LayoutParams(-1,ToolUi.dp(context(),Math.min(220,locations.size()*48))));
        Map<String,CheckBox> checks=new LinkedHashMap<>();TextView preview=ToolUi.text(context(),"请选择地点",13,ToolUi.MUTED);form.addView(preview);
        for(Map.Entry<String,Integer> entry:locations.entrySet()){
            CheckBox box=new CheckBox(context());box.setText(entry.getKey()+"（"+entry.getValue()+" 条）");checks.put(entry.getKey(),box);options.addView(box);
            box.setOnCheckedChangeListener((b,checked)->{int count=0;for(Map.Entry<String,CheckBox> c:checks.entrySet())if(c.getValue().isChecked())count+=locations.get(c.getKey());preview.setText("将整理 "+count+" 条记录（含已归档）");if(checked&&target.getText().toString().trim().isEmpty())target.setText(entry.getKey());});
        }
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("整理地点").setView(form).setNegativeButton("取消",null).setPositiveButton("预览整理",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{List<String> selected=new ArrayList<>();int count=0;for(Map.Entry<String,CheckBox> c:checks.entrySet())if(c.getValue().isChecked()){selected.add(c.getKey());count+=locations.get(c.getKey());}if(selected.isEmpty()){preview.setText("请先勾选地点");return;}String name=ParcelLocation.format(target.getText().toString());ParcelLocation place=ParcelLocation.of(name,"");if(name.isEmpty()||name.length()>512||!place.locker.isEmpty()){target.setError("请填写站点名称，不含室内 / 室外或柜号");return;}
            String summary=String.join("\n",selected)+"\n↓\n"+place.location+"\n\n将整理 "+count+" 条记录（含已归档），并记住这些名称的对应规则。";
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("确认统一地点").setMessage(summary).setNegativeButton("返回修改",null).setPositiveButton("确认整理",(confirm,w)->{dialog.dismiss();setBusy(true,"正在整理地点…");worker.execute(()->{try{int changed=store.unifyLocations(selected,name);ui(()->{setBusy(false,"已统一 "+changed+" 条记录的地点，后续导入沿用此规则");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}).show();
        }));dialog.show();
    }
    private void addMenu(){if(busy)return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("添加取件信息").setItems(new String[]{"扫描短信","从剪贴板粘贴","从相册选择"},(d,which)->{if(which==0)scanOptions();else if(which==1)paste();else pickImage();}).show();}
    private void scanOptions(){
        rollDates();
        LinearLayout form=ToolUi.column(context());int p=ToolUi.dp(context(),20);form.setPadding(p,0,p,p);
        ToolUi.Grid quick=new ToolUi.Grid(context(),60,4,6);for(int days:new int[]{1,2,3,7})quick.addView(ToolUi.button(context(),days==1?"今天":days+" 天",false,()->{presetDays=days;rollDates();dates();}));form.addView(quick);
        startButton=ToolUi.button(context(),"",false,()->pickDate(true));endButton=ToolUi.button(context(),"",false,()->pickDate(false));form.addView(startButton);form.addView(endButton);dates();
        ToolUi.space(form,ToolUi.text(context(),"所选时间内尚未处理的短信经关键词过滤后交给 AI 分析并去重。可在取件设置编辑或关闭过滤。",13,ToolUi.MUTED));
        CheckBox retry=new CheckBox(context());retry.setText("重新识别未生成快递记录的短信");form.addView(retry);
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("扫描短信").setView(form).setNeutralButton("仅检查读取",null).setPositiveButton("扫描并分析",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{checkOnly=false;if(!ready(false))return;retryUnmatched=retry.isChecked();dialog.dismiss();scan();});dialog.getButton(-3).setOnClickListener(v->{checkOnly=true;retryUnmatched=retry.isChecked();dialog.dismiss();scan();});});dialog.show();
    }
    private interface Choice{void set(String value);}
    private Spinner spinner(String[] labels,String[] values,Choice change){Spinner result=ToolUi.spinner(context());ArrayAdapter<String> adapter=ToolUi.choices(context(),labels);result.setAdapter(adapter);result.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){change.set(values[position]);}});return result;}
    private void rollDates(){if(presetDays>0){LocalDate[] range=ParcelDateRange.rolling(LocalDate.now(),presetDays);start=range[0];end=range[1];}}
    private void dates(){startButton.setText("起始日期："+start);endButton.setText("结束日期："+end);}
    private void pickDate(boolean first){if(busy)return;LocalDate date=first?start:end;new DatePickerDialog(context(),(view,y,m,d)->{presetDays=0;if(first)start=LocalDate.of(y,m+1,d);else end=LocalDate.of(y,m+1,d);dates();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}
    private boolean ready(boolean vision){try{AiSettings settings=new AiSettings(context());settings.parcelConfig(vision);return true;}catch(Exception e){error(e);return false;}}
    private void scan(){if(busy||!checkOnly&&!ready(false))return;rollDates();RuntimeLog.record(context(),"手动扫描开始","模式="+(checkOnly?"仅检查读取":"AI分析")+"；日期="+start+" 至 "+end+"；短信权限="+(checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED));if(end.isBefore(start)||ChronoUnit.DAYS.between(start,end)>92){error(new IllegalArgumentException("请选择不超过 93 天的有效日期范围"));return;}if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_SMS},SMS_PERMISSION);return;}readSms();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==AUTO_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)ParcelAutoReader.get(context()).request(true);else status.setText("未授予短信权限，可在取件设置中重新开启或使用粘贴/相册导入");return;}if(request!=SMS_PERMISSION)return;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(checkOnly||ready(false))readSms();}else {RuntimeLog.record(context(),"短信权限","用户未授予读取短信权限");status.setText("未获得读取短信权限。可以使用“从剪贴板粘贴”或“从相册选择”导入，无需短信权限。");}}
    private void readSms(){rollDates();boolean retry=retryUnmatched,diagnose=checkOnly;LocalDate from=start,to=end;long first=from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),last=to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1;setBusy(true,"正在读取所选日期短信，尚未发送给 AI…");worker.execute(()->{try{ParcelScan scan=ParcelScan.read(context().getApplicationContext(),first,last,store,retry);String provider=diagnose?ParcelSms.inboxSummary(context()):"";ui(()->{setBusy(false,"");if(diagnose){String report="短信权限：已授权\n读取来源：系统收件箱\n"+provider+"\n\n"+scan.summary().replace("；","\n")+"\n\n"+(scan.pending.isEmpty()?scan.emptyReason():"已读取到 "+scan.pending.size()+" 条待分析短信。本次只检查读取，未发送给 AI。")+"\n\n5G／增强消息可能不在系统短信库中。缺少的消息可从短信应用分享到取件助手，或复制后从剪贴板导入。此检查不修改已处理标记。";new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("短信读取诊断").setMessage(report).setNeutralButton("查看原文",(d,w)->readSources(scan.readSources)).setPositiveButton("查看日志",(d,w)->RuntimeLog.show(context())).setNegativeButton("关闭",null).show();status.setText("读取检查完成，未调用 AI");return;}if(scan.pending.isEmpty()){status.setText(scan.emptyReason());if(!scan.unlinked.isEmpty())new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("重新识别未生成记录的短信？").setNeutralButton("查看原文",(d,w)->readSources(scan.unlinked)).setMessage("已读到 "+scan.unlinked.size()+" 条以前分析过、但没有关联快递记录的短信，可能是非取件通知或模型漏识别。重新识别会将这些短信发给已配置的 AI，已有待取和归档记录继续跳过。").setNegativeButton("取消",null).setPositiveButton("重新识别",(d,w)->{RuntimeLog.record(context(),"手动重试","无关联记录来源="+scan.unlinked.size());analyze(scan.unlinked,Collections.emptyList(),true);}).show();return;}status.setText("已读取 "+scan.read+" 条，匹配 "+scan.matched+" 条，待分析 "+scan.pending.size()+" 条");analyze(scan.pending,Collections.emptyList(),retry);});}catch(Exception e){RuntimeLog.failure(context(),"手动读取",e);ui(()->{setBusy(false,"读取失败，可复制短信后从剪贴板导入。");error(e);});}});}

    private void readSources(List<ParcelSms.Message> sources){
        if(sources.isEmpty()){new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("系统实际读取短信").setMessage("所选日期没有读取到短信。").setPositiveButton("关闭",null).show();return;}
        List<ParcelSms.Message> recent=new ArrayList<>(sources.subList(Math.max(0,sources.size()-100),sources.size()));Collections.reverse(recent);String[] labels=new String[recent.size()];
        for(int i=0;i<recent.size();i++){ParcelSms.Message source=recent.get(i);String preview=source.body.replaceAll("[\\r\\n]"," ");labels[i]=dateTime(source.date)+" · "+source.sender+"\n"+(preview.isEmpty()?"（系统返回空正文）":preview.substring(0,Math.min(60,preview.length())));}
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("系统实际读取短信（仅本机）").setItems(labels,(d,which)->readSource(recent.get(which))).setNegativeButton("关闭",null).show();
    }
    private void readSource(ParcelSms.Message source){
        ScrollView scroll=new ScrollView(context());LinearLayout form=ToolUi.column(context());int pad=ToolUi.dp(context(),20);form.setPadding(pad,0,pad,pad);scroll.addView(form);
        form.addView(ToolUi.text(context(),"系统短信接口实际返回的正文，仅在本机展示。本次查看不调用 AI，也不改变已处理标记。\n"+dateTime(source.date)+" · "+source.sender+"\n"+ParcelInput.summary(List.of(source)),13,ToolUi.MUTED));
        TextView body=ToolUi.text(context(),source.body.isEmpty()?"（系统返回空正文，无法从该来源提取快递）":source.body,16,ToolUi.INK);body.setTextIsSelectable(true);ToolUi.space(form,body);
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("实际短信正文").setView(scroll).setPositiveButton("复制正文",(d,w)->{((android.content.ClipboardManager)context().getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("系统实际短信正文",source.body));Toast.makeText(context(),"原文已复制",Toast.LENGTH_SHORT).show();}).setNegativeButton("关闭",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setEnabled(!source.body.isEmpty()));dialog.show();
    }
    private void paste(){
        if(busy)return;
        try{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)context().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip=clipboard==null?null:clipboard.getPrimaryClip();
            if(clip==null||clip.getItemCount()==0)throw new IllegalArgumentException("剪贴板没有文字，请先复制短信内容");
            CharSequence text=clip.getItemAt(0).getText();if(text==null)throw new IllegalArgumentException("剪贴板内容不是文字，请先复制短信内容");
            String body=text.toString().trim();if(body.isEmpty())throw new IllegalArgumentException("剪贴板没有文字，请先复制短信内容");
            if(body.length()>200000)throw new IllegalArgumentException("剪贴板内容过长，请分批复制（最多 200000 字符）");
            analyze(Collections.singletonList(new ParcelSms.Message("manual:"+UUID.randomUUID(),System.currentTimeMillis(),"剪贴板粘贴",body)),Collections.emptyList());
        }catch(SecurityException e){error(new IllegalStateException("无法读取剪贴板，请返回应用并重新复制短信后再试"));}catch(Exception e){error(e);}
    }
    void receiveIntent(Intent intent){
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;
        if(getView()==null){pendingShare=new Intent(intent);return;}
        if(busy){error(new IllegalStateException("正在处理取件信息，请完成后重新分享"));return;}
        try{
            if(!"text/plain".equals(intent.getType())||intent.hasExtra(Intent.EXTRA_STREAM))throw new IllegalArgumentException("请选择短信正文，以纯文字分享到取件助手");
            CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(text==null||text.toString().trim().isEmpty())throw new IllegalArgumentException("分享内容没有文字，请在短信应用中选择消息正文后分享");
            String body=text.toString().trim();
            if(body.length()>200000)throw new IllegalArgumentException("分享内容过长，请分批分享（最多 200000 字符）");
            RuntimeLog.record(context(),"分享导入","来源=用户分享；正文长度="+body.length());
            status.setText("已收到分享正文，正在准备分析…");
            if(!ready(false)){status.setText("请先配置取件 AI，再从短信应用重新分享，或复制后从剪贴板导入");return;}
            analyze(List.of(new ParcelSms.Message("share:"+UUID.randomUUID(),System.currentTimeMillis(),"短信分享",body)),Collections.emptyList());
        }catch(Exception e){error(e);}
    }
    private void pickImage(){if(busy||!ready(true))return;Intent intent=ToolUi.photoPicker();try{startActivityForResult(intent,PICK_IMAGE);}catch(Exception e){error(e);}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();setBusy(true,"正在读取所选图片…");worker.execute(()->{try{String mime=getContentResolver().getType(uri);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalArgumentException("无法读取图片");byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>5*1024*1024)throw new IllegalArgumentException("图片最多 5 MiB，请压缩后重试");out.write(buffer,0,count);}}ParcelAi.Image value=new ParcelAi.Image(mime,out.toByteArray());ui(()->{setBusy(false,"");ParcelSms.Message source=new ParcelSms.Message("image:"+UUID.randomUUID(),System.currentTimeMillis(),"相册导入","提取图片中的取件通知",value.sha256);analyze(Collections.singletonList(source),Collections.singletonList(value));});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void analyze(List<ParcelSms.Message> sources,List<ParcelAi.Image> images){analyze(sources,images,false);}
    private void analyze(List<ParcelSms.Message> sources,List<ParcelAi.Image> images,boolean retry){if(!ready(!images.isEmpty()))return;try{parse(sources,images,new AiSettings(context()).parcelConfig(true),retry);}catch(Exception e){error(e);}}
    private void parse(List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config approvedConfig){parse(sources,images,approvedConfig,false);}
    private void parse(List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config approvedConfig,boolean retry){if(busy||!ready(!images.isEmpty()))return;setBusy(true,"正在调用 AI 分析…");worker.execute(()->{try{int count=ParcelAutoReader.importItems(context().getApplicationContext(),sources,images,approvedConfig,false,retry);ui(()->{setBusy(false,count==0?"已分析 "+sources.size()+" 条短信，AI 未生成新的取件记录；可在运行日志查看分类结果":"已更新 "+count+" 条取件信息");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void refresh(){if(rows==null||busy)return;String selection=filterMode,order=sortMode;worker.execute(()->{try{List<ParcelStore.Parcel> result=new ArrayList<>();if(!selection.equals("archived"))result.addAll(store.list(false,order));if(!selection.equals("pending"))result.addAll(store.list(true,order));result.sort((a,b)->{int group=order.equals("newest")?0:group(a,order).compareToIgnoreCase(group(b,order));return group!=0?group:Long.compare(b.receivedAt,a.receivedAt);});ui(()->{if(filterMode.equals(selection)&&sortMode.equals(order))show(result,order);});}catch(Exception e){ui(()->error(e));}});}
    private String group(ParcelStore.Parcel parcel,String order){return order.equals("locker")?value(parcel.location,"未提供地点")+" · "+value(parcel.locker,"未提供柜 / 站点"):order.equals("carrier")?value(parcel.carrier,"未提供快递公司"):value(parcel.location,"未提供地点");}
    private void show(List<ParcelStore.Parcel> values,String order){rows.removeAllViews();if(values.isEmpty()){TextView empty=ToolUi.text(context(),"暂无取件记录",15,ToolUi.MUTED);empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(0,ToolUi.dp(context(),32),0,ToolUi.dp(context(),32));rows.addView(empty);rows.addView(ToolUi.text(context(),"点击右上角 ＋，从短信或相册添加取件信息",13,ToolUi.MUTED));return;}String previous="";for(ParcelStore.Parcel parcel:values){String section=group(parcel,order);if(!order.equals("newest")&&!section.equals(previous)){TextView label=ToolUi.text(context(),section,17,ToolUi.INK);label.setPadding(0,ToolUi.dp(context(),18),0,ToolUi.dp(context(),8));rows.addView(label);previous=section;}LinearLayout card=ToolUi.column(context());int pad=ToolUi.dp(context(),14);card.setPadding(pad,pad,pad,pad);card.setBackground(ToolUi.box(context(),0xfff5f7fb,14));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=ToolUi.dp(context(),10);rows.addView(card,layout);TextView code=ToolUi.text(context(),"取件码："+value(parcel.code,"未提供"),22,ToolUi.INK);code.setTextIsSelectable(true);card.addView(code);if(parcel.needsReview)card.addView(ToolUi.text(context(),"疑似重复，请核对来源",13,0xffa33d32));card.addView(ToolUi.text(context(),value(parcel.location,"未提供地点")+"\n"+value(parcel.locker,"")+" · "+value(parcel.carrier,"未提供快递公司"),14,ToolUi.MUTED));card.addView(ToolUi.text(context(),(parcel.collected?"已归档":value(parcel.status,"待取"))+" · "+dateTime(parcel.receivedAt)+(parcel.deadline==null||parcel.deadline.isEmpty()?"":"\n截止："+parcel.deadline),13,ToolUi.MUTED));if(parcel.trackingNumber!=null&&!parcel.trackingNumber.isEmpty())card.addView(ToolUi.text(context(),"运单："+parcel.trackingNumber,13,ToolUi.MUTED));LinearLayout actions=new LinearLayout(context());actions.setGravity(android.view.Gravity.CENTER_VERTICAL);Button collect=ToolUi.button(context(),parcel.collected?"恢复待取":"标记已取",!parcel.collected,()->collect(parcel));actions.addView(collect,new LinearLayout.LayoutParams(0,-2,1));actions.addView(ToolUi.iconButton(context(),"more","快递操作",()->parcelActions(parcel)));ToolUi.space(card,actions);}}
    private void collect(ParcelStore.Parcel parcel){if(busy)return;setBusy(true,"正在更新…");worker.execute(()->{try{store.setCollected(parcel.id,!parcel.collected);ui(()->{setBusy(false,parcel.collected?"已恢复为待取件":"已归档，可在已归档中撤销");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void parcelActions(ParcelStore.Parcel parcel){if(busy)return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("快递操作").setItems(new String[]{"编辑信息","查看来源"},(d,which)->{if(which==0)edit(parcel);else sources(parcel);}).show();}
    private void edit(ParcelStore.Parcel parcel){
        if(busy)return;ScrollView scroll=new ScrollView(context());LinearLayout form=ToolUi.column(context());int pad=ToolUi.dp(context(),20);form.setPadding(pad,0,pad,pad);scroll.addView(form);
        TextView feedback=ToolUi.text(context(),"手动修改的字段会保留，原始来源与取件状态不变。",13,ToolUi.MUTED);form.addView(feedback);
        String[] labels={"取件码","运单号","快递公司","地点","柜位","截止提示"},values={parcel.code,parcel.trackingNumber,parcel.carrier,parcel.location,parcel.locker,parcel.deadline==null?"":parcel.deadline};EditText[] fields=new EditText[labels.length];
        for(int i=0;i<labels.length;i++){EditText input=ToolUi.field(context(),form,labels[i],values[i],false);fields[i]=input;input.setSingleLine(true);input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable value){input.setError(null);feedback.setText("手动修改的字段会保留，原始来源与取件状态不变。");feedback.setTextColor(ToolUi.MUTED);}});}
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("编辑快递信息").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{for(EditText input:fields)input.setError(null);ParcelStore.Edit changed=new ParcelStore.Edit(fields[0].getText().toString(),fields[1].getText().toString(),fields[2].getText().toString(),fields[3].getText().toString(),fields[4].getText().toString(),fields[5].getText().toString());
                dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);setBusy(true,"正在保存快递信息…");worker.execute(()->{try{store.edit(parcel,changed);ui(()->{dialog.dismiss();setBusy(false,"快递信息已保存");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);feedback.setText(e.getMessage());feedback.setTextColor(0xffa33d32);scroll.smoothScrollTo(0,0);dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);});}});
            }catch(Exception e){feedback.setText(e.getMessage());feedback.setTextColor(0xffa33d32);for(int i=0;i<labels.length;i++)if(e.getMessage()!=null&&e.getMessage().startsWith(labels[i]))fields[i].setError(e.getMessage());scroll.smoothScrollTo(0,0);}
        }));dialog.show();
    }
    private void sources(ParcelStore.Parcel parcel){StringBuilder text=new StringBuilder();for(ParcelSms.Message source:parcel.sources)text.append(dateTime(source.date)).append(" · ").append(source.sender).append("\n").append(source.body).append("\n\n");new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("来源内容（仅本机）").setMessage(text.toString()).setPositiveButton("关闭",null).show();}
    private void setBusy(boolean value,String message){busy=value;add.setEnabled(!value);filter.setEnabled(!value);sort.setEnabled(!value);status.setTextColor(ToolUi.MUTED);status.setText(message);}
    private void error(Exception e){RuntimeLog.failure(context(),"取件界面",e);if(status!=null){status.setTextColor(0xffa33d32);status.setText(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
    private void ui(Runnable action){runOnUiThread(()->{if(isAdded()&&getView()!=null)action.run();});}
    private static String value(String text,String fallback){return text==null||text.trim().isEmpty()?fallback:text;}
    private static String dateTime(long time){return new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(time));}
    @Override protected void onPageResume(){refresh();autoHandler.post(autoRefresh);if(pendingShare!=null){Intent incoming=pendingShare;pendingShare=null;receiveIntent(incoming);}}
    @Override protected void onPagePause(){autoHandler.removeCallbacks(autoRefresh);}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("start",start.toString());state.putString("end",end.toString());state.putInt("preset_days",presetDays);}
    @Override public void onDestroy(){worker.execute(()->{if(store!=null)store.close();});worker.shutdown();super.onDestroy();}
}
