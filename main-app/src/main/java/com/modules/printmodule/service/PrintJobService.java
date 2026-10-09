package com.modules.printmodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.printmodule.dto.PrintJobDto;
import com.modules.printmodule.model.*;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Accodamento dei job di stampa + transizioni di stato comuni ai trasporti (CloudPRNT e bridge). */
@Service
public class PrintJobService {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration SENT_TIMEOUT = Duration.ofMinutes(2);

    /** Cosa ha scatenato la richiesta di stampa. */
    public enum Trigger { CREATED, ACCEPTED }

    private final PrinterRepository printerRepository;
    private final PrintJobRepository jobRepository;
    private final MongoComandReadRepository comandRepository;
    private final TicketFactory ticketFactory;

    public PrintJobService(PrinterRepository printerRepository, PrintJobRepository jobRepository,
                           MongoComandReadRepository comandRepository, TicketFactory ticketFactory) {
        this.printerRepository = printerRepository;
        this.jobRepository = jobRepository;
        this.comandRepository = comandRepository;
        this.ticketFactory = ticketFactory;
    }

    // ── Accodamento ──────────────────────────────────────────────────────────

    /** Chiamato dal listener eventi: accoda un NEW_ORDER per ogni stampante abilitata la cui regola corrisponde. */
    public int enqueueForComand(String comandId, long idAgency, Trigger trigger) {
        Optional<ComandJpa> opt = comandRepository.findById(comandId);
        if (opt.isEmpty()) {
            ErrorLog.logger.warn("Stampa: comanda " + comandId + " non trovata");
            return 0;
        }
        ComandJpa comand = opt.get();
        if (comand.getIdAgency() == null || comand.getIdAgency() != idAgency) return 0;
        if (comand.getStatus() == ComandStatus.DELETED) return 0;

        List<PrinterDoc> printers = printerRepository.findEnabledByIdAgency(idAgency).stream()
                .filter(p -> shouldPrint(p.getPrintOn(), trigger, comand.getStatus()))
                .toList();
        if (printers.isEmpty()) return 0;

        TicketFactory.ComandContext ctx = ticketFactory.context(comand);
        int count = 0;
        for (PrinterDoc p : printers) {
            Optional<TicketData> ticket = ticketFactory.ticket(ctx, p, null);
            if (ticket.isEmpty()) continue; // nessun prodotto delle categorie di questa stampante
            if (enqueue(p, comandId, PrintJobKind.NEW_ORDER, TicketRenderer.render(ticket.get(), p.charsPerLine()))) count++;
        }
        return count;
    }

    static boolean shouldPrint(PrintOn printOn, Trigger trigger, ComandStatus status) {
        PrintOn rule = printOn == null ? PrintOn.CREATED : printOn;
        if (trigger == Trigger.CREATED) {
            // una comanda creata direttamente in PROGRESS (es. dal cameriere) è già "accettata"
            return rule == PrintOn.CREATED || status == ComandStatus.PROGRESS;
        }
        return rule == PrintOn.ACCEPTED;
    }

    /** Ristampa manuale dalla dashboard: printerId null = tutte le stampanti abilitate che hanno prodotti della comanda. */
    public Optional<Integer> reprint(String comandId, long idAgency, String printerId) {
        Optional<ComandJpa> opt = comandRepository.findById(comandId);
        if (opt.isEmpty() || opt.get().getIdAgency() == null || opt.get().getIdAgency() != idAgency) return Optional.empty();

        List<PrinterDoc> printers;
        if (printerId != null && !printerId.isBlank()) {
            Optional<PrinterDoc> p = printerRepository.findByIdAndIdAgency(printerId, idAgency);
            if (p.isEmpty()) return Optional.empty();
            printers = List.of(p.get());
        } else {
            printers = printerRepository.findEnabledByIdAgency(idAgency);
        }

        TicketFactory.ComandContext ctx = ticketFactory.context(opt.get());
        int count = 0;
        for (PrinterDoc p : printers) {
            Optional<TicketData> ticket = ticketFactory.ticket(ctx, p, "RISTAMPA");
            if (ticket.isEmpty()) continue;
            if (enqueue(p, comandId, PrintJobKind.REPRINT, TicketRenderer.render(ticket.get(), p.charsPerLine()))) count++;
        }
        return Optional.of(count);
    }

