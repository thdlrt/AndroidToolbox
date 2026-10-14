package net.lanbridge.android;
import org.junit.Test;
import java.nio.file.*;
import java.io.File;
import java.time.LocalDate;
import static org.junit.Assert.*;
public class ParcelDebugTest {
 @Test public void rollingRangesFollowTodayAcrossMidnightAndMonth(){LocalDate yesterday=LocalDate.of(2026,10,13),today=yesterday.plusDays(1);assertArrayEquals(new LocalDate[]{today,today},ParcelDateRange.rolling(today,1));assertArrayEquals(new LocalDate[]{today.minusDays(2),today},ParcelDateRange.rolling(today,3));assertArrayEquals(new LocalDate[]{LocalDate.of(2026,1,30),LocalDate.of(2026,2,1)},ParcelDateRange.rolling(LocalDate.of(2026,2,1),3));}
 @Test public void runtimeLogRotatesAndClearsWithoutReordering()throws Exception{File directory=Files.createTempDirectory("runtime-log-test").toFile();RuntimeLog log=new RuntimeLog(directory,512);try{for(int i=0;i<50;i++)log.append("扫描","事件="+i+"；读取=2；待AI=1");String text=log.read();assertTrue(text.contains("事件=49"));assertFalse(text.contains("事件=0；"));assertTrue(new File(directory,"events.log").length()<=512);assertTrue(new File(directory,"events.previous.log").length()<=512);log.clear();assertEquals("暂无运行日志",log.read());}finally{for(File file:directory.listFiles())file.delete();directory.delete();}}
 @Test public void fcboxNotificationPassesWithoutVisitingEmbeddedLink(){ParcelSms.Message source=new ParcelSms.Message("fcbox",1,"10600","【丰巢】凭取件码87654321至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/Synthetic");assertTrue(new ParcelSmsFilter.Rules(true,ParcelSmsFilter.DEFAULT_KEYWORDS).matches(source));}
}
