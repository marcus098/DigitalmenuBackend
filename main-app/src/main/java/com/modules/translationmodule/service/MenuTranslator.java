package com.modules.translationmodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import com.modules.translationmodule.ai.OllamaClient.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** Traduce un gruppo di testi del menu con una sola chiamata a Ollama (risposta JSON con le stesse chiavi). */
@Component
public class MenuTranslator {

    /** Lingue disponibili: codice → nome in italiano (per la dashboard) e in inglese (per il modello). */
    public record Language(String code, String label, String englishName) {}

    public static final List<Language> SUPPORTED = List.of(
            new Language("en", "Inglese", "English"),
            new Language("de", "Tedesco", "German"),
            new Language("fr", "Francese", "French"),
            new Language("es", "Spagnolo", "Spanish"),
            new Language("nl", "Olandese", "Dutch"),
            new Language("pl", "Polacco", "Polish"),
            new Language("pt", "Portoghese", "Portuguese"),
            new Language("zh", "Cinese", "Simplified Chinese"));

    public static Optional<Language> language(String code) {
        return SUPPORTED.stream().filter(l -> l.code().equals(code)).findFirst();
    }

    /** Testo da tradurre: {@code kind} aiuta il modello (categoria, nome piatto, descrizione, ingrediente). */
    public record Item(String key, String kind, String text) {}

    @Autowired
    private OllamaClient ollama;

    private final ObjectMapper mapper = new ObjectMapper();

    static String systemPrompt(Language lang) {
        return """
                You are a professional translator of Italian restaurant, pizzeria and bar menus.
                Translate each item from Italian into %s, the way it would appear on the printed menu of a good restaurant.
                Rules:
                - Keep traditional Italian dish, pizza, pasta and cocktail names as they are (e.g. Margherita, Carbonara, Tiramisù, Spritz, Bruschetta, Amatriciana), but translate the descriptive words around them (e.g. "Pizza ai quattro formaggi" -> "Four cheese pizza" in English).
                - Keep protected Italian products and brand names in Italian (e.g. Parmigiano Reggiano, Mozzarella di bufala campana DOP, Prosciutto di Parma, Aperol).
                - Keep numbers, units, prices, symbols and emoji unchanged.
                - Translate only: no explanations, notes, quotes or alternatives.
                - "kind" tells what the text is: category, dish, description or ingredient.
                Reply only with JSON in the form {"items":[{"k":"<same key>","t":"<translation>"}]} containing every key you received.
                """.formatted(lang.englishName());
    }

    /**
     * @return chiave → traduzione per gli elementi tradotti in modo valido (quelli scartati restano da tradurre)
     * @throws AiUnavailableException se Ollama non risponde o la risposta non è JSON
     */
    public Map<String, String> translate(Language lang, List<Item> items) {
        if (items.isEmpty()) return Map.of();
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode arr = payload.putArray("items");
        for (Item it : items) arr.addObject().put("k", it.key()).put("kind", it.kind()).put("t", it.text());

        String raw = ollama.chat(ollama.translateModel(),
                List.of(ChatMessage.system(systemPrompt(lang)), ChatMessage.user(payload.toString())), true, 0.2);
        return parse(raw, items);
    }

    /** Accetta solo chiavi richieste e traduzioni plausibili (non vuote, non sproporzionate rispetto all'originale). */
    Map<String, String> parse(String raw, List<Item> items) {
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            throw new AiUnavailableException("Risposta del servizio di traduzione non valida", e);
        }
        Map<String, Item> byKey = new HashMap<>();
        for (Item it : items) byKey.put(it.key(), it);
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode n : root.path("items")) {
            String k = n.path("k").asText(null);
            String t = n.path("t").asText(null);
            Item src = k == null ? null : byKey.get(k);
            if (src == null || t == null) continue;
            t = t.strip();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).strip();
            if (t.isEmpty() || t.length() > src.text().length() * 4 + 40) continue;
            out.put(k, t);
        }
        return out;
    }
}
