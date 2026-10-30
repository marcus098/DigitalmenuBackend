package com.modules.aimodule.service;

import com.modules.aimodule.model.AiInsightJpa;
import com.modules.aimodule.model.InsightKind;
import com.modules.aimodule.repository.AiInsightRepository;
import com.modules.aimodule.service.InsightDataSource.ComandRow;
import com.modules.translationmodule.ai.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Periodi, generazione con testo di riserva, refresh (single-flight e limite di 10 minuti), job notturno. */
class AiInsightServiceTest {

    private static final long AGENCY = 7L;
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private AiInsightRepository repo;
    private InsightDataSource data;
    private InsightWriter writer;
    private OllamaClient ollama;
    private List<Runnable> queued;
    private AiInsightService service;
    private final Map<String, AiInsightJpa> db = new HashMap<>();
    private Instant now;

    @BeforeEach
    void setUp() {
        repo = mock(AiInsightRepository.class);
        data = mock(InsightDataSource.class);
        writer = mock(InsightWriter.class);
        ollama = mock(OllamaClient.class);
        queued = new ArrayList<>();
        service = new AiInsightService(repo, data, writer, ollama, queued::add);
        // giovedì 30 ottobre 2026, 9:00 a Roma
        setNow(LocalDateTime.of(2026, 10, 30, 9, 0));

        when(repo.findByIdAgencyAndKindAndPeriodFrom(anyLong(), anyString(), any())).thenAnswer(i ->
                Optional.ofNullable(db.get(i.getArgument(0) + ":" + i.getArgument(1) + ":" + i.getArgument(2))));
        when(repo.save(any())).thenAnswer(i -> {
            AiInsightJpa r = i.getArgument(0);
            db.put(r.getIdAgency() + ":" + r.getKind() + ":" + r.getPeriodFrom(), r);
            return r;
        });
        when(data.comands(anyLong(), any(), any())).thenReturn(List.of(
                new ComandRow("FROM_TABLE", LocalDateTime.of(2026, 10, 29, 20, 0), 12_000)));
        when(data.products(anyLong(), any(), any())).thenReturn(List.of());
        when(data.reservations(anyLong(), any(), any())).thenReturn(List.of());
        when(data.soldOut(anyLong(), any(), any())).thenReturn(List.of());
        when(data.menuProducts(anyLong())).thenReturn(List.of());
    }

    private void setNow(LocalDateTime rome) {
        now = rome.atZone(ROME).toInstant();
        service.clock = Clock.fixed(now, ROME);
    }

