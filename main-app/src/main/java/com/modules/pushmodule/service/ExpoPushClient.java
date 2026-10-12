package com.modules.pushmodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.pushmodule.repository.PushTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invio tramite Expo Push API (Expo inoltra a FCM/APNs). Chiamato dal thread asincrono: non solleva mai eccezioni.
 * Con {@code app.push.enabled=false} i messaggi vengono solo loggati (sviluppo/test).
 */
@Component
public class ExpoPushClient {

    static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    static final int BATCH_SIZE = 100; // limite Expo per richiesta
    private static final int MAX_TITLE = 100;
    private static final int MAX_BODY = 240;

    @Autowired
    private PushTokenRepository tokenRepository;

    @Value("${app.push.enabled:true}")
    private boolean enabled;

    // Facoltativo: serve solo se sul progetto Expo è attiva "Enhanced push security"
    @Value("${app.push.expo-access-token:}")
    private String expoAccessToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpoPushClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Stesso contenuto a tutti i token indicati, a blocchi di 100. */
    public void send(List<String> tokens, PushContent content) {
        if (tokens == null || tokens.isEmpty() || content == null) return;
        List<Map<String, Object>> messages = tokens.stream().distinct().map(t -> toExpoMessage(t, content)).toList();
        if (!enabled) {
            // niente token nei log: basta sapere quanti dispositivi avrebbero ricevuto cosa
            ErrorLog.logger.info("PUSH (simulazione) a {} dispositivi: {} - {}", messages.size(), content.title(), content.body());
            return;
        }
        for (int i = 0; i < messages.size(); i += BATCH_SIZE) {
            sendBatch(messages.subList(i, Math.min(i + BATCH_SIZE, messages.size())));
        }
    }

    private void sendBatch(List<Map<String, Object>> batch) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (expoAccessToken != null && !expoAccessToken.isBlank()) {
                headers.setBearerAuth(expoAccessToken.trim());
            }
            ResponseEntity<String> response = restTemplate.postForEntity(
                    EXPO_PUSH_URL, new HttpEntity<>(objectMapper.writeValueAsString(batch), headers), String.class);

            // I ticket tornano nello stesso ordine dei messaggi inviati
            JsonNode tickets = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody()).path("data");
            List<String> deadTokens = new ArrayList<>();
            for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                JsonNode ticket = tickets.get(i);
                if (!"error".equals(ticket.path("status").asText())) continue;
                String error = ticket.path("details").path("error").asText("");
                if ("DeviceNotRegistered".equals(error)) {
                    deadTokens.add((String) batch.get(i).get("to"));
                } else {
                    ErrorLog.logger.warn("PUSH: errore Expo ({}): {}", error, ticket.path("message").asText());
                }
            }
            if (!deadTokens.isEmpty()) {
                int removed = tokenRepository.deleteByTokens(deadTokens);
                if (removed > 0) ErrorLog.logger.info("PUSH: rimossi {} token non più registrati", removed);
            }
        } catch (Exception e) {
            ErrorLog.logger.warn("PUSH: chiamata Expo Push API fallita: {}", e.getMessage());
        }
    }

    static Map<String, Object> toExpoMessage(String token, PushContent c) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("to", token);
        msg.put("title", truncate(c.title(), MAX_TITLE));
        msg.put("body", truncate(c.body(), MAX_BODY));
        msg.put("data", c.data() != null ? c.data() : Map.of());
        msg.put("sound", "default");
        msg.put("priority", "high");
        msg.put("channelId", c.channelId());
        return msg;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
