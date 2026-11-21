package io.github.kirchrath.sai.tools;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Prueft URLs fuer den Web-Fetch, um Zugriffe auf interne Dienste (SSRF) zu verhindern.
 */
public class UrlGuard {

    private final boolean allowPrivateNetworks;
    private final Set<String> blockedHosts;

    public UrlGuard(boolean allowPrivateNetworks, Set<String> blockedHosts) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        this.blockedHosts = blockedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * @return die validierte URI
     * @throws IllegalArgumentException wenn die URL nicht erlaubt ist
     */
    public URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Nur http und https sind erlaubt");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL ohne Host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Zugangsdaten in der URL sind nicht erlaubt");
        }
        if (blockedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Host ist gesperrt");
        }
        if (!allowPrivateNetworks) {
            try {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (isInternal(address)) {
                        throw new IllegalArgumentException("Zugriff auf interne Adressen ist nicht erlaubt");
                    }
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Host nicht aufloesbar: " + host);
            }
        }
        return uri;
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // 100.64.0.0/10 (Carrier-grade NAT), 0.0.0.0/8
            return (first == 100 && second >= 64 && second <= 127) || first == 0;
        }
        // fc00::/7 (Unique Local Addresses)
        return (b[0] & 0xfe) == 0xfc;
    }
}
