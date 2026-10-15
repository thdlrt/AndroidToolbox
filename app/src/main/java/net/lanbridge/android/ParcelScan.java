package net.lanbridge.android;

import android.content.Context;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** One observable read/filter/dedupe pipeline shared by manual and automatic inbox scanning. */
final class ParcelScan {
    final long start,end;final int read,matched,duplicates,skipped,waiting,archived;final boolean filtering,retry;
    final List<ParcelSms.Message> pending;
    final List<ParcelSms.Message> readSources;
    final List<ParcelSms.Message> unlinked;
    private ParcelScan(long start,long end,List<ParcelSms.Message> raw,int matched,boolean filtering,boolean retry,ParcelStore.SourceSelection selection){this.start=start;this.end=end;this.read=raw.size();this.readSources=Collections.unmodifiableList(raw);this.matched=matched;this.duplicates=selection.duplicates;this.skipped=matched-duplicates-selection.pending.size();this.filtering=filtering;this.retry=retry;this.pending=selection.pending;this.unlinked=selection.unlinked;this.waiting=selection.waiting;this.archived=selection.archived;}
    static ParcelScan read(Context context,long start,long end,ParcelStore store,boolean retry)throws IOException{
        ParcelSmsFilter.Rules rules=ParcelSmsFilter.load(context);List<ParcelSms.Message> raw=ParcelSms.query(context,start,end),matched=rules.select(raw);
        ParcelScan result=new ParcelScan(start,end,raw,matched.size(),rules.enabled,retry,store.selectSources(matched,retry));RuntimeLog.record(context,"短信扫描",result.summary());RuntimeLog.record(context,"读取输入概要",ParcelInput.summary(raw));return result;
    }
    String summary(){return "范围="+format(start)+" 至 "+format(end)+"；时区="+ZoneId.systemDefault()+"；读取="+read+"；关键词匹配="+matched+"；过滤="+(read-matched)+"；重复="+duplicates+"；已处理跳过="+skipped+"；已有待取记录="+waiting+"；已有归档记录="+archived+"；已分析无关联记录="+unlinked.size()+"；待AI="+pending.size()+"；过滤开关="+filtering+"；重扫="+retry;}
    String emptyReason(){if(read==0)return "所选日期未读取到普通收件箱短信，请检查日期或权限。5G／增强消息可能不在系统短信库中，可从短信应用分享到取件助手，或复制后从剪贴板导入";if(matched==0)return "已读取 "+read+" 条短信，但未命中关键词，可在取件设置调整或关闭过滤";if(!unlinked.isEmpty())return "已读取到短信，其中 "+unlinked.size()+" 条以前分析过但没有关联快递记录，可重新识别";return "已匹配 "+matched+" 条短信，已有待取记录 "+waiting+" 条、归档记录 "+archived+" 条；本次跳过，不再调用 AI";}
    static String format(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
}
