package com.modules.aimodule.service;

import com.modules.aimodule.service.ProductDescriptionService.DescriptionRequest;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Scrivi con AI": validazione dell'input, limite orario, pulizia della risposta. */
class ProductDescriptionServiceTest {

    private OllamaClient ollama;
    private ProductDescriptionService service;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaClient.class);
        when(ollama.isEnabled()).thenReturn(true);
        when(ollama.insightsModel()).thenReturn("qwen2.5:7b");
        service = new ProductDescriptionService(ollama, new IpRateLimiter());
    }

    private static DescriptionRequest req(String name) {
        return new DescriptionRequest(name, "Primi", List.of("guanciale", "pecorino", "uovo"), List.of("uova"), null, "classico");
    }

    @Test
    void nameIsRequired() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.describe(1, req("   ")));
        assertEquals("Indica il nome del piatto", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.describe(1, null));
        verify(ollama, never()).chat(anyString(), anyList(), anyBoolean(), anyDouble());
    }

    @Test
    void sanitizeTrimsAndLimits() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(" ingrediente " + i + " ");
        many.add(null);
        ProductDescriptionService.Input in = ProductDescriptionService.sanitize(new DescriptionRequest(
                "  Carbonara\n  ", " ", many, Arrays.asList("glutine", "glutine", ""), "x".repeat(300), "BOH"));
        assertEquals("Carbonara", in.name());
        assertNull(in.categoryName());
        assertEquals(30, in.ingredients().size());
        assertEquals("ingrediente 0", in.ingredients().get(0));
        assertEquals(List.of("glutine"), in.allergens());
        assertEquals(200, in.notes().length());
        assertEquals("classico", in.tone());
        assertEquals("elegante", ProductDescriptionService.sanitize(new DescriptionRequest("a", null, null, null, null, " Elegante ")).tone());
    }

    @Test
    void promptWarnsWhenNoIngredients() {
        String p = ProductDescriptionService.userPrompt(ProductDescriptionService.sanitize(
                new DescriptionRequest("Piatto del giorno", null, null, null, null, null)));
        assertTrue(p.contains("non nominarne nessuno"));
        assertTrue(p.contains("\"piatto\":\"Piatto del giorno\""));
    }

    @Test
    void postProcessStripsQuotesEmojiAndMarkdown() {
        assertEquals("Pasta con guanciale croccante, pecorino e uovo, come vuole la tradizione romana.",
                ProductDescriptionService.postProcess("Descrizione: \"**Pasta** con guanciale croccante, pecorino e uovo, come vuole la tradizione romana.\" 🍝"));
        assertEquals("Un classico dell'osteria, cremoso e avvolgente.",
                ProductDescriptionService.postProcess("«Un classico dell'osteria, cremoso e avvolgente.»"));
    }

    @Test
    void postProcessCutsAtSentenceBoundary() {
        String first = "Pasta fresca condita con guanciale croccante e pecorino romano, mantecata con tuorlo d'uovo.";
        String text = first + " " + "Una ricetta della tradizione che profuma di casa e di domenica in famiglia. ".repeat(5);
        String out = ProductDescriptionService.postProcess(text);
        assertTrue(out.length() <= ProductDescriptionService.MAX_DESCRIPTION, out);
        assertTrue(out.endsWith("."), out);
        assertTrue(out.startsWith(first));
        // nessuna frase completa entro il limite: taglio alla parola
        String noStop = ProductDescriptionService.postProcess("parola ".repeat(80));
        assertTrue(noStop.length() <= ProductDescriptionService.MAX_DESCRIPTION + 1);
        assertTrue(noStop.endsWith("parola."), noStop);
    }

    @Test
    void describeReturnsCleanDescription() {
        when(ollama.chat(eq("qwen2.5:7b"), anyList(), eq(true), anyDouble()))
                .thenReturn("{\"description\": \"Spaghetti con guanciale croccante, pecorino e uovo. Un classico romano.\"}");
        assertEquals("Spaghetti con guanciale croccante, pecorino e uovo. Un classico romano.", service.describe(1, req("Carbonara")));
    }

    @Test
    void invalidOrMissingAiIsUnavailable() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenReturn("{\"testo\": \"x\"}");
        assertThrows(AiUnavailableException.class, () -> service.describe(1, req("Carbonara")));
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenReturn("{\"description\": \"🍝\"}");
        assertThrows(AiUnavailableException.class, () -> service.describe(1, req("Carbonara")));
        when(ollama.isEnabled()).thenReturn(false);
        assertThrows(AiUnavailableException.class, () -> service.describe(1, req("Carbonara")));
    }

    @Test
    void rateLimitedTo30PerHourPerAgency() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble()))
                .thenReturn("{\"description\": \"Spaghetti con guanciale, pecorino e uovo.\"}");
        for (int i = 0; i < ProductDescriptionService.MAX_PER_HOUR; i++) service.describe(9, req("Carbonara"));
        ProductDescriptionService.TooManyRequestsException e = assertThrows(ProductDescriptionService.TooManyRequestsException.class,
                () -> service.describe(9, req("Carbonara")));
        assertEquals("Troppe richieste, riprova tra poco", e.getMessage());
        // altro locale: non limitato
        assertNotNull(service.describe(10, req("Carbonara")));
    }
}
