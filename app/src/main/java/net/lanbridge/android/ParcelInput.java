package net.lanbridge.android;

import java.text.Normalizer;
import java.util.*;

/** Count-only input evidence; never includes sender, text, code, URL, or credentials. */
final class ParcelInput {
    static String summary(List<ParcelSms.Message> sources){
        int chars=0,empty=0,fcbox=0,code=0,pickup=0,format=0,templates=0;
        for(ParcelSms.Message source:sources){String body=Normalizer.normalize(source.body,Normalizer.Form.NFKC);chars+=body.length();if(body.trim().isEmpty())empty++;if(body.contains("丰巢"))fcbox++;if(body.contains("取件码"))code++;if(body.contains("取件")||body.contains("来取"))pickup++;for(int at=0;at<body.length();){int point=body.codePointAt(at);if(Character.getType(point)==Character.FORMAT)format++;at+=Character.charCount(point);}if(ParcelFallback.fcbox(source)!=null)templates++;}
        return "来源="+sources.size()+"；正文字符数="+chars+"；空正文="+empty+"；丰巢标记="+fcbox+"；取件码标签="+code+"；取件指令="+pickup+"；格式字符="+format+"；丰巢完整模板="+templates;
    }
    static String safeModel(String model){return model.matches("(?i)(?:deepseek|qwen|gpt|o[134])[-./_a-z0-9]{0,160}")?model:"自定义模型（名称未写入日志）";}
}
