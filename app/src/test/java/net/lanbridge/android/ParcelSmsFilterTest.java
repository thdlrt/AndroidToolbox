package net.lanbridge.android;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ParcelSmsFilterTest {
    private ParcelSms.Message sms(String sender,String body){return new ParcelSms.Message("test",1,sender,body);}
    @Test public void broadDefaultKeepsStationCarrierLockerAndEnglishNotifications(){
        ParcelSmsFilter.Rules rules=new ParcelSmsFilter.Rules(true,ParcelSmsFilter.DEFAULT_KEYWORDS);
        for(String body:List.of("【驿小哥】您的菜鸟速递包裹已到某大学六栋菜根谭快递点，请23:30前凭68-4-2468来取，谢谢。","顺丰通知凭7890来取","兔喜：58-3-9000","丰巢 87654321","【中邮速递易】凭45-123取货","EMS邮件已投递","韵达正在派送","You can PICK UP item A-123 at North","Your parcel is delivered","京东已签收","邻里驿站请领取","快递保管费提醒"))assertTrue(body,rules.matches(sms("10600",body)));
        assertTrue(rules.matches(sms("菜鸟","请凭8765到北门")));
        for(String body:List.of("验证码123456，五分钟内有效","明天中午一起吃饭","您的账户余额为100元","登录密码重置通知"))assertFalse(body,rules.matches(sms("10600",body)));
        assertTrue(rules.keywords.size()>100);
    }
    @Test public void keywordsAreLiteralUnicodeCaseInsensitiveAndDelimited(){
        ParcelSmsFilter.Rules rules=new ParcelSmsFilter.Rules(true,"  自定义站点\nPICK UP， ｆｅｄｅｘ ; pick up；A+B\n");assertEquals(4,rules.keywords.size());
        assertTrue(rules.matches(sms("自定义站点","消息")));assertTrue(rules.matches(sms("10600","Please pick up your item")));assertTrue(rules.matches(sms("ＦＥＤＥＸ","code123")));assertTrue(rules.matches(sms("10600","A+B")));assertFalse(rules.matches(sms("10600","AAAB")));
    }
    @Test public void offAndImagesBypassFilterWithoutChangingSources(){
        ParcelSms.Message other=sms("10600","私人消息"),parcel=sms("10600","取件码1234");List<ParcelSms.Message> sources=List.of(other,parcel);
        assertEquals(sources,new ParcelSmsFilter.Rules(false,"").select(sources));List<ParcelSms.Message> selected=new ParcelSmsFilter.Rules(true,"取件").select(sources);assertEquals(List.of(parcel),selected);assertSame(parcel,selected.get(0));
        ParcelSms.Message image=new ParcelSms.Message("image",1,"图片","用户说明",ParcelSms.sha256(new byte[]{1}));assertTrue(new ParcelSmsFilter.Rules(true,"取件").matches(image));
    }
    @Test public void invalidOrEmptyEnabledRulesDoNotSilentlyFilterEverything(){
        for(String text:List.of(" ","x".repeat(81),"bad\u0000keyword","x".repeat(16001))){try{new ParcelSmsFilter.Rules(true,text);fail();}catch(IllegalArgumentException expected){}}
        StringJoiner many=new StringJoiner("\n");for(int i=0;i<257;i++)many.add("词"+i);try{ParcelSmsFilter.parse(many.toString());fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,ParcelSmsFilter.parse("快递\n快递，快递").size());
    }
}
