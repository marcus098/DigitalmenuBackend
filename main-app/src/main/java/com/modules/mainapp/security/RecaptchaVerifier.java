package com.modules.mainapp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifica server-side dei token Google reCAPTCHA v3 (invisibile, a punteggio) tramite
 * {@code https://www.google.com/recaptcha/api/siteverify}.
 *
 * <ul>
 *     <li>Secret vuoto ({@code app.recaptcha.secret} / env {@code RECAPTCHA_SECRET}) = verifica disattivata (sviluppo).</li>
 *     <li>Un token è valido se Google risponde {@code success=true}, l'azione coincide e lo score è &gt;= soglia.</li>
 *     <li>Google irraggiungibile (timeout, errore di rete, HTTP != 200, risposta illeggibile) o secret rifiutato da
 *     Google = {@link Outcome#UNAVAILABLE}: con {@code app.recaptcha.fail-open=true} (predefinito) la richiesta è
 *     accettata e resta soggetta ai limiti sui tentativi falliti; con {@code false} viene rifiutata.</li>
 * </ul>
 * Token e secret non vengono mai scritti nei log.
 */
@Component
public class RecaptchaVerifier {

    public static final String DEFAULT_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    /** I token reali sono lunghi poche migliaia di caratteri: oltre è sicuramente spazzatura. */
    static final int MAX_TOKEN_LENGTH = 10_000;

    public enum Outcome { DISABLED, VALID, MISSING, INVALID, UNAVAILABLE }

    public record Result(Outcome outcome, Double score) {
        static Result of(Outcome outcome) { return new Result(outcome, null); }
    }

    /** Trasporto HTTP (POST form-urlencoded) separato per poterlo sostituire nei test. */
    @FunctionalInterface
    interface Transport {
        TransportResponse post(String url, String formBody, Duration timeout) throws IOException, InterruptedException;
    }

    record TransportResponse(int status, String body) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String secret;
    private final double minScore;
    private final Duration timeout;
    private final boolean failOpen;
    private final Set<String> allowedHostnames;
    private final String verifyUrl;
    private final Transport transport;

    @Autowired
    public RecaptchaVerifier(@Value("${app.recaptcha.secret:${RECAPTCHA_SECRET:}}") String secret,
                             @Value("${app.recaptcha.min-score:0.5}") double minScore,
                             @Value("${app.recaptcha.timeout-ms:3000}") long timeoutMs,
                             @Value("${app.recaptcha.fail-open:true}") boolean failOpen,
                             @Value("${app.recaptcha.allowed-hostnames:}") String allowedHostnames,
                             @Value("${app.recaptcha.verify-url:" + DEFAULT_VERIFY_URL + "}") String verifyUrl) {
        this(secret, minScore, timeoutMs, failOpen, allowedHostnames, verifyUrl, null);
    }

    RecaptchaVerifier(String secret, double minScore, long timeoutMs, boolean failOpen, String allowedHostnames,
                      String verifyUrl, Transport transport) {
        this.secret = secret == null ? "" : secret.trim();
        this.minScore = minScore;
        this.timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 3000);
        this.failOpen = failOpen;
        this.allowedHostnames = allowedHostnames == null ? Set.of() : Arrays.stream(allowedHostnames.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.verifyUrl = verifyUrl == null || verifyUrl.isBlank() ? DEFAULT_VERIFY_URL : verifyUrl.trim();
        this.transport = transport != null ? transport : httpTransport(this.timeout);
    }

    public boolean isEnabled() {
        return !secret.isEmpty();
    }

    /** true se l'esito permette di proseguire (verifica disattivata, token valido o Google giù con fail-open). */
    public boolean accepts(Result result) {
        return switch (result.outcome()) {
            case DISABLED, VALID -> true;
            case UNAVAILABLE -> failOpen;
            default -> false;
        };
    }

    public Result verify(String token, String expectedAction, String remoteIp) {
        if (!isEnabled()) return Result.of(Outcome.DISABLED);
        if (token == null || token.isBlank()) return Result.of(Outcome.MISSING);
        if (token.length() > MAX_TOKEN_LENGTH) return Result.of(Outcome.INVALID);

        StringBuilder form = new StringBuilder()
                .append("secret=").append(enc(secret))
                .append("&response=").append(enc(token.trim()));
        if (remoteIp != null && !remoteIp.isBlank()) form.append("&remoteip=").append(enc(remoteIp));

        TransportResponse res;
        try {
            res = transport.post(verifyUrl, form.toString(), timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ErrorLog.logger.warn("reCAPTCHA: verifica interrotta");
            return Result.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            ErrorLog.logger.warn("reCAPTCHA: Google non raggiungibile ({})", e.getClass().getSimpleName());
            return Result.of(Outcome.UNAVAILABLE);
        }
        if (res == null || res.status() != 200 || res.body() == null) {
            ErrorLog.logger.warn("reCAPTCHA: risposta inattesa da siteverify (HTTP {})", res == null ? -1 : res.status());
            return Result.of(Outcome.UNAVAILABLE);
        }

        JsonNode node;
        try {
            node = JSON.readTree(res.body());
        } catch (Exception e) {
            ErrorLog.logger.warn("reCAPTCHA: risposta di siteverify non leggibile");
            return Result.of(Outcome.UNAVAILABLE);
        }
        if (node == null || !node.isObject()) return Result.of(Outcome.UNAVAILABLE);

        if (!node.path("success").asBoolean(false)) {
            String codes = node.path("error-codes").toString();
            if (codes.contains("invalid-input-secret") || codes.contains("missing-input-secret")) {
                // Errore di configurazione nostro, non del client: trattato come servizio non disponibile
                ErrorLog.logger.error("reCAPTCHA: secret rifiutato da Google ({}): controlla app.recaptcha.secret", codes);
                return Result.of(Outcome.UNAVAILABLE);
            }
            ErrorLog.logger.info("reCAPTCHA: token rifiutato da Google ({})", codes);
            return Result.of(Outcome.INVALID);
        }

        Double score = node.hasNonNull("score") ? node.path("score").asDouble() : null;
        String action = node.path("action").asText("");
        if (expectedAction != null && !expectedAction.equals(action)) {
            ErrorLog.logger.info("reCAPTCHA: azione inattesa '{}' (attesa '{}')", action, expectedAction);
            return new Result(Outcome.INVALID, score);
        }
        if (score == null || score < minScore) {
            ErrorLog.logger.info("reCAPTCHA: score {} sotto la soglia {} (azione {})", score, minScore, expectedAction);
            return new Result(Outcome.INVALID, score);
        }
        if (!allowedHostnames.isEmpty()) {
            String host = node.path("hostname").asText("").toLowerCase(Locale.ROOT);
            if (!allowedHostnames.contains(host)) {
                ErrorLog.logger.info("reCAPTCHA: hostname '{}' non ammesso", host);
                return new Result(Outcome.INVALID, score);
            }
        }
        return new Result(Outcome.VALID, score);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Transport httpTransport(Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return (url, formBody, t) -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(t)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new TransportResponse(res.statusCode(), res.body());
        };
    }
}