    @Test
    void periodsInRomeTime() {
        LocalDate thu = LocalDate.of(2026, 10, 29);
        assertEquals(new InsightPeriod(InsightKind.DAILY, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28)),
                InsightPeriod.of(InsightKind.DAILY, thu));
        assertEquals(new InsightPeriod(InsightKind.WEEKLY, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)),
                InsightPeriod.of(InsightKind.WEEKLY, thu));
        assertEquals(new InsightPeriod(InsightKind.MENU_TIPS, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 25)),
                InsightPeriod.of(InsightKind.MENU_TIPS, thu));
        // domenica: la settimana in corso non è completa
        assertEquals(LocalDate.of(2026, 10, 19), InsightPeriod.of(InsightKind.WEEKLY, LocalDate.of(2026, 11, 1)).from());
        // lunedì: la settimana appena finita
        assertEquals(new InsightPeriod(InsightKind.WEEKLY, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)),
                InsightPeriod.of(InsightKind.WEEKLY, LocalDate.of(2026, 11, 2)));

        // 23:30 UTC del 29 = 00:30 del 30 a Roma (ora solare, UTC+1)
        service.clock = Clock.fixed(Instant.parse("2026-10-29T23:30:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 10, 30), service.today());
    }

    @Test
    void generateFallsBackToJavaTextWhenAiUnavailable() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        InsightPeriod p = InsightPeriod.of(InsightKind.DAILY, service.today());
        AiInsightJpa r = service.generate(AGENCY, p);
        assertEquals("READY", r.getStatus());
        assertFalse(r.isAiGenerated());
        assertNull(r.getModel());
        assertTrue(r.getText().startsWith("Ieri, giovedì 29 ottobre, hai incassato 120 €"), r.getText());
        assertTrue(r.getHighlightsJson().contains("\"label\":\"Incasso\""));

        AiInsightService.Insight dto = service.toDto(r);
        assertEquals("DAILY", dto.kind());
        assertEquals("2026-10-29", dto.periodFrom());
        assertEquals("2026-10-29", dto.periodTo());
        assertEquals("2026-10-30T09:00:00", dto.generatedAt());
        assertEquals("Incasso", dto.highlights().get(0).label());
    }

    @Test
    void generateUsesAiTextWhenValid() {
        when(writer.write(any(), any())).thenReturn(Optional.of(new InsightWriter.AiText("Testo AI", "qwen2.5:7b")));
        AiInsightJpa r = service.generate(AGENCY, InsightPeriod.of(InsightKind.DAILY, service.today()));
        assertTrue(r.isAiGenerated());
        assertEquals("Testo AI", r.getText());
        assertEquals("qwen2.5:7b", r.getModel());
    }

    @Test
    void generateWithoutComandsIsNoData() {
        when(data.comands(anyLong(), any(), any())).thenReturn(List.of());
        AiInsightJpa r = service.generate(AGENCY, InsightPeriod.of(InsightKind.WEEKLY, service.today()));
        assertEquals("NO_DATA", r.getStatus());
        AiInsightService.Insight dto = service.toDto(r);
        assertNull(dto.text());
        assertTrue(dto.highlights().isEmpty());
        verify(writer, never()).write(any(), any());
    }

    @Test
    void generateErrorIsSavedAsFailed() {
        when(data.comands(anyLong(), any(), any())).thenThrow(new RuntimeException("mongo giù"));
        AiInsightJpa r = service.generate(AGENCY, InsightPeriod.of(InsightKind.DAILY, service.today()));
        assertEquals("FAILED", r.getStatus());
        assertNotNull(r.getError());
    }

    @Test
    void overviewReturnsNullForMissingPeriods() {
        when(ollama.isEnabled()).thenReturn(true);
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        service.generate(AGENCY, InsightPeriod.of(InsightKind.DAILY, service.today()));
        AiInsightService.Overview o = service.overview(AGENCY);
        assertTrue(o.aiEnabled());
        assertNotNull(o.daily());
        assertNull(o.weekly());
        assertNull(o.menuTips());
    }

    @Test
    void refreshIsSingleFlightAndRateLimited() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());

        AiInsightService.Insight first = service.refresh(AGENCY, InsightKind.DAILY);
        assertEquals("PENDING", first.status());
        assertEquals(1, queued.size());

        // in corso: nessun nuovo lavoro, restituisce il PENDING
        AiInsightService.Insight again = service.refresh(AGENCY, InsightKind.DAILY);
        assertEquals("PENDING", again.status());
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertEquals("READY", service.overview(AGENCY).daily().status());

        // terminato ma entro 10 minuti: 429
        setNow(LocalDateTime.of(2026, 10, 30, 9, 9));
        assertThrows(AiInsightService.RateLimitedException.class, () -> service.refresh(AGENCY, InsightKind.DAILY));
        // un altro tipo o un altro locale non sono limitati
        assertEquals("PENDING", service.refresh(AGENCY, InsightKind.WEEKLY).status());
        assertEquals("PENDING", service.refresh(AGENCY + 1, InsightKind.DAILY).status());

        setNow(LocalDateTime.of(2026, 10, 30, 9, 10));
        assertEquals("PENDING", service.refresh(AGENCY, InsightKind.DAILY).status());
        assertEquals(4, queued.size());
    }

    @Test
    void stalePendingIsShownAsFailed() {
        AiInsightJpa r = new AiInsightJpa(AGENCY, InsightKind.DAILY, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29));
        r.setStatus("PENDING");
        r.setGeneratedAt(LocalDateTime.of(2026, 10, 30, 8, 49));
        assertEquals("FAILED", service.toDto(r).status());
        r.setGeneratedAt(LocalDateTime.of(2026, 10, 30, 8, 55));
        assertEquals("PENDING", service.toDto(r).status());
    }

    @Test
    void nightlyDailyOnlyOnWeekdaysAndSkipsReady() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        when(data.agenciesWithCompletedComands(any(), any())).thenReturn(new TreeSet<>(Set.of(1L, 2L, 3L)));
        when(data.comands(eq(2L), any(), any())).thenThrow(new RuntimeException("errore"));
        // il locale 3 ha già il riepilogo pronto
        AiInsightJpa ready = new AiInsightJpa(3L, InsightKind.DAILY, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29));
        ready.setStatus("READY");
        ready.setGeneratedAt(LocalDateTime.of(2026, 10, 30, 5, 30));
        db.put("3:DAILY:2026-10-29", ready);

        service.runNightly(LocalDate.of(2026, 10, 30)); // giovedì
        verify(data).agenciesWithCompletedComands(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29));
        verify(data, times(1)).agenciesWithCompletedComands(any(), any());
        assertEquals("READY", db.get("1:DAILY:2026-10-29").getStatus());
        assertEquals("FAILED", db.get("2:DAILY:2026-10-29").getStatus()); // errore isolato al locale
        verify(data, never()).comands(eq(3L), any(), any());
    }

    @Test
    void nightlyRegeneratesReadyDailyGeneratedBeforeTheDayWasOver() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        when(data.agenciesWithCompletedComands(any(), any())).thenReturn(Set.of(3L));
        // "Genera ora" poco dopo mezzanotte: le comande chiuse più tardi non sarebbero contate
        AiInsightJpa early = new AiInsightJpa(3L, InsightKind.DAILY, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29));
        early.setStatus("READY");
        early.setGeneratedAt(LocalDateTime.of(2026, 10, 30, 0, 10));
        db.put("3:DAILY:2026-10-29", early);

        service.runNightly(LocalDate.of(2026, 10, 30));
        verify(data).comands(eq(3L), any(), any());
    }

    @Test
    void nightlyOnMondayAlsoWeeklyAndMenuTips() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        when(data.agenciesWithCompletedComands(any(), any())).thenReturn(Set.of(1L));
        service.runNightly(LocalDate.of(2026, 11, 2));
        assertTrue(db.containsKey("1:DAILY:2026-11-01"));
        assertTrue(db.containsKey("1:WEEKLY:2026-10-26"));
        assertTrue(db.containsKey("1:MENU_TIPS:2026-10-05"));
        verify(data).menuProducts(1L);
    }

    @Test
    void nightlyRunsOnExecutorNotOnSchedulerThread() {
        when(writer.write(any(), any())).thenReturn(Optional.empty());
        when(data.agenciesWithCompletedComands(any(), any())).thenReturn(Set.of(1L));

        service.nightly();
        // lo scheduler ritorna subito: il lavoro è in coda sull'executor
        assertEquals(1, queued.size());
        verify(data, never()).agenciesWithCompletedComands(any(), any());
        // un secondo avvio mentre il primo è in corso viene saltato
        service.nightly();
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertTrue(db.containsKey("1:DAILY:2026-10-29"));
        // terminato: la notte dopo riparte
        service.nightly();
        assertEquals(2, queued.size());
    }
}
