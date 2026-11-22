package com.modules.deliverymodule.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.deliverymodule.service.DeliveryRules;
import com.modules.ordermodule.exception.OrderRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Proxy di geocodifica verso Nominatim (OpenStreetMap), nel rispetto della policy d'uso: User-Agent dedicato con
 * contatto, massimo 1 richiesta al secondo per l'intero backend, cache dei risultati, niente autocompletamento
 * (il client cerca con pulsante/invio). Per passare a un fornitore a pagamento basta sostituire questa classe.
 */
@Service
public class GeocodingService {

    public record GeoResult(String label, double lat, double lng, String street, String houseNumber, String city, String postcode) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final long MIN_INTERVAL_MS = 1100;
    private static final long MAX_WAIT_MS = 4000;
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int CACHE_MAX = 5000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;
    private final String userAgent;
    private final String email;

    private final Object throttleLock = new Object();
    private long nextSlotAt = 0;

    private record Cached(long at, Object value) {}
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_MAX;
        }
    });

    public GeocodingService(@Value("${app.geo.nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
                            @Value("${app.geo.nominatim.contact-email:}") String contactEmail,
                            @Value("${app.geo.nominatim.user-agent:RestaurantFlow/1.0}") String userAgent) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.email = contactEmail == null ? "" : contactEmail.trim();
        this.userAgent = userAgent + (this.email.isEmpty() ? "" : " (" + this.email + ")");
        if (this.email.isEmpty()) {
            ErrorLog.logger.warn("Geocodifica: app.geo.nominatim.contact-email non impostata (la policy Nominatim chiede un contatto)");
        }
    }

    /**
     * Max 5 risultati vicini al punto di partenza: se l'origine è nota la ricerca è limitata a un riquadro di
     * {@code radiusKm} (+2 km) intorno ad essa; altrimenti all'Italia.
     */
    @SuppressWarnings("unchecked")
    public List<GeoResult> search(String q, Double originLat, Double originLng, double radiusKm) {
        String query = q == null ? "" : q.strip().replaceAll("\\s+", " ");
        if (query.length() < 3) throw new OrderRejectedException(400, "Scrivi almeno 3 caratteri dell'indirizzo");
        if (query.length() > 200) query = query.substring(0, 200);
        boolean hasOrigin = DeliveryRules.validCoordinates(originLat, originLng);
        double r = Math.max(5, radiusKm + 2);
        StringBuilder url = new StringBuilder(baseUrl).append("/search?format=jsonv2&addressdetails=1&limit=8")
                .append("&accept-language=it&countrycodes=it&q=").append(enc(query));
        String key = "s|" + query.toLowerCase(Locale.ROOT);
        if (hasOrigin) {
            double dLat = r / 111.0;
            double dLng = r / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(originLat))));
            url.append("&bounded=1&viewbox=")
                    .append(fmt(originLng - dLng)).append(',').append(fmt(originLat + dLat)).append(',')
                    .append(fmt(originLng + dLng)).append(',').append(fmt(originLat - dLat));
            key += "|" + fmt(originLat) + "," + fmt(originLng) + "," + (int) r;
        }
        Object hit = cached(key);
        if (hit != null) return (List<GeoResult>) hit;

        JsonNode arr = get(url.toString());
        List<GeoResult> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                GeoResult g = toResult(n);
                if (g == null) continue;
                if (hasOrigin && DeliveryRules.haversineKm(originLat, originLng, g.lat(), g.lng()) > r * 1.5) continue;
                if (out.stream().anyMatch(o -> o.label().equalsIgnoreCase(g.label()))) continue;
                out.add(g);
                if (out.size() == 5) break;
            }
            if (hasOrigin) {
                out.sort(Comparator.comparingDouble(g -> DeliveryRules.haversineKm(originLat, originLng, g.lat(), g.lng())));
            }
        }
        List<GeoResult> result = List.copyOf(out);
        cache.put(key, new Cached(System.currentTimeMillis(), result));
        return result;
    }

    public GeoResult reverse(double lat, double lng) {
        if (!DeliveryRules.validCoordinates(lat, lng)) throw new OrderRejectedException(400, "Coordinate non valide");
        // ~11 m: punti vicinissimi condividono la cache
        String key = "r|" + String.format(Locale.ROOT, "%.4f,%.4f", lat, lng);
        Object hit = cached(key);
        if (hit != null) return (GeoResult) hit;
        JsonNode n = get(baseUrl + "/reverse?format=jsonv2&addressdetails=1&zoom=18&accept-language=it&lat="
                + fmt(lat) + "&lon=" + fmt(lng));
        GeoResult g = n == null || n.has("error") ? null : toResult(n);
        if (g == null) throw new OrderRejectedException(404, "Nessun indirizzo trovato in questo punto");
        // la posizione resta quella del pin, l'indirizzo è quello più vicino
        GeoResult out = new GeoResult(g.label(), lat, lng, g.street(), g.houseNumber(), g.city(), g.postcode());
        cache.put(key, new Cached(System.currentTimeMillis(), out));
        return out;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Object cached(String key) {
        Cached c = cache.get(key);
        if (c == null) return null;
        if (System.currentTimeMillis() - c.at() > CACHE_TTL_MS) {
            cache.remove(key);
            return null;
        }
        return c.value();
    }

    static GeoResult toResult(JsonNode n) {
        if (n == null || !n.hasNonNull("lat") || !n.hasNonNull("lon")) return null;
        double lat, lng;
        try {
            lat = Double.parseDouble(n.get("lat").asText());
            lng = Double.parseDouble(n.get("lon").asText());
        } catch (NumberFormatException e) {
            return null;
        }
        JsonNode a = n.path("address");
        String street = first(a, "road", "pedestrian", "footway", "square", "path", "residential", "place");
        String house = first(a, "house_number");
        String city = first(a, "city", "town", "village", "municipality", "hamlet", "suburb");
        String postcode = first(a, "postcode");
        String label;
        if (street != null) {
            label = street + (house != null ? " " + house : "") + (city != null ? ", " + city : "");
        } else {
            String dn = n.path("display_name").asText("");
            label = dn.contains(",") ? String.join(",", Arrays.asList(dn.split(",")).subList(0, Math.min(3, dn.split(",").length))).trim() : dn;
        }
        if (label == null || label.isBlank()) return null;
        return new GeoResult(label, lat, lng, street, house, city, postcode);
    }

    private static String first(JsonNode a, String... keys) {
        for (String k : keys) {
            String v = a.path(k).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private JsonNode get(String url) {
        throttle();
        String full = url + (email.isEmpty() ? "" : "&email=" + enc(email));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(full)).timeout(TIMEOUT)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "it")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429 || res.statusCode() == 503) {
                ErrorLog.logger.warn("Nominatim: HTTP {} (limite superato)", res.statusCode());
                throw new OrderRejectedException(503, "Ricerca indirizzi momentaneamente non disponibile, riprova tra poco");
            }
            if (res.statusCode() >= 400) {
                ErrorLog.logger.warn("Nominatim: HTTP {}", res.statusCode());
                throw new OrderRejectedException(502, "Ricerca indirizzi non riuscita");
            }
            return JSON.readTree(res.body());
        } catch (OrderRejectedException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderRejectedException(503, "Ricerca indirizzi non disponibile");
        } catch (Exception e) {
            ErrorLog.logger.warn("Nominatim non raggiungibile ({})", e.getClass().getSimpleName());
            throw new OrderRejectedException(503, "Ricerca indirizzi non disponibile, riprova tra poco");
        }
    }

    /** Limite globale 1 richiesta/s: prenota il prossimo turno; se l'attesa supera 4 s → 503. */
    private void throttle() {
        long wait;
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextSlotAt);
            wait = slot - now;
            if (wait > MAX_WAIT_MS) {
                throw new OrderRejectedException(503, "Troppe ricerche in corso, riprova tra qualche secondo");
            }
            nextSlotAt = slot + MIN_INTERVAL_MS;
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OrderRejectedException(503, "Ricerca indirizzi non disponibile");
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.6f", d);
    }
}
