package com.modules.stylemodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validazione del sito vetrina a sezioni (StyleJpa.landingConfig).
 * <p>
 * Il JSON è libero nei contenuti (lo interpreta il client), ma qui si controllano i limiti che
 * proteggono il database e i visitatori: dimensione massima, struttura {version, global, sections},
 * tipi di sezione noti, lunghezza dei testi e delle liste, colori e link (solo https / tel / mailto,
 * video solo da YouTube o Vimeo). Gli errori sono IllegalArgumentException → 400 col messaggio.
 */
public final class LandingConfigValidator {

    static final int MAX_BYTES = 200 * 1024;
    static final int MAX_SECTIONS = 30;
    static final int MAX_ARRAY = 50;
    static final int MAX_STRING = 5000;
    static final int MAX_DEPTH = 8;
    static final int CURRENT_VERSION = 1;

    static final Set<String> SECTION_TYPES = Set.of(
            "hero", "about", "gallery", "features", "highlights", "testimonials", "hours", "location",
            "reservations", "contact", "announcement", "text", "video", "categories", "ticker");

    private static final Set<String> VIDEO_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com",
            "youtube-nocookie.com", "vimeo.com", "www.vimeo.com", "player.vimeo.com");

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern SECTION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LandingConfigValidator() {}

    /**
     * @return JSON compatto da salvare, oppure null se il valore è vuoto (= torna al sito classico)
     * @throws IllegalArgumentException se non valido
     */
    public static String validOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw bad("Il sito vetrina è troppo grande (massimo 200 KB): riduci testi o sezioni");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(v);
        } catch (Exception e) {
            throw bad("Configurazione del sito vetrina non valida (JSON)");
        }
        if (root == null || !root.isObject()) throw bad("Configurazione del sito vetrina non valida");

        JsonNode version = root.get("version");
        if (version == null || !version.isInt() || version.asInt() < 1 || version.asInt() > CURRENT_VERSION) {
            throw bad("Versione del sito vetrina non supportata");
        }
        JsonNode global = root.get("global");
        if (global != null && !global.isNull() && !global.isObject()) throw bad("Impostazioni globali non valide");

        JsonNode sections = root.get("sections");
        if (sections == null || !sections.isArray()) throw bad("Elenco delle sezioni mancante");
        if (sections.size() > MAX_SECTIONS) throw bad("Troppe sezioni (massimo " + MAX_SECTIONS + ")");
        for (JsonNode s : sections) {
            if (!s.isObject()) throw bad("Sezione non valida");
            JsonNode type = s.get("type");
            if (type == null || !type.isTextual() || !SECTION_TYPES.contains(type.asText())) {
                throw bad("Tipo di sezione non valido");
            }
            JsonNode id = s.get("id");
            if (id == null || !id.isTextual() || !SECTION_ID.matcher(id.asText()).matches()) {
                throw bad("Identificativo di sezione non valido");
            }
        }
        walk(root, null, 0);
        return root.toString();
    }

    private static void walk(JsonNode node, String key, int depth) {
        if (depth > MAX_DEPTH) throw bad("Configurazione del sito vetrina troppo annidata");
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().length() > 60) throw bad("Chiave non valida nel sito vetrina");
                walk(e.getValue(), e.getKey(), depth + 1);
            }
        } else if (node.isArray()) {
            if (node.size() > MAX_ARRAY) throw bad("Elenco troppo lungo nel sito vetrina (massimo " + MAX_ARRAY + " elementi)");
            for (JsonNode child : node) walk(child, key, depth + 1);
        } else if (node.isTextual()) {
            checkString(key, node.asText());
        }
    }

    static void checkString(String key, String value) {
        if (value.length() > MAX_STRING) throw bad("Testo troppo lungo nel sito vetrina (massimo " + MAX_STRING + " caratteri)");
        String v = value.trim();
        if (key == null || v.isEmpty()) return;
        // I testi liberi restano testo (il client non li interpreta come HTML): i controlli sugli schemi
        // valgono solo per i campi che diventano link o immagini, così "Data: sabato 12" resta valido.
        String lower = v.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean dangerous = lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:");

        // chiavi confrontate senza distinzione di maiuscole: "imageURL" o "url" non devono sfuggire ai controlli
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("videourl")) {
            if (!isHttps(v) || !VIDEO_HOSTS.contains(host(v))) {
                throw bad("Video: incolla un link di YouTube o Vimeo");
            }
        } else if (k.equals("href") || k.endsWith("url")) {
            if (dangerous) throw bad("Link non consentito nel sito vetrina");
            if (!(isHttps(v) || lower.startsWith("tel:") || lower.startsWith("mailto:"))) {
                throw bad("I link devono iniziare con https://");
            }
        } else if (k.equals("src")) {
            // immagini: percorso del bucket (relativo) oppure https; "//host" e "/\host" sono link a un altro sito
            boolean otherHost = lower.startsWith("//") || lower.startsWith("/\\") || lower.startsWith("\\");
            if (dangerous || otherHost || (v.contains(":") && !isHttps(v))) throw bad("Immagine non valida nel sito vetrina");
        } else if (k.endsWith("color")) {
            if (!HEX_COLOR.matcher(v).matches()) throw bad("Colore non valido nel sito vetrina (usa #RRGGBB)");
        }
    }

    private static boolean isHttps(String v) {
        if (!v.regionMatches(true, 0, "https://", 0, 8)) return false;
        return host(v) != null;
    }

    private static String host(String v) {
        try {
            String h = new URI(v).getHost();
            return h == null || h.isBlank() ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message); // mapped to 400 by GlobalExceptionHandler
    }
}
