package com.modules.aimodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import com.modules.translationmodule.ai.OllamaClient.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * "Scrivi con AI": descrizione breve di un piatto a partire da nome, categoria, ingredienti e note forniti dal locale.
 * Sincrono; l'AI non deve inventare ingredienti né fare promesse salutistiche.
 */
@Service
public class ProductDescriptionService {

    static final int MAX_LIST = 30;
    static final int MAX_STRING = 200;
    static final int MAX_DESCRIPTION = 280;
    static final int MIN_DESCRIPTION = 15;
    static final int MAX_PER_HOUR = 30;
    static final long HOUR_MS = 60 * 60 * 1000L;
    static final double TEMPERATURE = 0.5;
    static final Set<String> TONES = Set.of("classico", "elegante", "semplice");

    static final String UNAVAILABLE = "L'assistente AI non è disponibile in questo momento";
    static final String TOO_MANY = "Troppe richieste, riprova tra poco";

    static final String SYSTEM_PROMPT = """
            Sei un copywriter esperto di ristorazione italiana. Scrivi la descrizione di un piatto per il menu \
            digitale di un ristorante, in italiano.

            Regole tassative:
            - 1-3 frasi, al massimo 250 caratteri in tutto.
            - Cita solo gli ingredienti forniti: non aggiungerne altri, non inventare provenienze, certificazioni, \
            tecniche di cottura o abbinamenti che non sono nei dati.
            - Nessuna affermazione su salute, dieta, calorie, proprietà nutrizionali o benefici.
            - Non elencare gli allergeni (il menu li mostra già) e non dire che il piatto ne è privo.
            - Niente virgolette, emoji, hashtag, markdown, prezzi o punti esclamativi multipli.
            - Non ripetere il nome del piatto all'inizio.
            - Rispondi solo con un oggetto JSON nella forma {"description": "..."}.""";

    private final OllamaClient ollama;
    private final IpRateLimiter rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductDescriptionService(OllamaClient ollama, IpRateLimiter rateLimiter) {
        this.ollama = ollama;
        this.rateLimiter = rateLimiter;
    }

    public record DescriptionRequest(String name, String categoryName, List<String> ingredients,
                                     List<String> allergens, String notes, String tone) {}

    /** Dati già ripuliti e limitati. */
    record Input(String name, String categoryName, List<String> ingredients, List<String> allergens,
                 String notes, String tone) {}

    /** Troppe richieste (→ 429). */
    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) { super(message); }
    }

    /**
     * @throws IllegalArgumentException nome mancante (→ 400)
     * @throws TooManyRequestsException oltre 30 richieste l'ora per locale (→ 429)
     * @throws AiUnavailableException AI spenta, irraggiungibile o risposta non valida (→ 503)
     */
    public String describe(long idAgency, DescriptionRequest request) {
        Input in = sanitize(request);
        if (!ollama.isEnabled()) throw new AiUnavailableException(UNAVAILABLE);
        if (!rateLimiter.isAllowed("ai-description:" + idAgency, MAX_PER_HOUR, HOUR_MS)) {
            throw new TooManyRequestsException(TOO_MANY);
        }
        String raw = ollama.chat(ollama.insightsModel(), List.of(
                ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(userPrompt(in))), true, TEMPERATURE);
        String description = parse(raw);
        if (description == null) throw new AiUnavailableException(UNAVAILABLE);
        return description;
    }

    static Input sanitize(DescriptionRequest r) {
        String name = r == null ? null : clip(r.name());
        if (name == null) throw new IllegalArgumentException("Indica il nome del piatto");
        String tone = r.tone() == null ? "classico" : r.tone().trim().toLowerCase(Locale.ROOT);
        if (!TONES.contains(tone)) tone = "classico";
        return new Input(name, clip(r.categoryName()), list(r.ingredients()), list(r.allergens()), clip(r.notes()), tone);
    }

    private static String clip(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        return t.length() > MAX_STRING ? t.substring(0, MAX_STRING).trim() : t;
    }

    private static List<String> list(List<String> values) {
        if (values == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String v : values) {
            String c = clip(v);
            if (c != null && !out.contains(c)) out.add(c);
            if (out.size() >= MAX_LIST) break;
        }
        return out;
    }

    static String userPrompt(Input in) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("piatto", in.name());
        if (in.categoryName() != null) data.put("categoria", in.categoryName());
        if (!in.ingredients().isEmpty()) data.put("ingredienti", in.ingredients());
        if (!in.allergens().isEmpty()) data.put("allergeni", in.allergens());
        if (in.notes() != null) data.put("note_del_ristoratore", in.notes());
        String toneHint = switch (in.tone()) {
            case "elegante" -> "elegante e raffinato, da ristorante curato";
            case "semplice" -> "semplice e diretto, parole quotidiane";
            default -> "classico e invitante, da trattoria italiana";
        };
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            json = String.valueOf(data);
        }
        String noIngredients = in.ingredients().isEmpty()
                ? " Non sono indicati ingredienti: non nominarne nessuno, descrivi il piatto in modo generico." : "";
        return "Tono: " + toneHint + "." + noIngredients + "\nDati del piatto:\n" + json;
    }

    /** Estrae "description" dalla risposta JSON e la ripulisce; null se non utilizzabile. */
    String parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text;
        try {
            JsonNode node = mapper.readTree(raw.trim());
            JsonNode d = node == null ? null : node.get("description");
            if (d == null || !d.isTextual()) return null;
            text = d.asText();
        } catch (Exception e) {
            return null;
        }
        String clean = postProcess(text);
        return clean.length() < MIN_DESCRIPTION ? null : clean;
    }

    /**
     * Toglie virgolette, emoji, markdown ed etichette ("Descrizione:"), compatta gli spazi e taglia a
     * {@value #MAX_DESCRIPTION} caratteri alla fine di una frase (o di una parola, se non c'è una frase intera).
     */
    static String postProcess(String text) {
        String s = text == null ? "" : text;
        s = s.replaceAll("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]", "");
        s = s.replaceAll("[\"“”«»„`*_#]", "");
        s = s.replaceAll("(?<![\\p{L}])['‘’]|['‘’](?=\\s*$)", "");   // apici usati come virgolette, non apostrofi
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("(?i)^(descrizione|description)\\s*:\\s*", "");
        s = s.replaceFirst("^[-•]\\s*", "");
        s = s.replaceAll("!{2,}", "!");
        if (s.length() > MAX_DESCRIPTION) {
            String cut = s.substring(0, MAX_DESCRIPTION);
            int end = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
            if (cut.endsWith(".") || cut.endsWith("!") || cut.endsWith("?")) end = cut.length() - 1;
            if (end >= 40) {
                s = cut.substring(0, end + 1);
            } else {
                int space = cut.lastIndexOf(' ');
                s = (space > 0 ? cut.substring(0, space) : cut).replaceAll("[,;:\\s]+$", "") + ".";
            }
        }
        return s.trim();
    }
}
