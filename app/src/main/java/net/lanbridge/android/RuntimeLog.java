package net.lanbridge.android;

import android.app.Activity;
import android.content.*;
import android.os.Build;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/** App-private bounded diagnostic events. Callers pass counts/state, never SMS, keys or responses. */
final class RuntimeLog {
    private static final Object LOCK=new Object();
    private final File current,previous;private final int limit;
    RuntimeLog(File folder,int limit){current=new File(folder,"events.log");previous=new File(folder,"events.previous.log");this.limit=limit;}
    private static RuntimeLog store(Context context){return new RuntimeLog(new File(context.getFilesDir(),"runtime-logs"),128*1024);}
    void append(String event,String detail)throws IOException{synchronized(LOCK){if(!current.getParentFile().isDirectory()&&!current.getParentFile().mkdirs())throw new IOException("无法创建日志目录");String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date())+" ["+event.replaceAll("[\\r\\n]"," ")+"] "+detail.replaceAll("[\\r\\n]"," ")+"\n";byte[] bytes=line.getBytes(StandardCharsets.UTF_8);if(bytes.length>4096)throw new IOException("日志事件过长");if(current.length()+bytes.length>limit){if(previous.exists()&&!previous.delete())throw new IOException("无法轮换日志");if(current.exists()&&!current.renameTo(previous))throw new IOException("无法轮换日志");}try(FileOutputStream out=new FileOutputStream(current,true)){out.write(bytes);}}}
    String read()throws IOException{synchronized(LOCK){StringBuilder text=new StringBuilder();for(File file:new File[]{previous,current})if(file.isFile())text.append(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));return text.length()==0?"暂无运行日志":text.toString();}}
    void clear()throws IOException{synchronized(LOCK){for(File file:new File[]{previous,current})if(file.exists()&&!file.delete())throw new IOException("无法清空日志");}}
    static void record(Context context,String event,String detail){try{store(context).append(event,detail);}catch(IOException e){android.util.Log.w("RuntimeLog","日志写入失败");}}
    static void failure(Context context,String stage,Throwable error){String message=error.getMessage()==null?"":error.getMessage(),reason="";java.util.regex.Matcher http=java.util.regex.Pattern.compile("HTTP ([1-5][0-9]{2})").matcher(message);if(http.find())reason="；HTTP="+http.group(1);else if(message.startsWith("AI 连接失败"))reason="；连接失败或超时";else if(message.startsWith("AI 提取字段"))reason="；原文证据校验失败";else if(message.startsWith("AI 返回不完整"))reason="；响应截断";else if(message.startsWith("AI 未完整"))reason="；来源覆盖不完整";else if(message.startsWith("需要")&&message.contains("短信"))reason="；短信权限未授予";record(context,stage,"失败类型="+error.getClass().getSimpleName()+reason+"；详细提示见当前操作界面");}
    static void show(Activity activity){ScrollView scroll=new ScrollView(activity);LinearLayout form=ToolUi.column(activity);int pad=ToolUi.dp(activity,16);form.setPadding(pad,0,pad,pad);scroll.addView(form);form.addView(ToolUi.text(activity,"仅保存在本机，最多两份128 KiB日志；记录读取、过滤、AI及入库状态，不记录短信正文、取件码、密钥或AI原始响应。",13,ToolUi.MUTED));TextView text=ToolUi.text(activity,"正在读取日志…",12,ToolUi.INK);text.setTextIsSelectable(true);form.addView(text);
        androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("运行日志").setView(scroll).setPositiveButton("复制日志",null).setNeutralButton("清空",null).setNegativeButton("关闭",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-1).setEnabled(false);dialog.getButton(-1).setOnClickListener(v->{((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("安卓工具箱运行日志",text.getText()));Toast.makeText(activity,"日志已复制",Toast.LENGTH_SHORT).show();});dialog.getButton(-3).setOnClickListener(v->new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("清空运行日志？").setMessage("仅删除本机调试日志，不影响快递记录。").setNegativeButton("取消",null).setPositiveButton("清空",(c,w)->new Thread(()->{try{store(activity).clear();activity.runOnUiThread(()->text.setText("暂无运行日志"));}catch(IOException e){activity.runOnUiThread(()->Toast.makeText(activity,"无法清空日志",Toast.LENGTH_SHORT).show());}},"runtime-log-clear").start()).show());});dialog.show();
        new Thread(()->{String value;try{value="安卓工具箱 "+BuildConfig.VERSION_NAME+" · Android "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL+"\n\n"+store(activity).read();}catch(IOException e){value="日志读取失败";}String loaded=value;activity.runOnUiThread(()->{if(dialog.isShowing()){text.setText(loaded);dialog.getButton(-1).setEnabled(true);scroll.post(()->scroll.fullScroll(ScrollView.FOCUS_DOWN));}});},"runtime-log-read").start();
    }
}
