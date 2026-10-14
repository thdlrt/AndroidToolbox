package net.lanbridge.android;

import android.content.Context;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** One observable read/filter/dedupe pipeline shared by manual and automatic inbox scanning. */
final class ParcelScan {
    final long start,end;final int read,matched,duplicates,skipped;final boolean filtering,retry;
    final List<ParcelSms.Message> pending;
    private ParcelScan(long start,long end,int read,int matched,int duplicates,int skipped,boolean filtering,boolean retry,List<ParcelSms.Message> pending){this.start=start;this.end=end;this.read=read;this.matched=matched;this.duplicates=duplicates;this.skipped=skipped;this.filtering=filtering;this.retry=retry;this.pending=Collections.unmodifiableList(pending);}
    static ParcelScan read(Context context,long start,long end,ParcelStore store,boolean retry)throws IOException{
        ParcelSmsFilter.Rules rules=ParcelSmsFilter.load(context);List<ParcelSms.Message> raw=ParcelSms.query(context,start,end),matched=rules.select(raw);Set<String> unique=new HashSet<>();for(ParcelSms.Message message:matched)unique.add(message.fingerprint);List<ParcelSms.Message> pending=store.pendingSources(matched,retry);
        ParcelScan result=new ParcelScan(start,end,raw.size(),matched.size(),matched.size()-unique.size(),unique.size()-pending.size(),rules.enabled,retry,pending);RuntimeLog.record(context,"短信扫描",result.summary());return result;
    }
    String summary(){return "范围="+format(start)+" 至 "+format(end)+"；时区="+ZoneId.systemDefault()+"；读取="+read+"；关键词匹配="+matched+"；过滤="+(read-matched)+"；重复="+duplicates+"；已处理跳过="+skipped+"；待AI="+pending.size()+"；过滤开关="+filtering+"；重扫="+retry;}
    String emptyReason(){if(read==0)return "所选日期未读取到收件箱短信，请检查日期或短信权限";if(matched==0)return "已读取 "+read+" 条短信，但未命中关键词，可在取件设置调整或关闭过滤";return "已匹配 "+matched+" 条短信，均已处理或重复；查看已归档，或勾选重新识别未生成记录的短信";}
    static String format(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
}
