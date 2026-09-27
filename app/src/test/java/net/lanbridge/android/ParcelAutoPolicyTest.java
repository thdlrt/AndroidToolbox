package net.lanbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class ParcelAutoPolicyTest {
 @Test public void opensAreCoalescedButLaterOpenRuns(){ParcelAutoPolicy p=new ParcelAutoPolicy();assertFalse(p.begin(false,false,0));assertTrue(p.begin(true,false,0));assertFalse(p.begin(true,true,1));p.end();assertFalse(p.begin(true,false,59000));assertTrue(p.begin(true,false,60000));p.end();assertTrue(p.begin(true,true,60001));}
 @Test public void failureReleasesGateForExplicitRetry(){ParcelAutoPolicy p=new ParcelAutoPolicy();assertTrue(p.begin(true,false,10));p.end();assertTrue(p.begin(true,true,11));}
 @Test public void rangesIncludeTodayAcrossMonthsAndDst(){ZoneId zone=ZoneId.of("Asia/Shanghai");long now=ZonedDateTime.of(2026,3,1,8,0,0,0,zone).toInstant().toEpochMilli();assertEquals(LocalDate.of(2026,2,27).atStartOfDay(zone).toInstant().toEpochMilli(),ParcelAutoPolicy.start(now,3,zone));assertEquals(LocalDate.of(2026,3,1).atStartOfDay(zone).toInstant().toEpochMilli(),ParcelAutoPolicy.start(now,1,zone));ZoneId ny=ZoneId.of("America/New_York");long dst=ZonedDateTime.of(2026,3,9,8,0,0,0,ny).toInstant().toEpochMilli();assertEquals(LocalDate.of(2026,3,7).atStartOfDay(ny).toInstant().toEpochMilli(),ParcelAutoPolicy.start(dst,3,ny));}
 @Test public void dayBounds(){assertEquals(93,ParcelAutoPolicy.days(93));for(int d:new int[]{-1,0,94,999})try{ParcelAutoPolicy.days(d);fail();}catch(IllegalArgumentException expected){}}
}
