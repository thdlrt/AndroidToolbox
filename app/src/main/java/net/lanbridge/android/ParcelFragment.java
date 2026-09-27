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
    private static final int SMS_PERMISSION=610, PICK_IMAGE=611;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LocalDate start=LocalDate.now().minusDays(2),end=LocalDate.now();
    private Button startButton,endButton;
    private android.widget.ImageButton add;
    private TextView status;
    private LinearLayout rows;
    private Spinner filter,sort;
    private boolean busy;
    private ParcelStore store;
    private String filterMode="pending",sortMode="address";
    @Override protected View createContent(Bundle state){
        store=new ParcelStore(context().getApplicationContext());
        if(state!=null)try{start=LocalDate.parse(state.getString("start",start.toString()));end=LocalDate.parse(state.getString("end",end.toString()));}catch(Exception ignored){}
        ScrollView scroll=new ScrollView(context());LinearLayout body=ToolUi.column(context());int pad=ToolUi.dp(context(),20);body.setPadding(pad,pad,pad,pad);scroll.addView(body);
        LinearLayout heading=ToolUi.header(context(),"取件助手",this::closePage);body.addView(heading);
        heading.addView(ToolUi.iconButton(context(),"settings","AI 设置",()->navigate("ai-settings")));
        add=ToolUi.iconButton(context(),"plus","添加取件信息",this::addMenu);ToolUi.ripple(add,0xffe5edff,14);heading.addView(add);
        status=ToolUi.text(context(),"",14,ToolUi.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        LinearLayout controls=new LinearLayout(context());filter=spinner(new String[]{"待取件","已归档","全部"},new String[]{"pending","archived","all"},value->{filterMode=value;refresh();});sort=spinner(new String[]{"按地点","按柜 / 站点","按快递公司","按时间"},new String[]{"address","locker","carrier","newest"},value->{sortMode=value;refresh();});filter.setContentDescription("取件状态");sort.setContentDescription("分类排序");controls.addView(filter,new LinearLayout.LayoutParams(0,ToolUi.dp(context(),52),1));LinearLayout.LayoutParams sortLayout=new LinearLayout.LayoutParams(0,ToolUi.dp(context(),52),1);sortLayout.leftMargin=ToolUi.dp(context(),8);controls.addView(sort,sortLayout);body.addView(controls);
        rows=ToolUi.column(context());body.addView(rows);return scroll;
    }
    private void addMenu(){if(busy)return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("添加取件信息").setItems(new String[]{"扫描短信","粘贴短信","从相册选择"},(d,which)->{if(which==0)scanOptions();else if(which==1)paste();else pickImage();}).show();}
    private void scanOptions(){
        LinearLayout form=ToolUi.column(context());int p=ToolUi.dp(context(),20);form.setPadding(p,0,p,p);
        ToolUi.Grid quick=new ToolUi.Grid(context(),60,4,6);for(int days:new int[]{1,2,3,7})quick.addView(ToolUi.button(context(),days==1?"今天":days+" 天",false,()->{start=LocalDate.now().minusDays(days-1);end=LocalDate.now();dates();}));form.addView(quick);
        startButton=ToolUi.button(context(),"",false,()->pickDate(true));endButton=ToolUi.button(context(),"",false,()->pickDate(false));form.addView(startButton);form.addView(endButton);dates();
        ToolUi.space(form,ToolUi.text(context(),"所选时间内尚未处理的短信会发送给您配置的 AI，自动提取并去重。",13,ToolUi.MUTED));
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("扫描短信").setView(form).setPositiveButton("扫描并分析",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(!ready(false))return;if(end.isBefore(start)||ChronoUnit.DAYS.between(start,end)>92){Toast.makeText(context(),"请选择不超过 93 天的有效日期范围",0).show();return;}dialog.dismiss();scan();}));dialog.show();
    }
    private interface Choice{void set(String value);}
    private Spinner spinner(String[] labels,String[] values,Choice change){Spinner result=ToolUi.spinner(context());ArrayAdapter<String> adapter=ToolUi.choices(context(),labels);result.setAdapter(adapter);result.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){change.set(values[position]);}});return result;}
    private void dates(){startButton.setText("起始日期："+start);endButton.setText("结束日期："+end);}
    private void pickDate(boolean first){if(busy)return;LocalDate date=first?start:end;new DatePickerDialog(context(),(view,y,m,d)->{if(first)start=LocalDate.of(y,m+1,d);else end=LocalDate.of(y,m+1,d);dates();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}
    private boolean ready(boolean vision){try{AiSettings settings=new AiSettings(context());settings.parcelConfig(vision);return true;}catch(Exception e){error(e);return false;}}
    private void scan(){if(busy||!ready(false))return;if(end.isBefore(start)||ChronoUnit.DAYS.between(start,end)>92){error(new IllegalArgumentException("请选择不超过 93 天的有效日期范围"));return;}if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_SMS},SMS_PERMISSION);return;}readSms();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request!=SMS_PERMISSION)return;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(ready(false))readSms();}else status.setText("未获得读取短信权限。可以使用“粘贴短信”或“选择图片”导入，无需短信权限。");}
    private void readSms(){LocalDate from=start,to=end;long first=from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),last=to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1;setBusy(true,"正在读取所选日期短信，尚未发送给 AI…");worker.execute(()->{try{List<ParcelSms.Message> sources=store.pendingSources(ParcelSms.query(context().getApplicationContext(),first,last));ui(()->{setBusy(false,"");if(sources.isEmpty()){status.setText("此日期范围没有尚未处理的短信");return;}analyze(sources,Collections.emptyList());});}catch(Exception e){ui(()->{setBusy(false,"读取失败，可改用粘贴短信导入。");error(e);});}});}
    private void paste(){if(busy||!ready(false))return;EditText text=new EditText(context());text.setHint("在此粘贴需要分析的短信");text.setMinLines(4);text.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("粘贴短信").setView(text).setPositiveButton("分析并添加",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String body=text.getText().toString().trim();if(body.isEmpty()){text.setError("请粘贴短信内容");return;}if(body.length()>200000){text.setError("内容过长，请分批导入");return;}dialog.dismiss();analyze(Collections.singletonList(new ParcelSms.Message("manual:"+UUID.randomUUID(),System.currentTimeMillis(),"手动粘贴",body)),Collections.emptyList());}));dialog.show();}
    private void pickImage(){if(busy||!ready(true))return;Intent intent=ToolUi.photoPicker();try{startActivityForResult(intent,PICK_IMAGE);}catch(Exception e){error(e);}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();setBusy(true,"正在读取所选图片…");worker.execute(()->{try{String mime=getContentResolver().getType(uri);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalArgumentException("无法读取图片");byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>5*1024*1024)throw new IllegalArgumentException("图片最多 5 MiB，请压缩后重试");out.write(buffer,0,count);}}ParcelAi.Image value=new ParcelAi.Image(mime,out.toByteArray());ui(()->{setBusy(false,"");ParcelSms.Message source=new ParcelSms.Message("image:"+UUID.randomUUID(),System.currentTimeMillis(),"相册导入","提取图片中的取件通知",value.sha256);analyze(Collections.singletonList(source),Collections.singletonList(value));});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void analyze(List<ParcelSms.Message> sources,List<ParcelAi.Image> images){if(!ready(!images.isEmpty()))return;try{parse(sources,images,new AiSettings(context()).parcelConfig(true));}catch(Exception e){error(e);}}
    private void parse(List<ParcelSms.Message> sources,List<ParcelAi.Image> images,ParcelAi.Config approvedConfig){if(busy||!ready(!images.isEmpty()))return;setBusy(true,"正在调用 AI 分析…");worker.execute(()->{try{List<ParcelSms.Message> pending=store.pendingSources(sources);if(pending.isEmpty()){ui(()->setBusy(false,"这些内容已经处理过"));return;}List<ParcelAi.Item> items=ParcelAi.parse(approvedConfig,pending,images);int count=store.ingest(pending,items);ui(()->{setBusy(false,count==0?"没有新的取件信息":"已更新 "+count+" 条取件信息");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void refresh(){if(rows==null||busy)return;String selection=filterMode,order=sortMode;worker.execute(()->{try{List<ParcelStore.Parcel> result=new ArrayList<>();if(!selection.equals("archived"))result.addAll(store.list(false,order));if(!selection.equals("pending"))result.addAll(store.list(true,order));result.sort((a,b)->{int group=order.equals("newest")?0:group(a,order).compareToIgnoreCase(group(b,order));return group!=0?group:Long.compare(b.receivedAt,a.receivedAt);});ui(()->{if(filterMode.equals(selection)&&sortMode.equals(order))show(result,order);});}catch(Exception e){ui(()->error(e));}});}
    private String group(ParcelStore.Parcel parcel,String order){return order.equals("locker")?value(parcel.locker,"未提供柜 / 站点"):order.equals("carrier")?value(parcel.carrier,"未提供快递公司"):value(parcel.location,"未提供地点");}
    private void show(List<ParcelStore.Parcel> values,String order){rows.removeAllViews();if(values.isEmpty()){TextView empty=ToolUi.text(context(),"暂无取件记录",15,ToolUi.MUTED);empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(0,ToolUi.dp(context(),32),0,ToolUi.dp(context(),32));rows.addView(empty);rows.addView(ToolUi.text(context(),"点击右上角 ＋，从短信或相册添加取件信息",13,ToolUi.MUTED));return;}String previous="";for(ParcelStore.Parcel parcel:values){String section=group(parcel,order);if(!order.equals("newest")&&!section.equals(previous)){TextView label=ToolUi.text(context(),section,17,ToolUi.INK);label.setPadding(0,ToolUi.dp(context(),18),0,ToolUi.dp(context(),8));rows.addView(label);previous=section;}LinearLayout card=ToolUi.column(context());int pad=ToolUi.dp(context(),14);card.setPadding(pad,pad,pad,pad);card.setBackground(ToolUi.box(context(),0xfff5f7fb,14));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=ToolUi.dp(context(),10);rows.addView(card,layout);TextView code=ToolUi.text(context(),"取件码："+value(parcel.code,"未提供"),22,ToolUi.INK);code.setTextIsSelectable(true);card.addView(code);if(parcel.needsReview)card.addView(ToolUi.text(context(),"疑似重复，请核对来源",13,0xffa33d32));card.addView(ToolUi.text(context(),value(parcel.location,"未提供地点")+"\n"+value(parcel.locker,"")+" · "+value(parcel.carrier,"未提供快递公司"),14,ToolUi.MUTED));card.addView(ToolUi.text(context(),(parcel.collected?"已归档":value(parcel.status,"待取"))+" · "+dateTime(parcel.receivedAt)+(parcel.deadline==null||parcel.deadline.isEmpty()?"":"\n截止："+parcel.deadline),13,ToolUi.MUTED));if(parcel.trackingNumber!=null&&!parcel.trackingNumber.isEmpty())card.addView(ToolUi.text(context(),"运单："+parcel.trackingNumber,13,ToolUi.MUTED));LinearLayout actions=new LinearLayout(context());actions.setGravity(android.view.Gravity.CENTER_VERTICAL);Button collect=ToolUi.button(context(),parcel.collected?"恢复待取":"标记已取",!parcel.collected,()->collect(parcel));actions.addView(collect,new LinearLayout.LayoutParams(0,-2,1));actions.addView(ToolUi.iconButton(context(),"more","查看来源",()->sources(parcel)));ToolUi.space(card,actions);}}
    private void collect(ParcelStore.Parcel parcel){if(busy)return;setBusy(true,"正在更新…");worker.execute(()->{try{store.setCollected(parcel.id,!parcel.collected);ui(()->{setBusy(false,parcel.collected?"已恢复为待取件":"已归档，可在已归档中撤销");refresh();});}catch(Exception e){ui(()->{setBusy(false,"");error(e);});}});}
    private void sources(ParcelStore.Parcel parcel){StringBuilder text=new StringBuilder();for(ParcelSms.Message source:parcel.sources)text.append(dateTime(source.date)).append(" · ").append(source.sender).append("\n").append(source.body).append("\n\n");new com.google.android.material.dialog.MaterialAlertDialogBuilder(context()).setTitle("来源内容（仅本机）").setMessage(text.toString()).setPositiveButton("关闭",null).show();}
    private void setBusy(boolean value,String message){busy=value;add.setEnabled(!value);filter.setEnabled(!value);sort.setEnabled(!value);status.setTextColor(ToolUi.MUTED);status.setText(message);}
    private void error(Exception e){if(status!=null){status.setTextColor(0xffa33d32);status.setText(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
    private void ui(Runnable action){runOnUiThread(()->{if(isAdded()&&getView()!=null)action.run();});}
    private static String value(String text,String fallback){return text==null||text.trim().isEmpty()?fallback:text;}
    private static String dateTime(long time){return new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(time));}
    @Override protected void onPageResume(){refresh();}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("start",start.toString());state.putString("end",end.toString());}
    @Override public void onDestroy(){worker.execute(()->{if(store!=null)store.close();});worker.shutdown();super.onDestroy();}
}
