package com.modules.translationmodule.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.modules.common.logs.errorlog.ErrorLog;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Client verso l'API HTTP di Ollama (POST {base}/api/chat, stream=false).
 * Chiamato solo dal backend: il browser non conosce l'indirizzo di Ollama.
 */
@Component
public class OllamaClient {

    @Value("${app.ai.enabled:false}")
    private boolean enabled;
    @Value("${app.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;
    @Value("${app.ai.ollama.username:}")
    private String username;
    @Value("${app.ai.ollama.password:}")
    private String password;
    @Value("${app.ai.model.translate:qwen2.5:7b}")
    private String translateModel;
    @Value("${app.ai.timeout-seconds:120}")
    private int timeoutSeconds;
    @Value("${app.ai.num-ctx:8192}")
    private int numCtx;

    private RestClient restClient;

    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String c) { return new ChatMessage("system", c); }
        public static ChatMessage user(String c) { return new ChatMessage("user", c); }
    }

    @PostConstruct
    void init() {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", ""))
                .requestFactory(factory);
        if (username != null && !username.isBlank()) {
            String creds = Base64.getEncoder().encodeToString((username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            builder = builder.defaultHeader("Authorization", "Basic " + creds);
        }
        restClient = builder.build();
    }

    public boolean isEnabled() { return enabled; }

    public String translateModel() { return translateModel; }

    /** Chat non in streaming; con {@code json} Ollama è vincolato a rispondere con un oggetto JSON. */
    public String chat(String model, List<ChatMessage> messages, boolean json, double temperature) {
        if (!enabled) throw new AiUnavailableException("Traduzione automatica non attiva sul server");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        if (numCtx > 0) options.put("num_ctx", numCtx);
        List<Map<String, String>> msgs = new ArrayList<>();
        for (ChatMessage m : messages) msgs.add(Map.of("role", m.role(), "content", m.content() == null ? "" : m.content()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", msgs);
        body.put("stream", false);
        if (json) body.put("format", "json");
        body.put("options", options);

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = restClient.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String err = e.getResponseBodyAsString();
            ErrorLog.logger.warn("Ollama: errore HTTP {} (model={}): {}", e.getStatusCode().value(), model,
                    err.length() > 300 ? err.substring(0, 300) : err);
            throw new AiUnavailableException("Il servizio di traduzione ha risposto con un errore", e);
        } catch (RestClientException e) {
            ErrorLog.logger.warn("Ollama non raggiungibile (model={}, dopo {} ms): {}", model, System.currentTimeMillis() - start, e.getMessage());
            throw new AiUnavailableException("Servizio di traduzione non raggiungibile", e);
        }
        String content = response == null ? null : response.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) throw new AiUnavailableException("Risposta vuota dal servizio di traduzione");
        return content;
    }
}
