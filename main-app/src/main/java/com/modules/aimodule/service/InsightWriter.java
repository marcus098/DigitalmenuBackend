package com.modules.aimodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.aimodule.model.InsightKind;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import com.modules.translationmodule.ai.OllamaClient.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrive con Ollama il testo di un riepilogo a partire dai fatti già calcolati in Java.
 * Se l'AI non è attiva, non risponde o produce un testo non valido restituisce {@link Optional#empty()}:
 * il chiamante usa allora il testo deterministico.
 */
@Component
public class InsightWriter {

    static final double TEMPERATURE = 0.4;
    static final int MIN_LENGTH = 40;
    static final int MAX_LENGTH = 2000;

    static final String SYSTEM_PROMPT = """
            Sei AxiomAI, un consulente esperto di ristorazione che aiuta il titolare di un ristorante italiano \
            a capire i propri numeri. Scrivi in italiano, dando del tu al titolare, con tono cordiale, concreto e pratico. \
            Sii breve: niente frasi di circostanza, saluti o premesse.

            Regole tassative:
            - Usa SOLO i numeri, le percentuali, le date e i nomi dei piatti presenti nei dati forniti, \
            scritti esattamente come compaiono (es. "1.240 €", "+18%"). Non inventare, non stimare e non ricalcolare nulla.
            - Se un'informazione non è nei dati, non parlarne e non fare supposizioni sulle cause.
            - "incasso" è il totale lordo delle comande completate: chiamalo semplicemente incasso.
            - Testo semplice: niente markdown, grassetti, titoli, emoji o tabelle. Separa i paragrafi con un a capo. \
            Per gli elenchi usa righe che iniziano con "- ".
            - Rispondi solo con un oggetto JSON nella forma {"text": "..."}.""";

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*(?:[*•·–—]|\\d+[.)])\\s+");

    private final OllamaClient ollama;
    private final ObjectMapper mapper = new ObjectMapper();

    public InsightWriter(OllamaClient ollama) {
        this.ollama = ollama;
    }

    /** Testo scritto dall'AI con il modello usato, se valido. */
    public record AiText(String text, String model) {}

    public Optional<AiText> write(InsightKind kind, Map<String, Object> facts) {
        if (!ollama.isEnabled()) return Optional.empty();
        String factsJson;
        try {
            factsJson = mapper.writeValueAsString(facts);
        } catch (Exception e) {
            return Optional.empty();
        }
        String model = ollama.insightsModel();
        try {
            String raw = ollama.chat(model, List.of(
                    ChatMessage.system(SYSTEM_PROMPT),
                    ChatMessage.user(instructions(kind) + "\n\nDati:\n" + factsJson)), true, TEMPERATURE);
            Optional<String> text = validate(kind, raw, factsJson);
            if (text.isEmpty()) ErrorLog.logger.warn("AxiomAI: risposta non valida per {}, uso il testo di riserva", kind);
            return text.map(t -> new AiText(t, model));
        } catch (AiUnavailableException e) {
            ErrorLog.logger.warn("AxiomAI: servizio AI non disponibile per {}: {}", kind, e.getMessage());
            return Optional.empty();
        }
    }

    static String instructions(InsightKind kind) {
        return switch (kind) {
            case DAILY -> "Scrivi il riepilogo di com'è andata la giornata di ieri in 3-5 frasi, in un unico paragrafo: "
                    + "incasso e confronto con i giorni di riferimento, comande e scontrino medio, momento di punta, "
                    + "piatti più venduti, prenotazioni ed eventuali piatti esauriti (solo se presenti nei dati). "
                    + "Chiudi con un breve spunto pratico.";
            case WEEKLY -> "Scrivi il riepilogo della settimana in 5-7 frasi (al massimo due paragrafi): "
                    + "incasso e confronto con le settimane precedenti, comande e scontrino medio, giorno migliore e "
                    + "più debole, piatti più venduti, prenotazioni ed eventuali esauriti (solo se presenti nei dati). "
                    + "Chiudi con uno o due spunti pratici per la prossima settimana.";
            case MENU_TIPS -> "Scrivi da 3 a 5 consigli pratici e concreti sul menu, uno per riga, ogni riga inizia con \"- \". "
                    + "Basati su piatti più venduti, piatti poco venduti, piatti esauriti spesso, giorni e fasce orarie "
                    + "più deboli e quota di asporto e domicilio. Ogni consiglio deve citare il dato da cui nasce. "
                    + "Nessuna introduzione o conclusione.";
        };
    }

    /**
     * Estrae e ripulisce il campo "text"; scarta risposte vuote, troppo corte/lunghe, senza punti elenco
     * (MENU_TIPS) o con numeri (da 10 in su) che non compaiono nei dati.
     */
    static Optional<String> validate(InsightKind kind, String raw, String factsJson) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        JsonNode node;
        try {
            node = new ObjectMapper().readTree(raw.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
        JsonNode t = node == null ? null : node.get("text");
        if (t == null || !t.isTextual()) return Optional.empty();
        String text = clean(t.asText());
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) return Optional.empty();
        if (kind == InsightKind.MENU_TIPS && text.lines().filter(l -> l.startsWith("- ")).count() < 2) return Optional.empty();
        if (!numbersAreKnown(text, factsJson)) return Optional.empty();
        return Optional.of(text);
    }

    /** Toglie markdown ed emoji, uniforma i punti elenco a "- " e gli a capo. */
    static String clean(String s) {
        String out = s.replace("\r", "")
                .replace("**", "").replace("__", "").replace("`", "")
                .replaceAll("(?m)^\\s*#+\\s*", "");
        out = BULLET.matcher(out).replaceAll("- ");
        out = out.replaceAll("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]", "");
        out = out.replaceAll("[ \\t]+", " ").replaceAll("(?m)^ +| +$", "").replaceAll("\n{2,}", "\n");
        return out.trim();
    }

    /** Ogni numero del testo da 10 in su (separatori ignorati: "1.240" = "1240") deve comparire nei dati. */
    static boolean numbersAreKnown(String text, String factsJson) {
        Set<String> known = new HashSet<>();
        Matcher f = NUMBER.matcher(factsJson);
        while (f.find()) {
            String n = normalize(f.group());
            known.add(n);
            // anche le singole parti ("20:00" → "20", "29,50" → "29")
            for (String part : f.group().split("[.,]")) known.add(normalize(part));
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String n = normalize(m.group());
            if (n.length() > 1 && !n.equals("00") && !known.contains(n)) {
                // "29,5" per "29,50"
                if (!known.contains(n + "0")) return false;
            }
        }
        return true;
    }

    private static String normalize(String n) {
        String d = n.replaceAll("[.,]", "").replaceFirst("^0+(?=\\d)", "");
        return d;
    }
}
