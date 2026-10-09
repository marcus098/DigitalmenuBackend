package com.modules.mainapp.esl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validazione anti-SSRF dell'URL dell'access point OpenEPaperLink.
 *
 * Trade-off: l'AP vive sulla LAN del ristorante, quindi un backend in cloud non può raggiungerlo
 * comunque (serve un deployment on-prem/edge o un tunnel). Per questo:
 *  - solo http/https, niente userinfo, query o fragment;
 *  - sempre rifiutati: loopback, link-local (169.254.0.0/16 = metadata cloud), any-local, multicast;
 *  - se l'host è in {@code esl.ap.allowed-hosts} (lista separata da virgola) è accettato;
 *  - altrimenti è accettato solo se TUTTI gli IP risolti sono privati (10/8, 172.16/12, 192.168/16, fc00::/7)
 *    e {@code esl.ap.allow-private-networks=true} (default). In un deployment cloud impostarlo a false
 *    e usare solo l'allowlist, per non esporre la rete interna del provider (VPC).
 * La validazione è ripetuta prima di ogni push (mitiga DNS rebinding tra salvataggio e invio).
 */
@Component
public class EslApUrlValidator {

    @Value("${esl.ap.allow-private-networks:true}")
    private boolean allowPrivateNetworks;

    @Value("${esl.ap.allowed-hosts:}")
    private String allowedHostsProp;

    public void validate(String apUrl) {
        if (apUrl == null || apUrl.isBlank()) return; // vuoto = usa esl.ap.default-url (configurato dall'operatore)
        URI uri;
        try {
            uri = new URI(apUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("URL AP non valido");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("URL AP: solo http/https");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("URL AP non valido");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL AP: host mancante");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("URL AP: host non risolvibile");
        }

        boolean allowListed = allowedHosts().contains(host);
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                throw new IllegalArgumentException("URL AP: indirizzo non consentito");
            }
            if (!allowListed && !(allowPrivateNetworks && isPrivate(a))) {
                throw new IllegalArgumentException("URL AP: consentiti solo indirizzi LAN privati o host in allowlist");
            }
        }
    }

    private Set<String> allowedHosts() {
        if (allowedHostsProp == null || allowedHostsProp.isBlank()) return Set.of();
        return Arrays.stream(allowedHostsProp.split(","))
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toSet());
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isSiteLocalAddress()) return true; // IPv4 10/8, 172.16/12, 192.168/16 (+ fec0::/10)
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc; // IPv6 ULA fc00::/7
    }
}
