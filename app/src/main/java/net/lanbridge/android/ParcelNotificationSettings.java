package net.lanbridge.android;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

final class ParcelNotificationSettings {
    static void show(Activity activity,Runnable changed){
        ScrollView scroll=new ScrollView(activity);LinearLayout form=ToolUi.column(activity);int pad=ToolUi.dp(activity,20);form.setPadding(pad,0,pad,pad);scroll.addView(form);
        CheckBox enabled=new CheckBox(activity);enabled.setText("导入快递通知");enabled.setChecked(ParcelNotifications.enabled(activity));form.addView(enabled);
        form.addView(ToolUi.text(activity,"只缓存所选短信应用中命中快递关键词的通知，打开工具箱后分析。无需一直打开界面。",13,ToolUi.MUTED));
        Map<String,String> apps=new TreeMap<>();PackageManager pm=activity.getPackageManager();String selected=ParcelNotifications.sourcePackage(activity);if(!selected.isEmpty())apps.put(selected,label(pm,selected));
        for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){String pkg=info.activityInfo.packageName;if(!pkg.equals(activity.getPackageName()))apps.put(pkg,info.loadLabel(pm).toString());}
        List<String> packages=new ArrayList<>(apps.keySet());packages.sort(Comparator.comparing(apps::get));List<String> names=new ArrayList<>();for(String pkg:packages)names.add(apps.get(pkg));packages.add(0,"");names.add(0,"请选择短信应用");
        form.addView(ToolUi.text(activity,"短信来源应用",14,ToolUi.INK));Spinner source=ToolUi.spinner(activity);source.setContentDescription("短信通知来源应用");source.setAdapter(ToolUi.choices(activity,names.toArray(new String[0])));if(packages.contains(selected))source.setSelection(packages.indexOf(selected));form.addView(source);
        int count;try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(activity)){count=inbox.count(System.currentTimeMillis());}
        String state="通知访问："+(ParcelNotifications.access(activity)?"已授权":"未授权")+"；系统连接："+(ParcelNotificationService.connected?"已连接":"未连接")+"\n待处理缓存："+count+" 条（保留14天，最多500条）";
        ToolUi.space(form,ToolUi.text(activity,state+"\n"+ParcelNotifications.get(activity).status,13,ToolUi.MUTED));
        androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("5G消息通知导入").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        Runnable save=()->{String pkg=packages.isEmpty()?"":packages.get(source.getSelectedItemPosition());ParcelNotifications.configure(activity,enabled.isChecked(),pkg);changed.run();};
        ToolUi.space(form,ToolUi.button(activity,"通知访问设置",false,()->{try{save.run();dialog.dismiss();activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();}}));
        ToolUi.space(form,ToolUi.button(activity,"处理已缓存通知",false,()->{try{save.run();dialog.dismiss();ParcelNotifications.get(activity).request(true);}catch(Exception e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();}}));
        ToolUi.space(form,ToolUi.button(activity,"清空通知缓存",false,()->new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle("清空通知缓存？").setMessage("删除尚未分析及用于通知去重的缓存，已生成的快递记录保留。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(activity)){inbox.clear();}dialog.dismiss();changed.run();}).show()));
        ToolUi.space(form,ToolUi.text(activity,"关键词在“短信快速过滤”中编辑。未通知、隐藏正文或已清除的消息请分享/复制。强行停止后需再打开应用。分析时正文会发给取件 AI。",13,ToolUi.MUTED));
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{save.run();dialog.dismiss();if(enabled.isChecked()&&ParcelNotifications.access(activity))android.service.notification.NotificationListenerService.requestRebind(ParcelNotifications.component(activity));}catch(Exception e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();
    }
    private static String label(PackageManager pm,String pkg){try{return pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
}
