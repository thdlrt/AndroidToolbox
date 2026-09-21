package net.lanbridge.android;

import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public class LiveNasTest {
    @Test public void fnConnectAndConfiguredSshServer() throws Exception {
        String password=System.getenv("LANBRIDGE_LIVE_PASSWORD");Assume.assumeTrue(password!=null&&!password.isEmpty());
        try(NasSession session=new NasSession(System.getenv("LANBRIDGE_LIVE_ORIGIN"))) {
            session.login(System.getenv("LANBRIDGE_LIVE_USER"),password,"all");
            for(int i=0;i<2;i++) {
                String target=System.getenv("LANBRIDGE_LIVE_TARGET");Assume.assumeTrue(target!=null&&!target.isEmpty());
                try(WsChannel ws=session.openTunnel(target,22,"all")) {
                    Object message=ws.receive(20);assertTrue(message instanceof byte[]);
                    assertTrue(new String((byte[])message,StandardCharsets.UTF_8).startsWith("SSH-"));
                }
                session.maintain();
            }
        }
    }
}
