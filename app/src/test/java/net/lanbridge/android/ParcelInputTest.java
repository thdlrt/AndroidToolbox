package net.lanbridge.android;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ParcelInputTest {
    @Test public void summaryDistinguishesActualFcboxFromEmptyOrOrdinaryInputs(){
        String body="【丰巢】凭取件码62845173至某大学菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/Example";
        ParcelSms.Message source=new ParcelSms.Message("sms",1780000000000L,"106000",body);String summary=ParcelInput.summary(List.of(source));assertTrue(summary.contains("丰巢完整模板=1"));assertTrue(summary.contains("空正文=0"));assertTrue(summary.contains("取件码标签=1"));assertFalse(summary.contains("62845173"));assertFalse(summary.contains("Example"));assertFalse(summary.contains("106000"));assertFalse(summary.contains("某大学"));
        summary=ParcelInput.summary(List.of(new ParcelSms.Message("empty",source.date,"106000",""),new ParcelSms.Message("otp",source.date,"106000","验证码889900")));assertTrue(summary.contains("空正文=1"));assertTrue(summary.contains("丰巢完整模板=0"));assertTrue(summary.contains("来源=2"));assertFalse(summary.contains("889900"));
    }
    @Test public void notesInvisibleFormattingWithoutLeakingOriginalText(){String summary=ParcelInput.summary(List.of(new ParcelSms.Message("format",1780000000000L,"106000","\u200e【丰巢】凭取件码62845173\u200b至测试丰巢柜取件。")));assertTrue(summary.contains("格式字符=2"));assertTrue(summary.contains("丰巢标记=1"));assertTrue(summary.contains("丰巢完整模板=0"));assertFalse(summary.contains("62845173"));}
    @Test public void modelLogAllowsKnownNamesAndOmitsArbitrarySecrets(){assertEquals("deepseek-v4-pro",ParcelInput.safeModel("deepseek-v4-pro"));assertFalse(ParcelInput.safeModel("sk-secret-value").contains("sk-secret"));assertFalse(ParcelInput.safeModel("private-name\nkey=secret").contains("secret"));}
}
