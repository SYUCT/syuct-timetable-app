package top.syuct.timetable;

import java.net.URI;

/** Exact-origin policy shared by navigation and capture. No wildcard trust. */
public final class Policy {
    public static final String LOCAL = "https://appassets.androidplatform.net/";
    public static boolean school(String url, String host) {
        try {
            URI u = URI.create(url);
            return (host.equals("jws.syuct.edu.cn") || host.equals("geims.syuct.edu.cn"))
                && "https".equalsIgnoreCase(u.getScheme()) && host.equalsIgnoreCase(u.getHost())
                && u.getUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) { return false; }
    }
    public static boolean local(String url) {
        try {
            URI u = URI.create(url);
            return "https".equals(u.getScheme()) && "appassets.androidplatform.net".equals(u.getHost())
                && u.getUserInfo() == null && u.getPort() == -1;
        } catch (Exception e) { return false; }
    }
}
