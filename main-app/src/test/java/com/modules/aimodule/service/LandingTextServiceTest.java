package com.modules.aimodule.service;

import com.modules.aimodule.service.LandingTextService.LandingText;
import com.modules.aimodule.service.LandingTextService.LandingTextRequest;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Scrivi con AI" del sito vetrina: validazione, limiti per tipo di testo, pulizia, limite orario. */
class LandingTextServiceTest {

    private OllamaClient ollama;
    private LandingTextService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        when(ollama.isEnabled()).thenReturn(true);
        when(ollama.insightsModel()).thenReturn("qwen2.5:7b");
        service = new LandingTextService(ollama, new IpRateLimiter());
    }

    private static LandingTextRequest req(String kind) {
        return new LandingTextRequest(kind, "Trattoria da Mario", "cucina romana, pasta fresca", "classico");
    }

    @Test
    void kindAndVenueNameAreRequired() {
        assertEquals("Tipo di testo non valido",
                assertThrows(IllegalArgumentException.class, () -> service.write(1, req("popup"))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.write(1, null));
        assertEquals("Indica il nome del locale", assertThrows(IllegalArgumentException.class,
                () -> service.write(1, new LandingTextRequest("hero", "  ", null, null))).getMessage());
        verify(ollama, never()).chat(anyString(), anyList(), anyBoolean(), anyDouble());
    }

    @Test
    void sanitizeLimitsAndNormalizesTone() {
        LandingTextService.Input in = LandingTextService.sanitize(
                new LandingTextRequest(" ABOUT ", " Da\nMario ", "n".repeat(900), "Giovane"));
        assertEquals(LandingTextService.Kind.ABOUT, in.kind());
        assertEquals("Da Mario", in.venueName());
        assertEquals(LandingTextService.MAX_NOTES, in.notes().length());
        assertEquals("giovane", in.tone());
        assertEquals("classico", LandingTextService.sanitize(new LandingTextRequest("hero", "x", null, "boh")).tone());
    }

    @Test
    void promptForbidsInventedFactsWithoutNotes() {
        String p = LandingTextService.userPrompt(LandingTextService.sanitize(new LandingTextRequest("announcement", "Bar Sport", null, null)));
        assertTrue(p.contains("Non ci sono note"));
        assertTrue(p.contains("\"locale\":\"Bar Sport\""));
        assertTrue(LandingTextService.SYSTEM_PROMPT.contains("Non inventare premi"));
    }

    @Test
    void heroIsCleanedAndCut() {
        when(ollama.chat(eq("qwen2.5:7b"), anyList(), eq(true), anyDouble())).thenReturn(
                "{\"title\": \"Titolo: «La cucina romana come a casa tua, ogni giorno dell'anno e anche di più».\", "
                        + "\"text\": \"**Pasta fresca** tirata a mano e sughi della tradizione. Ti aspettiamo per pranzo e per cena, "
                        + "con un servizio cordiale e un ambiente familiare che ti farà sentire a casa. 🍝\"}");
        LandingText out = service.write(1, req("hero"));
        assertTrue(out.title().length() <= LandingTextService.HERO_TITLE_MAX, out.title());
        assertTrue(out.title().startsWith("La cucina romana"), out.title());
        assertFalse(out.title().contains("«"));
        assertFalse(out.title().endsWith("."));
        assertTrue(out.text().length() <= LandingTextService.HERO_TEXT_MAX, out.text());
        assertEquals("Pasta fresca tirata a mano e sughi della tradizione.", out.text());
    }

    @Test
    void aboutKeepsTwoParagraphsWithinLimit() {
        String p1 = "Siamo una trattoria di quartiere dove la pasta fresca si prepara ogni mattina. ".repeat(3);
        String p2 = "Il nostro menu segue le stagioni e la tradizione romana. ".repeat(10);
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble()))
                .thenReturn("{\"title\": \"La nostra storia\", \"text\": \"" + p1 + "\\n\\n" + p2 + "\\n\\nTerzo paragrafo.\"}");
        LandingText out = service.write(1, req("about"));
        assertEquals("La nostra storia", out.title());
        assertTrue(out.text().length() <= LandingTextService.ABOUT_TEXT_MAX, String.valueOf(out.text().length()));
        assertEquals(2, out.text().split("\n\n").length, out.text());
        assertTrue(out.text().endsWith("."));
    }

    @Test
    void announcementHasNoTitleAndIsShort() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble()))
                .thenReturn("{\"title\": \"Avviso\", \"text\": \"Da questa settimana siamo aperti anche la domenica a pranzo, prenota il tuo tavolo online!!\"}");
        LandingText out = service.write(1, req("announcement"));
        assertEquals("", out.title());
        assertTrue(out.text().length() <= LandingTextService.ANNOUNCEMENT_MAX, out.text());
        assertFalse(out.text().contains("!!"));
    }

    @Test
    void invalidOrMissingAiIsUnavailable() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenReturn("non json");
        assertThrows(AiUnavailableException.class, () -> service.write(1, req("hero")));
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenReturn("{\"title\": \"🍝\", \"text\": \"\"}");
        assertThrows(AiUnavailableException.class, () -> service.write(1, req("hero")));
        when(ollama.isEnabled()).thenReturn(false);
        assertThrows(AiUnavailableException.class, () -> service.write(1, req("hero")));
    }

    @Test
    void rateLimitedTo30PerHourPerAgency() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble()))
                .thenReturn("{\"title\": \"\", \"text\": \"Prenota il tuo tavolo per il weekend.\"}");
        for (int i = 0; i < LandingTextService.MAX_PER_HOUR; i++) service.write(5, req("announcement"));
        LandingTextService.TooManyRequestsException e = assertThrows(LandingTextService.TooManyRequestsException.class,
                () -> service.write(5, req("announcement")));
        assertEquals("Troppe richieste, riprova tra poco", e.getMessage());
        assertNotNull(service.write(6, req("announcement")));
    }
}
