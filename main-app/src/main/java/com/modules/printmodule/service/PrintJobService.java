package com.modules.printmodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.printmodule.dto.PrintJobDto;
import com.modules.printmodule.model.*;
import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
                .filter(PrintJobService::isQueued)
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

    /** Le stampanti TABLET_RAWBT non hanno coda: stampano solo dal browser del tablet (vedi tabletTicket). */
    public static boolean isQueued(PrinterDoc p) {
        return p.getType() != PrinterType.TABLET_RAWBT;
    }

    /**
     * Ristampa manuale dalla dashboard: printerId null = tutte le stampanti abilitate (con coda) che hanno prodotti
     * della comanda. Le stampanti tablet sono escluse (la ristampa tablet passa da tabletTicket con reprint=true).
     */
    public Optional<Integer> reprint(String comandId, long idAgency, String printerId) {
        Optional<ComandJpa> opt = comandRepository.findById(comandId);
        if (opt.isEmpty() || opt.get().getIdAgency() == null || opt.get().getIdAgency() != idAgency) return Optional.empty();

        List<PrinterDoc> printers;
        if (printerId != null && !printerId.isBlank()) {
            Optional<PrinterDoc> p = printerRepository.findByIdAndIdAgency(printerId, idAgency);
            if (p.isEmpty()) return Optional.empty();
            printers = isQueued(p.get()) ? List.of(p.get()) : List.of();
        } else {
            printers = printerRepository.findEnabledByIdAgency(idAgency).stream().filter(PrintJobService::isQueued).toList();
        }
        if (printers.isEmpty()) return Optional.of(0);

        TicketFactory.ComandContext ctx = ticketFactory.context(opt.get());
        int count = 0;
        for (PrinterDoc p : printers) {
            Optional<TicketData> ticket = ticketFactory.ticket(ctx, p, "RISTAMPA");
            if (ticket.isEmpty()) continue;
            if (enqueue(p, comandId, PrintJobKind.REPRINT, TicketRenderer.render(ticket.get(), p.charsPerLine()))) count++;
        }
        return Optional.of(count);
    }

    /** @throws IllegalStateException per le stampanti TABLET_RAWBT (nessuna coda: usare tabletTest). */
    public PrintJobDoc enqueueTest(PrinterDoc printer) {
        if (!isQueued(printer)) throw new IllegalStateException("Stampante tablet: nessuna coda");
        PrintJobDoc job = newJob(printer, null, PrintJobKind.TEST, testLines(printer));
        return jobRepository.insert(job);
    }

    private List<String> testLines(PrinterDoc printer) {
        return TicketRenderer.renderTest(ticketFactory.restaurantName(printer.getIdAgency()),
                printer.getName(), printer.charsPerLine(), LocalDateTime.now());
    }

    // ── Stampa dal tablet (RawBT) ────────────────────────────────────────────

    public enum TabletOutcome { OK, NOT_FOUND, CONFLICT, NO_CONTENT }

    /** escPos = concatenazione degli scontrini di tutte le stampanti tablet (copie incluse); tickets = n. stampanti coinvolte. */
    public record TabletTicket(TabletOutcome outcome, byte[] escPos, int tickets) {
        static TabletTicket of(TabletOutcome o) { return new TabletTicket(o, null, 0); }
    }

    public List<PrinterDoc> tabletPrinters(long idAgency) {
        return printerRepository.findEnabledByIdAgency(idAgency).stream()
                .filter(p -> p.getType() == PrinterType.TABLET_RAWBT).toList();
    }

    public boolean hasQueuedPrinters(long idAgency) {
        return printerRepository.findEnabledByIdAgency(idAgency).stream().anyMatch(PrintJobService::isQueued);
    }

    /**
     * Payload ESC/POS unico per RawBT: un tablet invia a una sola stampante fisica, quindi gli scontrini delle varie
     * stampanti tablet "logiche" (es. cucina / bar con filtri categoria diversi) escono uno dopo l'altro, ognuno col suo taglio.
     * Ogni scontrino generato viene registrato nello storico come job PRINTED (mai preso dai trasporti, che leggono solo
     * PENDING/SENT).
     */
    public TabletTicket tabletTicket(String comandId, long idAgency, boolean reprint) {
        Optional<ComandJpa> opt = comandRepository.findById(comandId);
        if (opt.isEmpty() || opt.get().getIdAgency() == null || opt.get().getIdAgency() != idAgency) {
            return TabletTicket.of(TabletOutcome.NOT_FOUND);
        }
        ComandJpa comand = opt.get();
        if (comand.getStatus() == ComandStatus.DELETED || comand.getStatus() == ComandStatus.AWAIT_PAYMENT) {
            return TabletTicket.of(TabletOutcome.CONFLICT);
        }
        List<PrinterDoc> printers = tabletPrinters(idAgency);
        if (printers.isEmpty()) return TabletTicket.of(TabletOutcome.NO_CONTENT);

        TicketFactory.ComandContext ctx = ticketFactory.context(comand);
        PrintJobKind kind = reprint ? PrintJobKind.REPRINT : PrintJobKind.NEW_ORDER;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = 0;
        for (PrinterDoc p : printers) {
            Optional<TicketData> ticket = ticketFactory.ticket(ctx, p, reprint ? "RISTAMPA" : null);
            if (ticket.isEmpty()) continue;
            List<String> lines = TicketRenderer.render(ticket.get(), p.charsPerLine());
            out.writeBytes(PrinterEncoder.escPos(lines, Math.max(1, p.getCopies())));
            recordTabletJob(p, comandId, kind, lines);
            count++;
        }
        if (count == 0) return TabletTicket.of(TabletOutcome.NO_CONTENT);
        return new TabletTicket(TabletOutcome.OK, out.toByteArray(), count);
    }

    /** Scontrino di prova per una stampante tablet (ESC/POS da passare a RawBT). */
    public byte[] tabletTest(PrinterDoc printer) {
        List<String> lines = testLines(printer);
        recordTabletJob(printer, null, PrintJobKind.TEST, lines);
        return PrinterEncoder.escPos(lines, 1);
    }

    /** Storico: job già PRINTED (inviato al tablet). Nessun dedupKey: la stessa comanda può essere stampata più volte. */
    private void recordTabletJob(PrinterDoc printer, String comandId, PrintJobKind kind, List<String> lines) {
        try {
            PrintJobDoc job = newJob(printer, comandId, kind, lines);
            Instant now = Instant.now();
            job.setDedupKey(null);
            job.setStatus(PrintJobStatus.PRINTED);
            job.setSentAt(now);
            job.setPrintedAt(now);
            jobRepository.insert(job);
        } catch (Exception e) {
            ErrorLog.logger.warn("Stampa tablet: storico non registrato per " + printer.getId() + ": " + e.getMessage());
        }
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
