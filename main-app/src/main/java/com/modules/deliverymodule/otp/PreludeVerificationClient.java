package com.modules.deliverymodule.otp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ordermodule.exception.OrderRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adattatore server-side di Prelude Verify (stesso fornitore di azzara-be): la chiave non lascia mai il backend.
 * POST /v2/verification (invio) e /v2/verification/check (verifica), Authorization: Bearer.
 *
 * <p>Esiti della creazione (https://docs.prelude.so/verify/v2/api-reference/create-or-retry-a-verification):
 * <ul>
 *   <li>{@code success} (nuova verifica) e {@code retry} (nuovo tentativo sulla verifica aperta): codice inviato;</li>
 *   <li>{@code challenged}: verifica sospetta, consegna limitata a canali non SMS/voce (es. WhatsApp): trattata come
 *       inviata ma loggata in WARN;</li>
 *   <li>{@code shadow_blocked}: regola anti-frode in prova (non applicata): codice inviato, WARN;</li>
 *   <li>{@code blocked}: NESSUN codice inviato. Errore esplicito al cliente in base a {@code reason}.</li>
 * </ul>
 * Ogni risposta viene loggata (id verifica, stato, motivo, canale, request_id) senza numero completo né chiave.
 */
@Component
public class PreludeVerificationClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> SENT = Set.of("success", "retry");
    private static final Set<String> SENT_WITH_WARNING = Set.of("challenged", "shadow_blocked");
    private static final Set<String> CHANNELS = Set.of("sms", "rcs", "whatsapp", "viber", "zalo", "telegram");

    public static final String MSG_UNAVAILABLE = "Verifica del telefono non disponibile al momento: contatta il locale";
    public static final String MSG_INVALID_LINE = "Questo numero non può ricevere SMS: usa un numero di cellulare";
    public static final String MSG_INVALID_NUMBER = "Numero di telefono non valido";
    public static final String MSG_BLOCKED = "Invio del codice bloccato per motivi di sicurezza: riprova più tardi o contatta il locale";
    public static final String MSG_TOO_MANY = "Troppi codici richiesti per questo numero: riprova più tardi";
    public static final String MSG_SEND_FAILED = "Impossibile inviare il codice SMS: riprova tra poco";

    private final OtpSettings settings;
    /** Canale preferito facoltativo (sms, whatsapp, ...): vuoto = instradamento automatico di Prelude. */
    private final String preferredChannel;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Autowired
    public PreludeVerificationClient(OtpSettings settings,
                                     @Value("${prelude.preferred-channel:}") String preferredChannel) {
        this.settings = settings;
        String c = preferredChannel == null ? "" : preferredChannel.trim().toLowerCase();
        if (!c.isEmpty() && !CHANNELS.contains(c)) {
            ErrorLog.logger.warn("OTP: prelude.preferred-channel={} non valido, ignorato (valori: {})", c, CHANNELS);
            c = "";
        }
        this.preferredChannel = c;
    }

    public PreludeVerificationClient(OtpSettings settings) {
        this(settings, "");
    }

    /** Corpo della richiesta di invio (visibile per i test). */
    Map<String, Object> sendBody(String phone, String clientIp) {
        Map<String, Object> body = new HashMap<>();
        body.put("target", Map.of("type", "phone_number", "value", phone));
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equals(clientIp)) {
            body.put("signals", Map.of("ip", clientIp));
        }
        if (!preferredChannel.isEmpty()) {
            body.put("options", Map.of("preferred_channel", preferredChannel));
        }
        return body;
    }

    /**
     * Crea (o ritenta) la verifica: Prelude invia il codice.
     *
     * @throws OrderRejectedException 400 numero non valido, 422 numero/verifica bloccati, 429 troppi tentativi,
     *                                502 risposta inattesa, 503 servizio non configurato o non raggiungibile
     */
    public void sendCode(String phone, String clientIp) {
        requireConfigured();
        JsonNode res = post("/v2/verification", sendBody(phone, clientIp));
        String status = res.path("status").asText("");
        String summary = summary(res);
        if (SENT.contains(status)) {
            ErrorLog.logger.info("Prelude: verifica per {} creata ({})", PhoneNumbers.mask(phone), summary);
            return;
        }
        if (SENT_WITH_WARNING.contains(status)) {
            ErrorLog.logger.warn("Prelude: verifica per {} accettata con riserva ({}): il codice potrebbe non arrivare via SMS",
                    PhoneNumbers.mask(phone), summary);
            return;
        }
        if ("blocked".equals(status)) {
            String reason = res.path("reason").asText("");
            ErrorLog.logger.warn("Prelude: verifica per {} BLOCCATA, nessun SMS inviato ({})", PhoneNumbers.mask(phone), summary);
            throw switch (reason) {
                case "invalid_phone_line" -> new OrderRejectedException(422, MSG_INVALID_LINE);
                case "invalid_phone_number" -> new OrderRejectedException(400, MSG_INVALID_NUMBER);
                case "repeated_attempts" -> new OrderRejectedException(429, MSG_TOO_MANY);
                default -> new OrderRejectedException(422, MSG_BLOCKED);
            };
        }
        ErrorLog.logger.error("Prelude: esito inatteso della verifica per {} ({})", PhoneNumbers.mask(phone), summary);
        throw new OrderRejectedException(502, MSG_SEND_FAILED);
    }

    public boolean isCodeValid(String phone, String code) {
        requireConfigured();
        JsonNode res = post("/v2/verification/check", Map.of(
                "target", Map.of("type", "phone_number", "value", phone),
                "code", code));
        String status = res.path("status").asText("");
        // success | failure (codice errato) | expired_or_not_found (scaduto / nessuna verifica aperta)
        if (!"success".equals(status)) {
            ErrorLog.logger.info("Prelude: verifica codice per {} non riuscita ({})", PhoneNumbers.mask(phone), summary(res));
        }
        return "success".equals(status);
    }

    /** Campi utili alla diagnosi, senza dati personali: id, status, reason, method, channels, request_id. */
    static String summary(JsonNode res) {
        List<String> parts = new ArrayList<>();
        for (String f : List.of("id", "status", "reason", "method")) {
            String v = res.path(f).asText("");
            if (!v.isEmpty()) parts.add(f + "=" + v);
        }
        if (res.path("channels").isArray() && !res.path("channels").isEmpty()) {
            List<String> ch = new ArrayList<>();
            res.path("channels").forEach(n -> ch.add(n.asText()));
            parts.add("channels=" + ch);
        }
        String requestId = res.path("request_id").asText("");
        if (!requestId.isEmpty()) parts.add("request_id=" + requestId);
        return String.join(", ", parts);
    }

    /** Il messaggio d'errore di Prelude può contenere il numero: le sequenze di 6+ cifre vengono mascherate. */
    static String scrub(String s) {
        return s == null ? "" : s.replaceAll("\\+?\\d[\\d ]{4,}\\d", "***");
    }

    private JsonNode post(String path, Map<String, Object> body) {
        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderRejectedException(503, MSG_UNAVAILABLE);
        } catch (Exception e) {
            ErrorLog.logger.error("Prelude non raggiungibile su {} ({}: {})", settings.baseUrl(), e.getClass().getSimpleName(),
                    scrub(e.getMessage()));
            throw new OrderRejectedException(503, MSG_UNAVAILABLE);
        }
        int statusCode = res.statusCode();
        JsonNode node = parse(res.body());
        if (statusCode >= 400) throw httpError(path, statusCode, node);
        if (node == null || !node.isObject()) {
            ErrorLog.logger.error("Prelude {}: risposta non valida (HTTP {})", path, statusCode);
            throw new OrderRejectedException(502, MSG_SEND_FAILED);
        }
        return node;
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** Errore HTTP di Prelude ({code, message, type, param, request_id}) → log diagnostico + errore per il cliente. */
    private static OrderRejectedException httpError(String path, int statusCode, JsonNode n) {
        String code = n != null ? n.path("code").asText("") : "";
        String detail = n == null ? "corpo assente" : "code=" + code + ", type=" + n.path("type").asText("")
                + ", message=" + scrub(n.path("message").asText("")) + ", request_id=" + n.path("request_id").asText("");
        if (statusCode == 401 || statusCode == 403) {
            ErrorLog.logger.error("Prelude {}: HTTP {} chiave API rifiutata, controlla PRELUDE_API_KEY ({})", path, statusCode, detail);
            return new OrderRejectedException(503, MSG_UNAVAILABLE);
        }
        if (statusCode == 429) {
            ErrorLog.logger.warn("Prelude {}: HTTP 429 troppe richieste ({})", path, detail);
            return new OrderRejectedException(429, MSG_TOO_MANY);
        }
        if (code.contains("phone")) {
            ErrorLog.logger.info("Prelude {}: HTTP {} numero rifiutato ({})", path, statusCode, detail);
            return new OrderRejectedException(400, MSG_INVALID_NUMBER);
        }
        if (statusCode >= 500) {
            ErrorLog.logger.error("Prelude {}: HTTP {} errore del servizio ({})", path, statusCode, detail);
            return new OrderRejectedException(503, MSG_UNAVAILABLE);
        }
        // 400/402/422...: richiesta non accettata (es. credito esaurito, paese non abilitato)
        ErrorLog.logger.error("Prelude {}: HTTP {} richiesta rifiutata ({})", path, statusCode, detail);
        return new OrderRejectedException(502, MSG_SEND_FAILED);
    }

    private void requireConfigured() {
        if (!settings.smsConfigured()) throw new OrderRejectedException(503, MSG_UNAVAILABLE);
    }
}
