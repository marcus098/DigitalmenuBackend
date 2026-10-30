package com.modules.stylemodule.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sito vetrina a sezioni: limiti di dimensione, struttura, link e colori. */
class LandingConfigValidatorTest {

    private static String config(String sections) {
        return "{\"version\":1,\"global\":{\"animation\":\"subtle\"},\"sections\":[" + sections + "]}";
    }

    private static void assertBad(String json, String messagePart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LandingConfigValidator.validOrNull(json));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    @Test
    void blankMeansClassicLanding() {
        assertNull(LandingConfigValidator.validOrNull(null));
        assertNull(LandingConfigValidator.validOrNull("   "));
    }

    @Test
    void validConfigIsCompacted() {
        String json = config("""
                { "id": "hero-1", "type": "hero", "enabled": true,
                  "title": "Benvenuti", "images": [ { "src": "bucket/12/img/a.webp", "alt": "Sala" } ],
                  "buttons": [ { "label": "Chiama", "action": "call" }, { "label": "Sito", "action": "link", "href": "https://example.com" } ],
                  "overlayColor": "#112233",
                  "style": { "bg": "custom", "bgColor": "#ffffff" } },
                { "id": "v1", "type": "video", "enabled": true, "videoUrl": "https://www.youtube.com/watch?v=abc" },
                { "id": "c1", "type": "contact", "enabled": true, "email": "info@example.com", "tripadvisorUrl": "https://www.tripadvisor.it/x" }
                """);
        String out = LandingConfigValidator.validOrNull(json);
        assertNotNull(out);
        assertFalse(out.contains("\n"));
        assertTrue(out.contains("\"type\":\"hero\""));
    }

    @Test
    void rejectsInvalidJsonAndStructure() {
        assertBad("{not json", "JSON");
        assertBad("[1,2]", "non valida");
        assertBad("{\"version\":2,\"sections\":[]}", "Versione");
        assertBad("{\"version\":1}", "sezioni");
        assertBad(config("{\"id\":\"x\",\"type\":\"popup\"}"), "Tipo di sezione");
        assertBad(config("{\"id\":\"bad id!\",\"type\":\"text\"}"), "Identificativo");
    }

    @Test
    void rejectsTooLarge() {
        String big = "a".repeat(LandingConfigValidator.MAX_BYTES);
        assertBad(config("{\"id\":\"t\",\"type\":\"text\",\"text\":\"" + big + "\"}"), "200 KB");
    }

    @Test
    void capsSectionsListsAndStrings() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= LandingConfigValidator.MAX_SECTIONS; i++) {
            if (i > 0) many.append(',');
            many.append("{\"id\":\"s").append(i).append("\",\"type\":\"text\"}");
        }
        assertBad(config(many.toString()), "Troppe sezioni");

        StringBuilder items = new StringBuilder();
        for (int i = 0; i <= LandingConfigValidator.MAX_ARRAY; i++) items.append(i > 0 ? "," : "").append("{\"src\":\"a.webp\"}");
        assertBad(config("{\"id\":\"g\",\"type\":\"gallery\",\"items\":[" + items + "]}"), "Elenco troppo lungo");

        assertBad(config("{\"id\":\"t\",\"type\":\"text\",\"text\":\"" + "x".repeat(LandingConfigValidator.MAX_STRING + 1) + "\"}"), "Testo troppo lungo");
    }

    @Test
    void linksMustBeHttpsTelOrMailto() {
        assertBad(config("{\"id\":\"h\",\"type\":\"hero\",\"buttons\":[{\"href\":\"http://example.com\"}]}"), "https://");
        assertBad(config("{\"id\":\"h\",\"type\":\"hero\",\"buttons\":[{\"href\":\"javascript:alert(1)\"}]}"), "non consentito");
        assertBad(config("{\"id\":\"a\",\"type\":\"announcement\",\"href\":\" JavaScript:alert(1)\"}"), "non consentito");
        // testo libero: resta testo anche se somiglia a uno schema ("Data: sabato 12 maggio")
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"t\",\"type\":\"text\",\"text\":\"Data: sabato 12 maggio\"}")));
        assertBad(config("{\"id\":\"c\",\"type\":\"contact\",\"tripadvisorUrl\":\"ftp://x\"}"), "https://");
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"h\",\"type\":\"hero\",\"buttons\":[{\"href\":\"tel:+39333\"},{\"href\":\"mailto:a@b.it\"}]}")));
    }

    @Test
    void videoOnlyFromYoutubeOrVimeo() {
        assertBad(config("{\"id\":\"v\",\"type\":\"video\",\"videoUrl\":\"https://evil.example/watch?v=1\"}"), "YouTube");
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"v\",\"type\":\"video\",\"videoUrl\":\"https://youtu.be/abc\"}")));
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"v\",\"type\":\"video\",\"videoUrl\":\"https://vimeo.com/12345\"}")));
    }

    @Test
    void imagesAndColorsAreChecked() {
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\"http://x/a.png\"}}"), "Immagine");
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\"data:image/png;base64,AAA\"}}"), "Immagine");
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"style\":{\"bgColor\":\"red\"}}"), "Colore");
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\"https://cdn.example.com/a.webp\"},\"style\":{\"bgColor\":\"\"}}")));
    }

    @Test
    void protocolRelativeImagesAndKeyCaseTricksAreRejected() {
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\"//evil.example/a.png\"}}"), "Immagine");
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\" /\\\\evil.example/a.png\"}}"), "Immagine");
        assertBad(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"SRC\":\"javascript:alert(1)\"}}"), "Immagine");
        assertBad(config("{\"id\":\"c\",\"type\":\"contact\",\"tripadvisorURL\":\"javascript:alert(1)\"}"), "non consentito");
        assertBad(config("{\"id\":\"c\",\"type\":\"contact\",\"url\":\"http://x.example\"}"), "https://");
        assertBad(config("{\"id\":\"h\",\"type\":\"hero\",\"buttons\":[{\"HREF\":\"javascript:alert(1)\"}]}"), "non consentito");
        assertBad(config("{\"id\":\"v\",\"type\":\"video\",\"VideoUrl\":\"https://evil.example/v\"}"), "YouTube");
        // percorsi del bucket con una sola barra iniziale restano validi
        assertNotNull(LandingConfigValidator.validOrNull(config("{\"id\":\"a\",\"type\":\"about\",\"image\":{\"src\":\"/bucket/1/img/a.webp\"}}")));
    }
}
