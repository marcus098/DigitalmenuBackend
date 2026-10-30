package com.modules.aimodule.service;

import com.modules.aimodule.model.InsightKind;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Testo AI: validazione del JSON, numeri non inventati, pulizia del markdown, fallback se l'AI non c'è. */
class InsightWriterTest {

    private static final String FACTS = "{\"giorno\":\"venerdì 30 ottobre 2026\",\"incasso\":\"1.240 €\",\"comande\":42,"
            + "\"scontrino_medio\":\"29,52 €\",\"ora_di_punta\":\"20:00–21:00\",\"confronto\":{\"variazione_incasso\":\"+18%\"}}";

    private static Map<String, Object> facts() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("incasso", "1.240 €");
        f.put("comande", 42);
        return f;
    }

    @Test
    void acceptsValidTextWithKnownNumbers() {
        String raw = "{\"text\": \"Ieri hai incassato 1.240 € con 42 comande, il 18% in più del solito. Il picco è stato tra le 20:00 e le 21:00.\"}";
        Optional<String> t = InsightWriter.validate(InsightKind.DAILY, raw, FACTS);
        assertTrue(t.isPresent());
        assertTrue(t.get().contains("1.240 €"));
    }

    @Test
    void rejectsInventedNumbers() {
        String raw = "{\"text\": \"Ieri hai incassato 1.540 € con 42 comande: davvero un'ottima giornata per il locale.\"}";
        assertTrue(InsightWriter.validate(InsightKind.DAILY, raw, FACTS).isEmpty());
        // numeri sotto 10 tollerati ("3 consigli", "2 volte")
        String ok = "{\"text\": \"Ieri hai incassato 1.240 € con 42 comande: ecco 3 spunti per la settimana che arriva.\"}";
        assertTrue(InsightWriter.validate(InsightKind.DAILY, ok, FACTS).isPresent());
    }

    @Test
    void rejectsInvalidJsonAndBadShapes() {
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "non è json", FACTS).isEmpty());
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "{\"testo\": \"abc\"}", FACTS).isEmpty());
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "{\"text\": 12}", FACTS).isEmpty());
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "{\"text\": \"Troppo corto\"}", FACTS).isEmpty());
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "", FACTS).isEmpty());
        assertTrue(InsightWriter.validate(InsightKind.DAILY, "{\"text\": \"" + "a".repeat(2100) + "\"}", FACTS).isEmpty());
    }

    @Test
    void menuTipsNeedBullets() {
        String noBullets = "{\"text\": \"Metti in evidenza i piatti più venduti e rivedi quelli meno richiesti del menu.\"}";
        assertTrue(InsightWriter.validate(InsightKind.MENU_TIPS, noBullets, FACTS).isEmpty());
        String bullets = "{\"text\": \"* **Valorizza** la Margherita nel menu.\\n\\n* Rivedi i piatti poco venduti 🍕\\n1. Controlla le scorte\"}";
        Optional<String> t = InsightWriter.validate(InsightKind.MENU_TIPS, bullets, FACTS);
        assertEquals(Optional.of("- Valorizza la Margherita nel menu.\n- Rivedi i piatti poco venduti\n- Controlla le scorte"), t);
    }

    @Test
    void cleanRemovesMarkdown() {
        assertEquals("Titolo\nTesto in grassetto", InsightWriter.clean("## Titolo\r\n\n\n**Testo** in __grassetto__ ✨"));
    }

    @Test
    void writeReturnsEmptyWhenAiDisabledOrUnavailable() {
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.isEnabled()).thenReturn(false);
        assertTrue(new InsightWriter(ollama).write(InsightKind.DAILY, facts()).isEmpty());
        verify(ollama, never()).chat(anyString(), anyList(), anyBoolean(), anyDouble());

        when(ollama.isEnabled()).thenReturn(true);
        when(ollama.insightsModel()).thenReturn("qwen2.5:7b");
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenThrow(new AiUnavailableException("Servizio AI non raggiungibile"));
        assertTrue(new InsightWriter(ollama).write(InsightKind.DAILY, facts()).isEmpty());
    }

    @Test
    void writeUsesInsightsModelInJsonMode() {
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.isEnabled()).thenReturn(true);
        when(ollama.insightsModel()).thenReturn("llama3.1:8b");
        when(ollama.chat(eq("llama3.1:8b"), anyList(), eq(true), eq(InsightWriter.TEMPERATURE)))
                .thenReturn("{\"text\": \"Ieri hai incassato 1.240 € con 42 comande. Bel risultato, continua così.\"}");
        Optional<InsightWriter.AiText> t = new InsightWriter(ollama).write(InsightKind.DAILY, facts());
        assertTrue(t.isPresent());
        assertEquals("llama3.1:8b", t.get().model());
    }
}
