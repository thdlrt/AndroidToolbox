package net.lanbridge.android;

import android.app.Activity;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local titles and working directories, across every configured drive. Loads on explicit selection. */
final class HapiSessionsDialog {
    private final Activity activity;
    private final HapiApi api;
    private final Consumer<String> open;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<HapiApi.Session> all=new ArrayList<>(),visible=new ArrayList<>();
    private AlertDialog dialog;
    private EditText search;
    private TextView status;
    private ListView list;
    private ArrayAdapter<HapiApi.Session> adapter;
    private boolean busy,closed;
    HapiSessionsDialog(Activity activity,String origin,String token,Consumer<String> open){this.activity=activity;api=new HapiApi(origin,token);this.open=open;}
    void dismiss(){if(dialog!=null)dialog.dismiss();}
    void show(){
        LinearLayout root=ToolUi.column(activity);int pad=ToolUi.dp(activity,16);root.setPadding(pad,0,pad,0);
        search=ToolUi.field(activity,root,"搜索标题或目录","",false);
        status=ToolUi.text(activity,"正在读取电脑会话…",12,ToolUi.MUTED);status.setPadding(0,pad,0,pad);root.addView(status);
        list=new ListView(activity);list.setDividerHeight(0);
        adapter=new ArrayAdapter<HapiApi.Session>(activity,android.R.layout.simple_list_item_1,visible){
            @Override public View getView(int position,View reused,ViewGroup parent){
                HapiApi.Session session=getItem(position);LinearLayout row=ToolUi.column(activity);row.setPadding(pad,pad,pad,pad);
                TextView title=ToolUi.text(activity,session.title,15,ToolUi.INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);row.addView(title);
                TextView cwd=ToolUi.text(activity,session.cwd,11,ToolUi.MUTED);cwd.setMaxLines(2);cwd.setEllipsize(TextUtils.TruncateAt.MIDDLE);row.addView(cwd);return row;
            }
        };list.setAdapter(adapter);
        int height=Math.round(activity.getResources().getDisplayMetrics().heightPixels*.82f);
        // AlertDialog measures its custom panel with an unspecified height. A
        // zero-height weighted ListView otherwise has no visible rows there.
        root.addView(list,new LinearLayout.LayoutParams(-1,Math.max(ToolUi.dp(activity,100),height-ToolUi.dp(activity,260)),1));
        TextView hint=ToolUi.text(activity,"选择会话载入历史后可在手机续聊；桌面正在运行的轮次不会同步接管。",12,ToolUi.MUTED);hint.setPadding(0,pad,0,pad);root.addView(hint);
        dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("电脑会话").setView(root).setNegativeButton("关闭",null).setNeutralButton("刷新",null).create();
        dialog.setOnDismissListener(d->{closed=true;api.close();worker.shutdownNow();});dialog.show();
        dialog.getWindow().setLayout(-1,height);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->refresh());
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){filter();}public void afterTextChanged(Editable s){}});
        list.setOnItemClickListener((parent,v,position,id)->{if(busy)return;HapiApi.Session selected=visible.get(position);run(()->api.load(selected),path->{dialog.dismiss();open.accept(path);},"正在载入会话…");});
        refresh();
    }
    private void filter(){String term=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(HapiApi.Session row:all)if((row.title+"\n"+row.cwd).toLowerCase(Locale.ROOT).contains(term))visible.add(row);adapter.notifyDataSetChanged();if(!busy)status.setText(all.isEmpty()?"未找到会话，请确认电脑 runner 使用了同一个 Codex 账号目录。":"显示 "+visible.size()+" 个会话");}
    private void refresh(){if(busy)return;run(api::sessions,rows->{all.clear();all.addAll(rows);filter();},"正在读取电脑会话…");}
    private <T> void run(Callable<T> task,Consumer<T> success,String message){
        busy=true;status.setText(message);list.setEnabled(false);
        worker.submit(()->{try{T result=task.call();activity.runOnUiThread(()->{if(closed)return;busy=false;list.setEnabled(true);success.accept(result);});}
        catch(Exception e){activity.runOnUiThread(()->{if(closed)return;busy=false;list.setEnabled(true);status.setText(e.getMessage()==null?"读取失败，请刷新重试":e.getMessage());});}});
    }
}
