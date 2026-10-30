package com.modules.aimodule.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.aimodule.model.AiInsightJpa;
import com.modules.aimodule.model.InsightKind;
import com.modules.aimodule.model.InsightStatus;
import com.modules.aimodule.repository.AiInsightRepository;
import com.modules.aimodule.service.InsightFacts.Highlight;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.utilities.BusinessTime;
import com.modules.translationmodule.ai.OllamaClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Riepiloghi AxiomAI (ieri, ultima settimana, consigli sul menu): lettura, rigenerazione su richiesta e job notturno.
 * I numeri li calcola {@link InsightFacts}; l'AI ({@link InsightWriter}) scrive solo il testo, con un testo di
 * riserva deterministico quando Ollama non è disponibile.
 */
@Service
public class AiInsightService {

    /** Intervallo minimo tra due richieste di aggiornamento dello stesso tipo per locale. */
    static final Duration REFRESH_INTERVAL = Duration.ofMinutes(10);
    /** Un PENDING più vecchio di così è considerato fallito (es. riavvio del server durante la generazione). */
    static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);
    static final String RATE_LIMIT_MESSAGE = "Hai già richiesto un aggiornamento da poco, riprova tra qualche minuto";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AiInsightRepository repository;
    private final InsightDataSource dataSource;
    private final InsightWriter writer;
    private final OllamaClient ollama;
    private final Executor executor;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Orologio del locale (sostituibile nei test). */
    Clock clock = Clock.system(BusinessTime.ZONE);

    /** Generazioni in corso (single-flight per locale e tipo). */
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    /** Ultima richiesta manuale di aggiornamento per locale e tipo. */
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();
    /** Job notturno in corso (gira sull'executor, non sul thread unico dello scheduler). */
    private final AtomicBoolean nightlyRunning = new AtomicBoolean(false);

    public AiInsightService(AiInsightRepository repository, InsightDataSource dataSource, InsightWriter writer,
                            OllamaClient ollama, @Qualifier("taskExecutor") Executor executor) {
        this.repository = repository;
        this.dataSource = dataSource;
        this.writer = writer;
        this.ollama = ollama;
        this.executor = executor;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Insight(String kind, String periodFrom, String periodTo, String status, boolean aiGenerated,
                          String text, List<Highlight> highlights, String generatedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Overview(boolean aiEnabled, Insight daily, Insight weekly, Insight menuTips) {}

    /** Troppe richieste di aggiornamento (→ 429). */
    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException(String message) { super(message); }
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(BusinessTime.ZONE));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BusinessTime.ZONE));
    }

    // ── Lettura ──────────────────────────────────────────────────────────────

    /** Riepiloghi dei periodi correnti; null per un tipo se non è ancora stato generato (nessuna generazione qui). */
    public Overview overview(long idAgency) {
        LocalDate today = today();
        return new Overview(ollama.isEnabled(),
                current(idAgency, InsightPeriod.of(InsightKind.DAILY, today)),
                current(idAgency, InsightPeriod.of(InsightKind.WEEKLY, today)),
                current(idAgency, InsightPeriod.of(InsightKind.MENU_TIPS, today)));
    }

    private Insight current(long idAgency, InsightPeriod p) {
        return repository.findByIdAgencyAndKindAndPeriodFrom(idAgency, p.kind().name(), p.from())
                .map(this::toDto).orElse(null);
    }

    Insight toDto(AiInsightJpa r) {
        String status = r.getStatus();
        if (InsightStatus.PENDING.name().equals(status) && isStale(r)) status = InsightStatus.FAILED.name();
        boolean noData = InsightStatus.NO_DATA.name().equals(status);
        return new Insight(r.getKind(), String.valueOf(r.getPeriodFrom()), String.valueOf(r.getPeriodTo()), status,
                r.isAiGenerated(), noData ? null : r.getText(), noData ? List.of() : highlights(r.getHighlightsJson()),
                r.getGeneratedAt() == null ? null : r.getGeneratedAt().format(TIMESTAMP));
    }

    private boolean isStale(AiInsightJpa r) {
        if (running.contains(key(r.getIdAgency(), InsightKind.valueOf(r.getKind())))) return false;
        return r.getGeneratedAt() == null || r.getGeneratedAt().plus(PENDING_TIMEOUT).isBefore(now());
    }

    private List<Highlight> highlights(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<Highlight>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Aggiornamento su richiesta ───────────────────────────────────────────

    /**
     * Rigenera in background il periodo corrente. Se una generazione è già in corso restituisce il record PENDING
     * senza avviarne un'altra.
     *
     * @throws RateLimitedException aggiornamento già richiesto negli ultimi 10 minuti (→ 429)
     */
    public Insight refresh(long idAgency, InsightKind kind) {
        InsightPeriod p = InsightPeriod.of(kind, today());
        String key = key(idAgency, kind);
        if (!running.add(key)) {
            return repository.findByIdAgencyAndKindAndPeriodFrom(idAgency, kind.name(), p.from())
                    .map(this::toDto).orElseGet(() -> pendingDto(p));
        }
        Instant now = clock.instant();
        boolean[] limited = {false};
        lastRefresh.compute(key, (k, last) -> {
            if (last != null && Duration.between(last, now).compareTo(REFRESH_INTERVAL) < 0) {
                limited[0] = true;
                return last;
            }
            return now;
        });
        if (limited[0]) {
            running.remove(key);
            throw new RateLimitedException(RATE_LIMIT_MESSAGE);
        }
        AiInsightJpa pending;
        try {
            pending = markPending(idAgency, p);
            executor.execute(() -> {
                try {
                    generate(idAgency, p);
                } finally {
                    running.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            running.remove(key);
            lastRefresh.remove(key);
            throw new IllegalStateException("Impossibile avviare la generazione", e);
        } catch (RuntimeException e) {
            running.remove(key);
            throw e;
        }
        return toDto(pending);
    }

    private Insight pendingDto(InsightPeriod p) {
        return new Insight(p.kind().name(), p.from().toString(), p.to().toString(), InsightStatus.PENDING.name(),
                false, null, List.of(), now().format(TIMESTAMP));
    }

    private AiInsightJpa markPending(long idAgency, InsightPeriod p) {
        AiInsightJpa r = record(idAgency, p);
        r.setStatus(InsightStatus.PENDING.name());
        r.setError(null);
        r.setGeneratedAt(now());
        return repository.save(r);
    }

    private AiInsightJpa record(long idAgency, InsightPeriod p) {
        AiInsightJpa r = repository.findByIdAgencyAndKindAndPeriodFrom(idAgency, p.kind().name(), p.from())
                .orElseGet(() -> new AiInsightJpa(idAgency, p.kind(), p.from(), p.to()));
        r.setPeriodTo(p.to());
        return r;
    }

    // ── Generazione ──────────────────────────────────────────────────────────

    /** Calcola i fatti, scrive il testo (AI o riserva) e salva. Non lancia eccezioni: in caso di errore salva FAILED. */
    AiInsightJpa generate(long idAgency, InsightPeriod p) {
        try {
            InsightFacts.Result result = InsightFacts.compute(load(idAgency, p));
            AiInsightJpa r = record(idAgency, p);
            r.setError(null);
            if (result.noData()) {
                r.setStatus(InsightStatus.NO_DATA.name());
                r.setText(null);
                r.setHighlightsJson("[]");
                r.setFactsJson(null);
                r.setAiGenerated(false);
                r.setModel(null);
            } else {
                Optional<InsightWriter.AiText> ai = writer.write(p.kind(), result.facts());
                r.setStatus(InsightStatus.READY.name());
                r.setText(ai.map(InsightWriter.AiText::text).orElse(result.fallbackText()));
                r.setAiGenerated(ai.isPresent());
                r.setModel(ai.map(InsightWriter.AiText::model).orElse(null));
                r.setHighlightsJson(mapper.writeValueAsString(result.highlights()));
                r.setFactsJson(mapper.writeValueAsString(result.facts()));
            }
            r.setGeneratedAt(now());
            return repository.save(r);
        } catch (Exception e) {
            ErrorLog.logger.error("AxiomAI: generazione " + p.kind() + " non riuscita (agency " + idAgency + ")", e);
            try {
                AiInsightJpa r = record(idAgency, p);
                r.setStatus(InsightStatus.FAILED.name());
                r.setError("Errore durante la generazione del riepilogo");
                r.setGeneratedAt(now());
                return repository.save(r);
            } catch (Exception inner) {
                ErrorLog.logger.error("AxiomAI: impossibile salvare lo stato FAILED (agency " + idAgency + ")", inner);
                return null;
            }
        }
    }

    /** Dati grezzi del periodo, con la finestra di confronto (4 settimane prima) per DAILY e WEEKLY. */
    InsightFacts.Dataset load(long idAgency, InsightPeriod p) {
        LocalDate comandsFrom = p.kind() == InsightKind.MENU_TIPS ? p.from()
                : p.from().minusWeeks(InsightFacts.COMPARISON_WEEKS);
        return new InsightFacts.Dataset(p,
                dataSource.comands(idAgency, comandsFrom, p.to()),
                dataSource.products(idAgency, p.from(), p.to()),
                dataSource.reservations(idAgency, p.from(), p.to()),
                dataSource.soldOut(idAgency, p.from(), p.to()),
                p.kind() == InsightKind.MENU_TIPS ? dataSource.menuProducts(idAgency) : List.of());
    }

    // ── Job notturno ─────────────────────────────────────────────────────────

    /** Dopo il job delle traduzioni (4:30): ogni giorno il riepilogo di ieri, il lunedì anche settimana e menu. */
    /**
     * Il lavoro (minuti o ore con Ollama su CPU) gira sull'executor: lo scheduler di Spring ha un solo thread e
     * bloccarlo fermerebbe gli altri job (scadenza comande, stampe, promemoria prenotazioni).
     */
    @Scheduled(cron = "0 30 5 * * *", zone = "Europe/Rome")
    public void nightly() {
        if (!nightlyRunning.compareAndSet(false, true)) {
            ErrorLog.logger.warn("AxiomAI: job notturno precedente ancora in corso, salto");
            return;
        }
        LocalDate today = today();
        try {
            executor.execute(() -> {
                try {
                    runNightly(today);
                } finally {
                    nightlyRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            nightlyRunning.set(false);
            ErrorLog.logger.error("AxiomAI: impossibile avviare il job notturno", e);
        }
    }

    /** Sequenziale: un locale alla volta (Ollama gira su CPU). Salta i periodi già pronti. */
    void runNightly(LocalDate today) {
        List<InsightKind> kinds = today.getDayOfWeek() == DayOfWeek.MONDAY
                ? List.of(InsightKind.DAILY, InsightKind.WEEKLY, InsightKind.MENU_TIPS)
                : List.of(InsightKind.DAILY);
        int done = 0;
        for (InsightKind kind : kinds) {
            InsightPeriod p = InsightPeriod.of(kind, today);
            Set<Long> agencies;
            try {
                agencies = dataSource.agenciesWithCompletedComands(p.from(), p.to());
            } catch (Exception e) {
                ErrorLog.logger.error("AxiomAI: impossibile leggere i locali per " + kind, e);
                continue;
            }
            for (long idAgency : agencies) {
                String key = key(idAgency, kind);
                try {
                    // Un READY generato prima delle 4 del giorno dopo il periodo (es. "Genera ora" a mezzanotte)
                    // può non contare le comande chiuse più tardi: di notte si rigenera
                    LocalDateTime complete = p.to().plusDays(1).atTime(4, 0);
                    boolean ready = repository.findByIdAgencyAndKindAndPeriodFrom(idAgency, kind.name(), p.from())
                            .filter(r -> InsightStatus.READY.name().equals(r.getStatus()))
                            .filter(r -> r.getGeneratedAt() != null && !r.getGeneratedAt().isBefore(complete))
                            .isPresent();
                    if (ready || !running.add(key)) continue;
                    try {
                        generate(idAgency, p);
                        done++;
                    } finally {
                        running.remove(key);
                    }
                } catch (Exception e) {
                    ErrorLog.logger.error("AxiomAI: job notturno " + kind + " non riuscito (agency " + idAgency + ")", e);
                }
            }
        }
        if (done > 0) ErrorLog.logger.info("AxiomAI: job notturno completato ({} riepiloghi)", done);
    }

    static String key(long idAgency, InsightKind kind) {
        return idAgency + ":" + kind.name();
    }
}
