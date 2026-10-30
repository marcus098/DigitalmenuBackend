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
 * "Scrivi con AI" per il sito vetrina: titolo e sottotitolo della hero, testo "Chi siamo", barra degli avvisi.
 * Sincrono; l'AI usa solo nome del locale e note del ristoratore, senza inventare premi, anni o primati.
 */
@Service
public class LandingTextService {

    static final int MAX_NAME = 120;
    static final int MAX_NOTES = 600;
    static final int MAX_PER_HOUR = 30;
    static final long HOUR_MS = 60 * 60 * 1000L;
    static final double TEMPERATURE = 0.6;
    static final Set<String> TONES = Set.of("classico", "elegante", "semplice", "giovane");

    static final int HERO_TITLE_MAX = 60;
    static final int HERO_TEXT_MAX = 140;
    static final int ABOUT_TITLE_MAX = 60;
    static final int ABOUT_TEXT_MAX = 700;
    static final int ANNOUNCEMENT_MAX = 90;

    static final String UNAVAILABLE = "L'assistente AI non è disponibile in questo momento";
    static final String TOO_MANY = "Troppe richieste, riprova tra poco";

    public enum Kind {
        HERO, ABOUT, ANNOUNCEMENT;

        static Optional<Kind> parse(String raw) {
            if (raw == null) return Optional.empty();
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "hero" -> Optional.of(HERO);
                case "about" -> Optional.of(ABOUT);
                case "announcement" -> Optional.of(ANNOUNCEMENT);
                default -> Optional.empty();
            };
        }
    }

    static final String SYSTEM_PROMPT = """
            Sei un copywriter esperto di ristorazione italiana. Scrivi testi brevi per il sito vetrina di un \
            locale, in italiano.

            Regole tassative:
            - Usa solo le informazioni fornite (nome del locale e note del ristoratore).
            - Non inventare premi, recensioni, anni di attività, date di fondazione, nomi di persone, \
            certificazioni, primati ("il migliore", "il primo") o specialità che non sono nelle note.
            - Nessuna affermazione su salute, dieta o proprietà nutrizionali.
            - Niente virgolette, emoji, hashtag, markdown, prezzi o punti esclamativi multipli.
            - Rispondi solo con un oggetto JSON nella forma {"title": "...", "text": "..."}.""";

    private final OllamaClient ollama;
    private final IpRateLimiter rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public LandingTextService(OllamaClient ollama, IpRateLimiter rateLimiter) {
        this.ollama = ollama;
        this.rateLimiter = rateLimiter;
    }

    public record LandingTextRequest(String kind, String venueName, String notes, String tone) {}

    public record LandingText(String title, String text) {}

    /** Dati già ripuliti e limitati. */
    record Input(Kind kind, String venueName, String notes, String tone) {}

    /** Troppe richieste (→ 429). */
    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) { super(message); }
    }

    /**
     * @throws IllegalArgumentException tipo o nome del locale mancanti (→ 400)
     * @throws TooManyRequestsException oltre 30 richieste l'ora per locale (→ 429)
     * @throws AiUnavailableException AI spenta, irraggiungibile o risposta non valida (→ 503)
     */
    public LandingText write(long idAgency, LandingTextRequest request) {
        Input in = sanitize(request);
        if (!ollama.isEnabled()) throw new AiUnavailableException(UNAVAILABLE);
        if (!rateLimiter.isAllowed("ai-landing:" + idAgency, MAX_PER_HOUR, HOUR_MS)) {
            throw new TooManyRequestsException(TOO_MANY);
        }
        String raw = ollama.chat(ollama.insightsModel(), List.of(
                ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(userPrompt(in))), true, TEMPERATURE);
        LandingText out = parse(in.kind(), raw);
        if (out == null) throw new AiUnavailableException(UNAVAILABLE);
        return out;
    }

    static Input sanitize(LandingTextRequest r) {
        Kind kind = r == null ? null : Kind.parse(r.kind()).orElse(null);
        if (kind == null) throw new IllegalArgumentException("Tipo di testo non valido");
        String name = clip(r.venueName(), MAX_NAME);
        if (name == null) throw new IllegalArgumentException("Indica il nome del locale");
        String tone = r.tone() == null ? "classico" : r.tone().trim().toLowerCase(Locale.ROOT);
        if (!TONES.contains(tone)) tone = "classico";
        return new Input(kind, name, clip(r.notes(), MAX_NOTES), tone);
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    static String userPrompt(Input in) {
        String task = switch (in.kind()) {
            case HERO -> "Scrivi l'intestazione della pagina: \"title\" è uno slogan di massimo 50 caratteri "
                    + "(non ripetere il nome del locale), \"text\" è un sottotitolo di una frase, massimo 130 caratteri.";
            case ABOUT -> "Scrivi la sezione \"Chi siamo\": \"title\" è un titolo di massimo 40 caratteri, "
                    + "\"text\" sono due paragrafi brevi separati da una riga vuota, in tutto massimo 600 caratteri.";
            case ANNOUNCEMENT -> "Scrivi un avviso per la barra in cima alla pagina: \"title\" vuoto, "
                    + "\"text\" una sola frase di massimo 80 caratteri. Se le note non indicano un avviso preciso, "
                    + "invita a prenotare o a scoprire il menu.";
        };
        String toneHint = switch (in.tone()) {
            case "elegante" -> "elegante e raffinato";
            case "semplice" -> "semplice e diretto, parole quotidiane";
            case "giovane" -> "giovane, informale e vivace";
            default -> "classico e accogliente";
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("locale", in.venueName());
        if (in.notes() != null) data.put("note_del_ristoratore", in.notes());
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            json = String.valueOf(data);
        }
        String noNotes = in.notes() == null
                ? " Non ci sono note: resta generico, senza citare piatti, luoghi o storia del locale." : "";
        return task + "\nTono: " + toneHint + "." + noNotes + "\nDati:\n" + json;
    }

    /** Estrae title/text dalla risposta JSON e li ripulisce secondo il tipo; null se non utilizzabile. */
    LandingText parse(Kind kind, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String title;
        String text;
        try {
            JsonNode node = mapper.readTree(raw.trim());
            if (node == null || !node.isObject()) return null;
            JsonNode t = node.get("title");
            JsonNode x = node.get("text");
            title = t != null && t.isTextual() ? t.asText() : "";
            text = x != null && x.isTextual() ? x.asText() : "";
        } catch (Exception e) {
            return null;
        }
        return switch (kind) {
            case HERO -> {
                String tt = cutWords(clean(title), HERO_TITLE_MAX).replaceAll("[.]+$", "");
                String tx = cutSentence(clean(text), HERO_TEXT_MAX);
                yield tt.length() < 3 || tx.length() < 10 ? null : new LandingText(tt, tx);
            }
            case ABOUT -> {
                String tt = cutWords(clean(title), ABOUT_TITLE_MAX).replaceAll("[.]+$", "");
                String tx = paragraphs(text, ABOUT_TEXT_MAX);
                yield tt.length() < 3 || tx.length() < 40 ? null : new LandingText(tt, tx);
            }
            case ANNOUNCEMENT -> {
                String tx = cutSentence(clean(text.isBlank() ? title : text), ANNOUNCEMENT_MAX);
                yield tx.length() < 8 ? null : new LandingText("", tx);
            }
        };
    }

    /** Toglie virgolette, emoji, markdown ed etichette ("Titolo:"), compatta gli spazi. Una sola riga. */
    static String clean(String text) {
        String s = text == null ? "" : text;
        s = s.replaceAll("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]", "");
        s = s.replaceAll("[\"“”«»„`*_#]", "");
        s = s.replaceAll("(?<![\\p{L}])['‘’]|['‘’](?=\\s*$)", "");   // apici usati come virgolette, non apostrofi
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("(?i)^(titolo|title|testo|text|sottotitolo|avviso)\\s*:\\s*", "");
        s = s.replaceFirst("^[-•]\\s*", "");
        s = s.replaceAll("!{2,}", "!");
        return s.trim();
    }

    /** Fino a 2 paragrafi puliti, separati da una riga vuota, in tutto al massimo {@code max} caratteri. */
    static String paragraphs(String text, int max) {
        if (text == null) return "";
        List<String> parts = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n|\\n")) {
            String c = clean(p);
            if (!c.isEmpty()) parts.add(c);
        }
        if (parts.size() > 2) {
            // l'AI ha spezzato troppo: unisce il resto nel secondo paragrafo
            parts = List.of(parts.get(0), String.join(" ", parts.subList(1, parts.size())));
        }
        if (parts.isEmpty()) return "";
        String first = cutSentence(parts.get(0), max);
        if (parts.size() == 1 || first.length() + 2 >= max - 20) return first;
        String second = cutSentence(parts.get(1), max - first.length() - 2);
        return second.length() < 20 ? first : first + "\n\n" + second;
    }

    /** Taglia a {@code max} caratteri alla fine di una frase (o di una parola, aggiungendo il punto). */
    static String cutSentence(String s, int max) {
        if (s.length() <= max) return s;
        String cut = s.substring(0, max);
        int end = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
        if (cut.endsWith(".") || cut.endsWith("!") || cut.endsWith("?")) end = cut.length() - 1;
        if (end >= Math.min(30, max / 3)) return cut.substring(0, end + 1).trim();
        int space = cut.lastIndexOf(' ', max - 2);
        String base = (space > 0 ? cut.substring(0, space) : cut.substring(0, max - 1)).replaceAll("[,;:\\s]+$", "");
        return base + ".";
    }

    /** Taglia a {@code max} caratteri all'ultima parola intera (per i titoli, senza punto finale). */
    static String cutWords(String s, int max) {
        if (s.length() <= max) return s;
        String cut = s.substring(0, max);
        int space = cut.lastIndexOf(' ');
        return (space > 0 ? cut.substring(0, space) : cut).replaceAll("[,;:\\s]+$", "");
    }
}
