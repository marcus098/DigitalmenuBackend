package com.modules.mainapp.payment.sumup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client delle API SumUp (https://api.sumup.com) autenticato con la chiave API DEL LOCALE (Bearer).
 * Isolato in un componente per poterlo mockare nei test. Gli importi SumUp sono decimali (euro), i nostri in centesimi.
 */
@Component
public class SumUpGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public SumUpGateway(@Value("${sumup.api-base-url:https://api.sumup.com}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** Centesimi → importo decimale SumUp (2 decimali, senza errori di virgola mobile). */
    public static BigDecimal toAmount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    /** Importo decimale SumUp → centesimi (arrotondato al centesimo). */
    public static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** GET /v0.1/me: verifica la chiave e restituisce il merchant_code del profilo (null se assente). */
    public String fetchMerchantCode(String apiKey) throws SumUpException {
        JsonNode me = send(apiKey, "GET", "/v0.1/me", null);
        JsonNode code = me.path("merchant_profile").path("merchant_code");
        return code.isTextual() && !code.asText().isBlank() ? code.asText() : null;
    }

    /** POST /v0.1/checkouts con checkout ospitato (hosted_checkout) abilitato. */
    public SumUpCheckout createCheckout(SumUpCredentials creds, String checkoutReference, long amountCents,
                                        String currency, String description, String returnUrl,
                                        String redirectUrl) throws SumUpException {
        ObjectNode body = json.createObjectNode();
        body.put("checkout_reference", checkoutReference);
        body.put("amount", toAmount(amountCents));
        body.put("currency", currency.toUpperCase());
        body.put("merchant_code", creds.merchantCode());
        if (description != null) body.put("description", description);
        if (returnUrl != null) body.put("return_url", returnUrl);
        if (redirectUrl != null) body.put("redirect_url", redirectUrl);
        body.putObject("hosted_checkout").put("enabled", true);
        return parseCheckout(send(creds.apiKey(), "POST", "/v0.1/checkouts", body));
    }

    public SumUpCheckout getCheckout(SumUpCredentials creds, String checkoutId) throws SumUpException {
        return parseCheckout(send(creds.apiKey(), "GET", "/v0.1/checkouts/" + seg(checkoutId), null));
    }

    /** DELETE /v0.1/checkouts/{id}: disattiva un checkout non ancora pagato. */
    public void deactivateCheckout(SumUpCredentials creds, String checkoutId) throws SumUpException {
        send(creds.apiKey(), "DELETE", "/v0.1/checkouts/" + seg(checkoutId), null);
    }

    /** POST /v0.1/me/refund/{transaction_id}: rimborso totale o parziale della transazione. */
    public void refund(SumUpCredentials creds, String transactionId, long amountCents) throws SumUpException {
        ObjectNode body = json.createObjectNode();
        body.put("amount", toAmount(amountCents));
        send(creds.apiKey(), "POST", "/v0.1/me/refund/" + seg(transactionId), body);
    }

    private static String seg(String s) {
        return UriUtils.encodePathSegment(s, StandardCharsets.UTF_8);
    }

    SumUpCheckout parseCheckout(JsonNode n) {
        List<SumUpCheckout.Transaction> txs = new ArrayList<>();
        for (JsonNode t : n.path("transactions")) {
            txs.add(new SumUpCheckout.Transaction(text(t, "id"), text(t, "transaction_code"), text(t, "status"),
                    t.hasNonNull("amount") ? t.get("amount").decimalValue() : null));
        }
        return new SumUpCheckout(text(n, "id"), text(n, "checkout_reference"), text(n, "status"),
                n.hasNonNull("amount") ? n.get("amount").decimalValue() : null, text(n, "currency"),
                text(n, "merchant_code"), text(n, "hosted_checkout_url"), txs);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private JsonNode send(String apiKey, String method, String path, JsonNode body) throws SumUpException {
        if (apiKey == null || apiKey.isBlank()) throw new SumUpException(401, "Chiave API SumUp mancante");
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json");
        if (body != null) {
            req.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        } else {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> resp;
        try {
            resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SumUpException("SumUp non raggiungibile: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SumUpException("Richiesta SumUp interrotta", e);
        }
        int status = resp.statusCode();
        String raw = resp.body();
        if (status < 200 || status >= 300) {
            throw new SumUpException(status, "SumUp HTTP " + status + errorDetail(raw));
        }
        if (raw == null || raw.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(raw);
        } catch (IOException e) {
            throw new SumUpException(status, "Risposta SumUp non valida");
        }
    }

    /** Estrae message/error_code dal corpo d'errore (mai l'intero corpo, per non loggare dati inattesi). */
    private String errorDetail(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            JsonNode n = json.readTree(raw);
            if (n.isArray() && !n.isEmpty()) n = n.get(0);
            String msg = text(n, "message");
            String code = text(n, "error_code");
            if (msg == null && code == null) return "";
            return ": " + (code != null ? code + " " : "") + (msg != null ? msg : "");
        } catch (IOException e) {
            return "";
        }
    }
}
