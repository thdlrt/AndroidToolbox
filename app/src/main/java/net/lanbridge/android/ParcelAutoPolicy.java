package net.lanbridge.android;

import java.time.*;

/** Bounds and foreground re-entry gate, independent of Android for deterministic tests. */
final class ParcelAutoPolicy {
    private long lastStart=-1;
    private boolean running;
    static int days(int value){if(value<1||value>93)throw new IllegalArgumentException("读取天数需为 1–93 天");return value;}
    static long start(long now,int days,ZoneId zone){return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(days(days)-1).atStartOfDay(zone).toInstant().toEpochMilli();}
    synchronized boolean begin(boolean enabled,boolean force,long elapsed){if(!enabled||running||!force&&lastStart>=0&&elapsed-lastStart<60000)return false;running=true;lastStart=elapsed;return true;}
    synchronized void end(){running=false;}
}