    public PrintJobDoc enqueueTest(PrinterDoc printer) {
        List<String> lines = TicketRenderer.renderTest(ticketFactory.restaurantName(printer.getIdAgency()),
                printer.getName(), printer.charsPerLine(), LocalDateTime.now());
        PrintJobDoc job = newJob(printer, null, PrintJobKind.TEST, lines);
        return jobRepository.insert(job);
    }

    /** @return false se il NEW_ORDER era già stato accodato (idempotenza via indice unique su dedupKey). */
    boolean enqueue(PrinterDoc printer, String comandId, PrintJobKind kind, List<String> lines) {
        try {
            jobRepository.insert(newJob(printer, comandId, kind, lines));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private PrintJobDoc newJob(PrinterDoc printer, String comandId, PrintJobKind kind, List<String> lines) {
        PrintJobDoc job = new PrintJobDoc();
        job.setIdAgency(printer.getIdAgency());
        job.setPrinterId(printer.getId());
        job.setComandId(comandId);
        job.setKind(kind);
        job.setStatus(PrintJobStatus.PENDING);
        job.setCreatedAt(Instant.now());
        job.setCopies(Math.max(1, printer.getCopies()));
        job.setContent(new ArrayList<>(lines));
        if (kind == PrintJobKind.NEW_ORDER) job.setDedupKey(printer.getId() + ":" + comandId + ":" + kind);
        return job;
    }

    // ── Trasporti ────────────────────────────────────────────────────────────

    public Optional<PrintJobDoc> peekNext(PrinterDoc printer) {
        return jobRepository.peekNextPending(printer.getId());
    }

    public Optional<PrintJobDoc> claim(PrinterDoc printer, String jobId) {
        return jobId == null || jobId.isBlank()
                ? jobRepository.claimNext(printer.getId())
                : jobRepository.claimById(printer.getId(), jobId);
    }

    /** Conferma (ok) o errore di stampa di un job SENT. Ignora job in altri stati (ack duplicati). */
    public Optional<PrintJobDoc> complete(PrinterDoc printer, String jobId, boolean ok, String error) {
        Optional<PrintJobDoc> job = jobId == null || jobId.isBlank()
                ? jobRepository.findLatestSent(printer.getId())
                : jobRepository.findByIdAndPrinterId(jobId, printer.getId());
        job.filter(j -> j.getStatus() == PrintJobStatus.SENT).ifPresent(j -> {
            if (ok) jobRepository.markPrinted(j.getId());
            else jobRepository.markFailedAttempt(j, error, MAX_ATTEMPTS);
        });
        return job;
    }

    /** Job SENT senza conferma da più di 2 minuti → di nuovo PENDING (o FAILED dopo 5 tentativi). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void requeueStale() {
        try {
            for (PrintJobDoc j : jobRepository.findStaleSent(Instant.now().minus(SENT_TIMEOUT))) {
                jobRepository.markFailedAttempt(j, "Nessuna conferma dalla stampante", MAX_ATTEMPTS);
            }
        } catch (Exception e) {
            ErrorLog.logger.error("Stampa: errore requeue job scaduti", e);
        }
    }

    public List<PrintJobDto> recentJobs(PrinterDoc printer, int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        return jobRepository.findRecentByPrinter(printer.getId(), lim).stream().map(j -> toDto(j, printer)).toList();
    }

    public static PrintJobDto toDto(PrintJobDoc j, PrinterDoc printer) {
        return new PrintJobDto(j.getId(), j.getComandId(), j.getKind(), j.getStatus(), j.getAttempts(),
                j.getCreatedAt(), j.getSentAt(), j.getPrintedAt(), j.getLastError(),
                TicketRenderer.toPlainText(j.getContent(), printer.charsPerLine()));
    }
}
