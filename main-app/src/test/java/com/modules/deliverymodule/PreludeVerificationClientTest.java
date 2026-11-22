package com.modules.deliverymodule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.deliverymodule.otp.OtpSettings;
import com.modules.deliverymodule.otp.PreludeVerificationClient;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client Prelude contro un server HTTP locale finto (nessuna chiamata a api.prelude.dev, nessun SMS reale):
 * forma della richiesta, esiti success/retry/challenged/blocked, errori HTTP resi espliciti al cliente.
 */
class PreludeVerificationClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().getPath());
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PreludeVerificationClient client(String preferredChannel) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new PreludeVerificationClient(new OtpSettings("sk_test_prelude", base, false, "", "", new String[0]),
                preferredChannel);
    }

    private void respond(int http, String body) {
        status = http;
        response = body;
    }

    @Test
    void sendPostsE164TargetWithBearerKeyAndIpSignal() throws Exception {
        respond(200, "{\"id\":\"vrf_1\",\"status\":\"success\",\"method\":\"message\",\"channels\":[\"sms\"]}");
        client("").sendCode("+393331234567", "203.0.113.7");

        assertEquals("/v2/verification", lastPath.get());
        assertEquals("Bearer sk_test_prelude", lastAuth.get());
        JsonNode body = JSON.readTree(lastBody.get());
        assertEquals("phone_number", body.path("target").path("type").asText());
        assertEquals("+393331234567", body.path("target").path("value").asText());
        assertEquals("203.0.113.7", body.path("signals").path("ip").asText());
        assertTrue(body.path("options").isMissingNode(), "senza canale preferito nessuna option");
    }

    @Test
    void unknownIpIsNotSentAndPreferredChannelIsPassed() throws Exception {
        respond(200, "{\"id\":\"vrf_1\",\"status\":\"success\"}");
        client("SMS").sendCode("+393331234567", "unknown");
        JsonNode body = JSON.readTree(lastBody.get());
        assertTrue(body.path("signals").isMissingNode());
        assertEquals("sms", body.path("options").path("preferred_channel").asText());
    }

    @Test
    void invalidPreferredChannelIsIgnored() throws Exception {
        respond(200, "{\"status\":\"success\"}");
        client("carrier-pigeon").sendCode("+393331234567", null);
        assertTrue(JSON.readTree(lastBody.get()).path("options").isMissingNode());
    }

    @Test
    void retryChallengedAndShadowBlockedCountAsSent() {
        for (String s : new String[]{"retry", "challenged", "shadow_blocked"}) {
            respond(200, "{\"id\":\"vrf_1\",\"status\":\"" + s + "\"}");
            assertDoesNotThrow(() -> client("").sendCode("+393331234567", null), s);
        }
    }

    @Test
    void blockedIsAnExplicitErrorNotASuccess() {
        respond(200, "{\"id\":\"vrf_1\",\"status\":\"blocked\",\"reason\":\"invalid_phone_line\"}");
        OrderRejectedException e = assertThrows(OrderRejectedException.class, () -> client("").sendCode("+390301234567", null));
        assertEquals(422, e.getStatus());
        assertEquals(PreludeVerificationClient.MSG_INVALID_LINE, e.getMessage());

        respond(200, "{\"status\":\"blocked\",\"reason\":\"repeated_attempts\"}");
        assertEquals(429, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());

        respond(200, "{\"status\":\"blocked\",\"reason\":\"suspicious\"}");
        assertEquals(422, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());

        respond(200, "{\"status\":\"blocked\",\"reason\":\"invalid_phone_number\"}");
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());
    }

    @Test
    void unexpectedStatusIs502() {
        respond(200, "{\"status\":\"something_new\"}");
        assertEquals(502, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());
    }

    @Test
    void rejectedApiKeyIs503() {
        respond(401, "{\"code\":\"invalid_api_key\",\"message\":\"Invalid API key\",\"type\":\"unauthorized\",\"request_id\":\"req_1\"}");
        OrderRejectedException e = assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null));
        assertEquals(503, e.getStatus());
        assertEquals(PreludeVerificationClient.MSG_UNAVAILABLE, e.getMessage());
    }

    @Test
    void httpErrorsMapToClearStatuses() {
        respond(429, "{\"code\":\"too_many_requests\"}");
        assertEquals(429, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());

        respond(400, "{\"code\":\"invalid_phone_number\",\"message\":\"+393331234567 is not valid\"}");
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());

        respond(402, "{\"code\":\"insufficient_balance\"}");
        assertEquals(502, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());

        respond(500, "oops");
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> client("").sendCode("+393331234567", null)).getStatus());
    }

    @Test
    void unreachableServiceIs503() {
        PreludeVerificationClient c = new PreludeVerificationClient(
                new OtpSettings("k", "http://127.0.0.1:1", false, "", "", new String[0]), "");
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> c.sendCode("+393331234567", null)).getStatus());
    }

    @Test
    void checkSucceedsOnlyOnSuccess() throws Exception {
        respond(200, "{\"id\":\"vrf_1\",\"status\":\"success\"}");
        assertTrue(client("").isCodeValid("+393331234567", "123456"));
        assertEquals("/v2/verification/check", lastPath.get());
        JsonNode body = JSON.readTree(lastBody.get());
        assertEquals("+393331234567", body.path("target").path("value").asText());
        assertEquals("123456", body.path("code").asText());

        respond(200, "{\"status\":\"failure\"}");
        assertFalse(client("").isCodeValid("+393331234567", "000000"));
        respond(200, "{\"status\":\"expired_or_not_found\"}");
        assertFalse(client("").isCodeValid("+393331234567", "000000"));
    }

    @Test
    void notConfiguredIs503WithoutCallingPrelude() {
        PreludeVerificationClient c = new PreludeVerificationClient(new OtpSettings("", "http://127.0.0.1:1", false, "", "", new String[0]));
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> c.sendCode("+393331234567", null)).getStatus());
        assertNull(lastPath.get());
    }

    @Test
    void italianNumberWithCountryCodeWithoutPlusIsNormalized() {
        assertEquals("+393331234567", com.modules.deliverymodule.otp.PhoneNumbers.normalize("39 333 1234567"));
        // 10 cifre che iniziano con 393: numero nazionale (prefisso TIM 393), non prefisso internazionale
        assertEquals("+393931234567", com.modules.deliverymodule.otp.PhoneNumbers.normalize("3931234567"));
    }
}
