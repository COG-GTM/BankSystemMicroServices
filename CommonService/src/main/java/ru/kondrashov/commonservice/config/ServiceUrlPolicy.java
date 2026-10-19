package ru.kondrashov.commonservice.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guards downstream service URLs so PII and balances never leave the host over plain HTTP.
 */
public final class ServiceUrlPolicy {

    private static final Pattern IPV4_LOOPBACK = Pattern.compile("^127(\\.\\d{1,3}){3}$");

    private ServiceUrlPolicy() {
    }

    public static void check(String name, String urlTemplate, DownstreamServicesProperties.Transport transport) {
        URI uri = parse(name, urlTemplate);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return;
        }
        if (!"http".equals(scheme)) {
            throw new IllegalStateException(name + " must use https:// (got '" + urlTemplate + "')");
        }
        if (transport.isRequireHttps()) {
            throw new IllegalStateException(name + " must use https:// because services.transport.require-https is set (got '"
                    + urlTemplate + "')");
        }
        if (!isLoopback(uri.getHost()) && !transport.isAllowInsecureNonLoopback()) {
            throw new IllegalStateException(name + " points at non-loopback host '" + uri.getHost()
                    + "' over plain http://; use https:// (set SERVICES_ALLOW_INSECURE_HTTP=true only if TLS is"
                    + " enforced by a service mesh)");
        }
    }

    static boolean isLoopback(String host) {
        return isLoopback(host, InetAddress::getAllByName);
    }

    static boolean isLoopback(String host, Resolver resolver) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if ("localhost".equals(h)) {
            return resolvesOnlyToLoopback(h, resolver);
        }
        return "::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h) || IPV4_LOOPBACK.matcher(h).matches();
    }

    private static boolean resolvesOnlyToLoopback(String host, Resolver resolver) {
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!address.isLoopbackAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static URI parse(String name, String urlTemplate) {
        if (urlTemplate == null || urlTemplate.trim().isEmpty()) {
            throw new IllegalStateException(name + " is not configured");
        }
        if (urlTemplate.replace("%s", "").indexOf('%') >= 0) {
            throw new IllegalStateException(name + " may only contain %s placeholders; percent-encoded characters in"
                    + " the base URL are not supported (got '" + urlTemplate + "')");
        }
        try {
            URI uri = new URI(urlTemplate.replace("%s", "x"));
            if (uri.getHost() == null) {
                throw new IllegalStateException(name + " has no host (got '" + urlTemplate + "')");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(name + " is not a valid URL (got '" + urlTemplate + "')", e);
        }
    }
}
