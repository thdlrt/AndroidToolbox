package net.lanbridge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class PocketSettingsTest {
    @Test public void bareHostPortBecomesHttpOrigin() throws Exception {
        assertEquals("http://192.168.100.110:3081", PocketSettings.normalize("192.168.100.110:3081"));
    }

    @Test public void pastedUrlWithTrailingSlashIsTrimmed() throws Exception {
        assertEquals("http://192.168.100.110:3081", PocketSettings.normalize("  http://192.168.100.110:3081/  "));
    }

    @Test public void httpsDropsItsDefaultPort() throws Exception {
        assertEquals("https://dsh.local", PocketSettings.normalize("https://dsh.local:443/"));
    }

    @Test public void blankAddressAsksForOne() {
        try {
            PocketSettings.normalize("   ");
            fail("expected rejection");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("电脑地址"));
        }
    }

    @Test public void pathIsRejected() {
        try {
            PocketSettings.normalize("http://192.168.100.110:3081/dsh");
            fail("expected rejection");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("端口"));
        }
    }

    @Test public void otherSchemesAreRejected() {
        try {
            PocketSettings.normalize("ftp://192.168.100.110");
            fail("expected rejection");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("地址"));
        }
    }

    @Test public void loginBodyMatchesTheDesktopForm() throws Exception {
        assertEquals("username=dsh-pocket&token=53305117", PocketSettings.formBody("53305117"));
    }

    @Test public void pinIsUrlEncoded() throws Exception {
        assertEquals("username=dsh-pocket&token=a+b%26c", PocketSettings.formBody("a b&c"));
    }

    @Test public void derivesLoginAndStartUrls() throws Exception {
        String origin = PocketSettings.normalize("192.168.100.110:3081");
        assertEquals("http://192.168.100.110:3081/", PocketSettings.startUrl(origin));
        assertEquals("http://192.168.100.110:3081/pocket-login", PocketSettings.loginUrl(origin));
        assertEquals("192.168.100.110:3081", PocketSettings.display(origin));
    }
}
