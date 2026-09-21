package net.lanbridge.android;

import java.util.*;

public final class VpnConfig {
    private VpnConfig() {}
    public static String network(String value) {
        try {
            String[] pair=value.split("/"); if(pair.length!=2 || !NasSession.privateIp(pair[0])) throw new IllegalArgumentException();
            int prefix=Integer.parseInt(pair[1]); if(prefix<8 || prefix>32) throw new IllegalArgumentException();
            long n=0;for(String part:pair[0].split("\\.")) n=n*256+Integer.parseInt(part);
            long mask=(0xffffffffL << (32-prefix))&0xffffffffL;
            long end=n|(~mask&0xffffffffL);
            String last=String.format(Locale.ROOT,"%d.%d.%d.%d",end>>>24,(end>>>16)&255,(end>>>8)&255,end&255);
            if((n&mask)!=n || !NasSession.privateIp(last)) throw new IllegalArgumentException();
            return value;
        } catch(Exception e) { throw new IllegalArgumentException("无效的私有 IPv4 网段："+value); }
    }
    public static String nativeConfig(int port,String scope) {
        if(port<1 || port>65535 || !(scope.equals("lan")||scope.equals("all"))) throw new IllegalArgumentException("代理配置无效");
        String result="tunnel:\n  name: lanbridge\n  mtu: 1400\n  ipv4: 198.18.0.1\n  ipv6: 'fdfe:dcba:9876::1'\n  icmp: 'off'\n"+
            "socks5:\n  address: 127.0.0.1\n  port: "+port+"\n  udp: 'udp'\n"+
            "misc:\n  log-level: error\n  log-file: stderr\n  connect-timeout: 20000\n  tcp-read-write-timeout: 300000\n  max-session-count: 128\n  tcp-buffer-size: 65536\n";
        if(scope.equals("all")) result+="mapdns:\n  address: 198.18.0.2\n  port: 53\n  network: 198.19.0.0\n  netmask: 255.255.0.0\n  cache-size: 4096\n";
        return result;
    }
}
