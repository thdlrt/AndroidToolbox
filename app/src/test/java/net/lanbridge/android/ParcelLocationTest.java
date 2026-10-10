package net.lanbridge.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParcelLocationTest {
    private void expect(String location,String locker,String expectedLocation,String expectedLocker){
        ParcelLocation result=ParcelLocation.of(location,locker);assertEquals(expectedLocation,result.location);assertEquals(expectedLocker,result.locker);
        ParcelLocation twice=ParcelLocation.of(result.location,result.locker);assertEquals(result.location,twice.location);assertEquals(result.locker,twice.locker);
    }
    @Test public void screenshotFormatsConvergeWithoutLosingCabinetDetails(){
        expect("南大仙林菜根谭","室内10号丰巢柜","南大仙林菜根谭","室内10号丰巢柜");
        expect("南大仙林菜根谭室内","9号丰巢柜","南大仙林菜根谭","室内9号丰巢柜");
        expect("南大仙林菜根谭室外39号","丰巢柜","南大仙林菜根谭","室外39号丰巢柜");
        expect("南大仙林菜根谭室内9号","9号丰巢柜","南大仙林菜根谭","室内9号丰巢柜");
        expect("南大仙林菜根谭室内9号丰巢柜","丰巢柜","南大仙林菜根谭","室内9号丰巢柜");
        expect("南大仙林菜根谭9号丰巢柜","","南大仙林菜根谭","9号丰巢柜");
    }
    @Test public void avoidsFuzzyAddressMergesAndPreservesConflicts(){
        expect("仙林路39号","丰巢柜","仙林路39号","丰巢柜");
        expect("南门菜鸟驿站","A柜","南门菜鸟驿站","A柜");
        expect("北门菜鸟驿站","A柜","北门菜鸟驿站","A柜");
        expect("南大仙林菜根谭室内","室外9号丰巢柜","南大仙林菜根谭室内","室外9号丰巢柜");
        expect("室内","9号丰巢柜","室内","9号丰巢柜");
        expect("North Station","Locker A","North Station","Locker A");
    }
    @Test public void normalizesWidthAndChineseSpaces(){expect(" 南大 仙林 菜根谭 室内 ","９ 号丰巢柜","南大仙林菜根谭","室内9号丰巢柜");}
}
