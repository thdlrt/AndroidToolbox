package net.lanbridge.android;

import android.content.Context;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import okhttp3.HttpUrl;

/**
 * One desktop DSH "手机访问" origin and its LAN PIN, encrypted on this device like every other
 * tool credential. The address is pasted once; the PIN is replayed on the server's own login form.
 */
final class PocketSettings {
    static final String USER = "dsh-pocket";
    static final String LOGIN_PATH = "/pocket-login";
    private PocketSettings() {}

    static LoginStore store(Context c) { return new LoginStore(c, "pocket-profile", "pocket-login-v1"); }
    static String origin(Context c) { return store(c).origin(); }
    static boolean configured(Context c) { LoginStore s = store(c); return !s.origin().isEmpty() && s.hasPassword(); }

    static String pin(Context c) {
        try { LoginStore s = store(c); return s.password(s.origin(), USER); }
        catch (Exception e) { return ""; }
    }

    /** Saves the address and PIN, keeping the stored PIN when the field is left empty. */
    static String save(Context c, String address, String password) throws Exception {
        String url = normalize(address);
        LoginStore s = store(c);
        String previous = s.origin();
        String pass = password == null ? "" : password;
        if (pass.isEmpty() && s.hasPassword()) pass = s.password(previous, USER);
        if (pass.isEmpty()) throw new IOException("请填写电脑显示的访问密码");
        s.save(url, USER, pass, "");
        return previous;
    }

    /** Accepts "host:port", "http://host:port/" or a copied URL and returns the bare origin. */
    static String normalize(String input) throws IOException {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IOException("请填写电脑地址，例如 192.168.100.110:3081");
        if (!value.contains("://")) value = "http://" + value;
        HttpUrl url;
        try { url = HttpUrl.get(value); }
        catch (IllegalArgumentException e) { throw new IOException("地址格式不正确，例如 192.168.100.110:3081"); }
        if (!"http".equals(url.scheme()) && !"https".equals(url.scheme()))
            throw new IOException("只支持 http 或 https 地址");
        if (!url.encodedPath().isEmpty() && !"/".equals(url.encodedPath()))
            throw new IOException("只填写地址和端口，不要带路径");
        return url.newBuilder().encodedPath("/").build().toString().replaceAll("/+$", "");
    }

    static String startUrl(String origin) { return origin + "/"; }
    static String loginUrl(String origin) { return origin + LOGIN_PATH; }

    /** The desktop login form posts exactly these two fields. */
    static String formBody(String pin) throws UnsupportedEncodingException {
        return "username=" + URLEncoder.encode(USER, "UTF-8") + "&token=" + URLEncoder.encode(pin, "UTF-8");
    }

    /** Host and non-default port, for the page header. */
    static String display(String origin) {
        try {
            HttpUrl url = HttpUrl.get(origin);
            return url.port() == HttpUrl.defaultPort(url.scheme()) ? url.host() : url.host() + ":" + url.port();
        } catch (Exception e) { return origin; }
    }
}
