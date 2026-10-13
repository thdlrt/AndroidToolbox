package net.lanbridge.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.*;

/** Broad, editable candidate selection only. The model still decides parcel status. */
final class ParcelSmsFilter {
    static final int MAX_KEYWORDS=256, MAX_KEYWORD_LENGTH=80, MAX_TEXT_LENGTH=16000;
    static final String DEFAULT_KEYWORDS=String.join("\n",new String[]{
        "快递","速递","包裹","邮包","邮件","物流","运单","单号","取件","取货","取包","待取","来取","领取","提货","收件","寄件","派件","派送","配送","投递","签收","已到站","已到店","已入柜","已入库","到达站点","代收","驿站","快递点","自提","提货码","领取码","取货码","取件码","提取码","快递柜","智能柜","储物柜","取件柜","保管费","滞留件",
        "顺丰","SFEXPRESS","中通","ZTO","圆通","YTO","申通","STO","韵达","YUNDA","极兔","J&T","邮政","EMS","京东","德邦","菜鸟","菜鸟速递","丰网","百世","天天快递","安能","宅急送","速尔","优速","龙邦","跨越速运","众邮","丹鸟","九曳","递四方","递壹时","苏宁物流","如风达","中铁快运","DHL","FedEx","UPS","TNT",
        "丰巢","速递易","驿小哥","驿收发","驿站管家","妈妈驿站","兔喜","喵站","小兵驿站","快宝","快递超市","邻里驿站","熊猫快收","蓝店","蜜罐","近邻宝","日日顺","乐收","云柜","富友收件宝","中邮速递易","菜根谭",
        "parcel","package","courier","delivery","shipment","tracking","pickup","pick up","locker","express","collect your"
    });
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("parcel-sms-filter",0);}
    static final class Rules {
        final boolean enabled; final List<String> keywords; final String text; private final List<String> needles=new ArrayList<>();
        Rules(boolean enabled,String text){this.enabled=enabled;this.keywords=parse(text);this.text=String.join("\n",keywords);if(enabled&&keywords.isEmpty())throw new IllegalArgumentException("开启过滤时请至少填写一个关键词");for(String keyword:keywords)needles.add(normalize(keyword));}
        boolean matches(ParcelSms.Message source){if(!enabled||source.imageSha256!=null)return true;String body=normalize(source.body),sender=normalize(source.sender);for(String keyword:needles)if(body.contains(keyword)||sender.contains(keyword))return true;return false;}
        List<ParcelSms.Message> select(List<ParcelSms.Message> sources){List<ParcelSms.Message> result=new ArrayList<>();for(ParcelSms.Message source:sources)if(matches(source))result.add(source);return result;}
    }
    static Rules load(Context context){SharedPreferences p=prefs(context);return new Rules(p.getBoolean("enabled",true),p.getString("keywords",DEFAULT_KEYWORDS));}
    static void save(Context context,boolean enabled,String text){Rules rules=new Rules(enabled,text);if(!prefs(context).edit().putBoolean("enabled",enabled).putString("keywords",rules.text).commit())throw new IllegalStateException("无法保存短信过滤设置");}
    static List<String> parse(String text){if(text==null||text.length()>MAX_TEXT_LENGTH)throw new IllegalArgumentException("关键词内容最多 16000 字符");Map<String,String> unique=new LinkedHashMap<>();for(String part:text.split("[\\r\\n,，;；]+")){String keyword=Normalizer.normalize(part,Normalizer.Form.NFKC).trim();if(keyword.isEmpty())continue;if(keyword.length()>MAX_KEYWORD_LENGTH||keyword.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("每个关键词最多 80 字符，不能包含控制字符");unique.putIfAbsent(normalize(keyword),keyword);if(unique.size()>MAX_KEYWORDS)throw new IllegalArgumentException("最多设置 256 个关键词");}return Collections.unmodifiableList(new ArrayList<>(unique.values()));}
    private static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
}
