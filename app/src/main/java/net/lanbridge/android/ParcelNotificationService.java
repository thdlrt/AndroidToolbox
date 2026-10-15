package net.lanbridge.android;

import android.app.Notification;
import android.os.*;
import android.service.notification.*;
import java.util.*;
import java.util.concurrent.*;

/** System-bound listener: queues local candidates; never calls AI from the background. */
public final class ParcelNotificationService extends NotificationListenerService {
    static volatile boolean connected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onListenerConnected(){connected=true;RuntimeLog.record(this,"通知监听","系统连接已建立");try{StatusBarNotification[] active=getActiveNotifications();if(active!=null)for(StatusBarNotification notification:active)onNotificationPosted(notification);}catch(Exception e){RuntimeLog.failure(this,"当前通知读取",e);}}
    @Override public void onListenerDisconnected(){connected=false;RuntimeLog.record(this,"通知监听","系统连接已断开；请检查通知访问权限或重新打开设置");}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null||!ParcelNotifications.enabled(this)||!sbn.getPackageName().equals(ParcelNotifications.sourcePackage(this)))return;
        Notification n=sbn.getNotification();if(n==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        worker.execute(()->{try{
            if(!ParcelNotifications.enabled(this)||!sbn.getPackageName().equals(ParcelNotifications.sourcePackage(this)))return;
            List<ParcelSms.Message> texts=extract(n,sbn.getPostTime());ParcelSmsFilter.Rules current=ParcelSmsFilter.load(this);ParcelSmsFilter.Rules keywords=new ParcelSmsFilter.Rules(true,current.keywords.isEmpty()?ParcelSmsFilter.DEFAULT_KEYWORDS:current.text);int matched=0,added=0;
            try(ParcelNotificationInbox inbox=new ParcelNotificationInbox(this)){for(ParcelSms.Message text:texts){if(!keywords.matches(text))continue;matched++;if(inbox.offer(sbn.getPackageName(),text.sender,text.body,text.date,System.currentTimeMillis()))added++;}}
            RuntimeLog.record(this,"短信通知","正文候选="+texts.size()+"；关键词匹配="+matched+"；新增缓存="+added+"；后台未调用AI");
            if(added>0)ParcelNotifications.get(this).captured();else ParcelNotifications.get(this).changed();
        }catch(Exception e){RuntimeLog.failure(this,"通知缓存",e);ParcelNotifications.get(this).failed(e);}});
    }
    static List<ParcelSms.Message> extract(Notification notification,long received){
        List<ParcelSms.Message> result=new ArrayList<>();Bundle extras=notification.extras;if(extras==null)return result;
        String title=text(extras.getCharSequence(Notification.EXTRA_TITLE));Parcelable[] bundles=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(bundles!=null){for(Notification.MessagingStyle.Message message:Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)){String body=text(message.getText());android.app.Person person=message.getSenderPerson();add(result,person==null?title:text(person.getName()),body,message.getTimestamp()>0?message.getTimestamp():received);}if(!result.isEmpty())return result;}
        String body=text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));if(body.isEmpty())body=text(extras.getCharSequence(Notification.EXTRA_TEXT));
        if(!body.isEmpty())add(result,title,body,received);else {CharSequence[] lines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)for(CharSequence line:lines)add(result,title,text(line),received);}return result;
    }
    private static String text(CharSequence value){return value==null?"":value.toString().trim();}
    private static void add(List<ParcelSms.Message> result,String sender,String body,long date){if(!body.isEmpty()&&body.length()<=ParcelNotificationInbox.MAX_BODY&&result.size()<100)result.add(new ParcelSms.Message("notification-preview:"+result.size(),date,sender.length()>500?sender.substring(0,500):sender,body));}
    @Override public void onDestroy(){connected=false;worker.shutdown();super.onDestroy();}
}
