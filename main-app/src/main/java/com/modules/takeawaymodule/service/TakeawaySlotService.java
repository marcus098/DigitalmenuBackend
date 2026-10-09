package com.modules.takeawaymodule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.takeawaymodule.dto.ClosureDto;
import com.modules.takeawaymodule.dto.PauseStatusDto;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import com.modules.takeawaymodule.model.TakeawayClosureJpa;
import com.modules.takeawaymodule.model.TakeawaySlotConfigJpa;
import com.modules.takeawaymodule.model.TakeawaySlotCounter;
import com.modules.takeawaymodule.model.TakeawaySlotOverrideJpa;
import com.modules.takeawaymodule.repository.TakeawayClosureRepository;
import com.modules.takeawaymodule.repository.TakeawaySlotConfigRepository;
import com.modules.takeawaymodule.repository.TakeawaySlotOverrideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TakeawaySlotService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String MSG_PAUSED = "L'asporto è momentaneamente sospeso: riprova più tardi";
    public static final String MSG_DAY_CLOSED = "Il locale non accetta ordini da asporto in questa data";

    @Autowired private TakeawaySlotConfigRepository configRepo;
    @Autowired private TakeawaySlotOverrideRepository overrideRepo;
    @Autowired private TakeawayClosureRepository closureRepo;
    @Autowired private MongoTemplate mongoTemplate;

    /** Identifica uno slot prenotato (agency, giorno, orario di inizio slot). */
    public record SlotKey(long idAgency, LocalDate date, LocalTime time) {
        public String id() {
            return idAgency + "|" + date + "|" + time.format(HHMM);
        }
        public LocalDateTime start() {
            return LocalDateTime.of(date, time);
        }
    }

    /** Esito della prenotazione: onRequest = posto nella riserva (serve l'approvazione del locale). */
    public record SlotReservation(SlotKey key, boolean onRequest) {
        public LocalDateTime slotStart() { return key.start(); }
    }

    // ── Config ───────────────────────────────────────────────────────────────

    public SlotConfigDto getConfig(long idAgency) {
        return toDto(config(idAgency));
    }

    @Transactional
    public SlotConfigDto saveConfig(long idAgency, SlotConfigDto dto) {
        TakeawaySlotConfigJpa entity = configRepo.findByIdAgency(idAgency)
                .orElseGet(() -> new TakeawaySlotConfigJpa(idAgency));
        entity.setSlotDurationMinutes(Math.max(5, Math.min(120, dto.getSlotDurationMinutes())));
        entity.setMaxOrdersPerSlot(Math.max(1, dto.getMaxOrdersPerSlot()));
        // 0 = limite prodotti disattivato
        entity.setMaxProductsPerSlot(Math.max(0, dto.getMaxProductsPerSlot()));
        entity.setReserveOrdersPerSlot(Math.max(0, Math.min(50, dto.getReserveOrdersPerSlot())));
        try {
            entity.setWeeklyHours(JSON.writeValueAsString(dto.getWeeklyHours() != null ? dto.getWeeklyHours() : Map.of()));
            entity.setClosedDates(JSON.writeValueAsString(dto.getClosedDates() != null ? dto.getClosedDates() : List.of()));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore serializzazione config slot", e);
        }
        configRepo.save(entity);
        return toDto(entity);
    }

    // ── Sospensione asporto ──────────────────────────────────────────────────

    public PauseStatusDto getPauseStatus(long idAgency) {
        TakeawaySlotConfigJpa cfg = config(idAgency);
        boolean active = cfg.isPausedAt(LocalDateTime.now());
        return new PauseStatusDto(active, active ? cfg.getPausedUntil() : null);
    }

    public boolean isPaused(long idAgency) {
        return config(idAgency).isPausedAt(LocalDateTime.now());
    }

    /** @param minutes durata della sospensione; null o ≤ 0 = finché non viene ripresa a mano */
    @Transactional
    public PauseStatusDto pause(long idAgency, Integer minutes) {
        TakeawaySlotConfigJpa cfg = config(idAgency);
        cfg.setPaused(true);
        cfg.setPausedUntil(minutes != null && minutes > 0
                ? LocalDateTime.now().plusMinutes(Math.min(minutes, 7 * 24 * 60)) : null);
        configRepo.save(cfg);
        return getPauseStatus(idAgency);
    }

    @Transactional
    public PauseStatusDto resume(long idAgency) {
        TakeawaySlotConfigJpa cfg = config(idAgency);
        cfg.setPaused(false);
        cfg.setPausedUntil(null);
        configRepo.save(cfg);
        return new PauseStatusDto(false, null);
    }

    // ── Chiusure (giorno / periodo) ──────────────────────────────────────────

    public List<ClosureDto> listClosures(long idAgency) {
        return closureRepo.findByIdAgencyAndToDateGreaterThanEqualOrderByFromDateAsc(idAgency, LocalDate.now())
                .stream().map(TakeawaySlotService::toDto).toList();
    }

    @Transactional
    public ClosureDto addClosure(long idAgency, LocalDate from, LocalDate to, String note) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new OrderRejectedException(400, "Periodo non valido: la data di fine precede quella di inizio");
        }
        if (to.isBefore(LocalDate.now())) {
            throw new OrderRejectedException(400, "Il periodo è già terminato");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new OrderRejectedException(400, "Il periodo di chiusura non può superare un anno");
        }
        String n = note == null || note.isBlank() ? null : note.strip();
        if (n != null && n.length() > 200) n = n.substring(0, 200);
        return toDto(closureRepo.save(new TakeawayClosureJpa(idAgency, from, to, n)));
    }

    @Transactional
    public boolean deleteClosure(long idAgency, long id) {
        Optional<TakeawayClosureJpa> c = closureRepo.findByIdAndIdAgency(id, idAgency);
        c.ifPresent(closureRepo::delete);
        return c.isPresent();
    }

    /** Chiude l'intera giornata (chiusura di un solo giorno). Idempotente. */
    @Transactional
    public void closeDay(long idAgency, LocalDate date) {
        if (closureFor(idAgency, date).isPresent() || parseClosedDates(config(idAgency).getClosedDates()).contains(date.toString())) return;
        closureRepo.save(new TakeawayClosureJpa(idAgency, date, date, null));
    }

    /**
     * Riapre la giornata: rimuove le chiusure di un solo giorno e la data dalle chiusure straordinarie della config.
     * Se il giorno è dentro un periodo di più giorni → 409 (va modificato/eliminato il periodo).
     */
    @Transactional
    public void openDay(long idAgency, LocalDate date) {
        for (TakeawayClosureJpa c : closureRepo.findByIdAgencyAndFromDateLessThanEqualAndToDateGreaterThanEqual(idAgency, date, date)) {
            if (c.getFromDate().equals(c.getToDate())) {
                closureRepo.delete(c);
            } else {
                throw new OrderRejectedException(409, "Il giorno fa parte del periodo di chiusura dal "
                        + c.getFromDate() + " al " + c.getToDate() + ": elimina o modifica il periodo");
            }
        }
        TakeawaySlotConfigJpa cfg = config(idAgency);
        Set<String> closed = parseClosedDates(cfg.getClosedDates());
        if (closed.remove(date.toString())) {
            try {
                cfg.setClosedDates(JSON.writeValueAsString(new ArrayList<>(closed)));
                configRepo.save(cfg);
            } catch (Exception e) {
                ErrorLog.logger.error("Errore aggiornamento closedDates", e);
            }
        }
    }

    // ── Slot del giorno ──────────────────────────────────────────────────────

    /** Slot per la dashboard (admin): mostra anche CLOSED, PAST, FULL. */
    public List<SlotDto> getSlotsForDay(long idAgency, LocalDate date) {
        return computeSlots(idAgency, date, false, true);
    }

    /**
     * Slot per il cliente pubblico: solo AVAILABLE e ON_REQUEST. Vuoto se l'asporto è sospeso o il giorno è chiuso.
     * @param prepayment il locale richiede il prepagamento per l'asporto (limita la riserva a 6 giorni)
     */
    public List<SlotDto> getAvailableSlotsForDay(long idAgency, LocalDate date, boolean prepayment) {
        TakeawaySlotConfigJpa cfg = config(idAgency);
        boolean reserveAllowed = SlotRules.reserveAllowed(cfg.getReserveOrdersPerSlot(), prepayment, date, LocalDate.now());
        return computeSlots(idAgency, date, true, reserveAllowed).stream()
                .filter(s -> s.getStatus() == SlotDto.Status.AVAILABLE || s.getStatus() == SlotDto.Status.ON_REQUEST)
                .toList();
    }

    @Transactional
    public void closeSlot(long idAgency, LocalDate date, LocalTime time) {
        TakeawaySlotOverrideJpa ov = overrideRepo.findByIdAgencyAndSlotDateAndSlotTime(idAgency, date, time)
                .orElseGet(() -> new TakeawaySlotOverrideJpa(idAgency, date, time));
        ov.setClosed(true);
        overrideRepo.save(ov);
    }

    @Transactional
    public void openSlot(long idAgency, LocalDate date, LocalTime time) {
        overrideRepo.findByIdAgencyAndSlotDateAndSlotTime(idAgency, date, time).ifPresent(ov -> {
            ov.setClosed(false);
            overrideRepo.save(ov);
        });
    }

    @Transactional
    public void addManualOrder(long idAgency, LocalDate date, LocalTime time, int products) {
        TakeawaySlotOverrideJpa ov = overrideRepo.findByIdAgencyAndSlotDateAndSlotTime(idAgency, date, time)
                .orElseGet(() -> new TakeawaySlotOverrideJpa(idAgency, date, time));
        ov.setManualOrdersCount(ov.getManualOrdersCount() + 1);
        ov.setManualProductsCount(ov.getManualProductsCount() + Math.max(1, products));
        overrideRepo.save(ov);
    }

    @Transactional
    public void resetManualOrders(long idAgency, LocalDate date, LocalTime time) {
        overrideRepo.findByIdAgencyAndSlotDateAndSlotTime(idAgency, date, time).ifPresent(ov -> {
            ov.setManualOrdersCount(0);
            ov.setManualProductsCount(0);
            overrideRepo.save(ov);
        });
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** Motivo di chiusura dell'intera giornata ("DAY" / "RANGE"), null se aperta. */
    private String dayClosedReason(long idAgency, TakeawaySlotConfigJpa cfg, LocalDate date) {
        if (parseClosedDates(cfg.getClosedDates()).contains(date.toString())) return "DAY";
        return closureFor(idAgency, date)
                .map(c -> c.getFromDate().equals(c.getToDate()) ? "DAY" : "RANGE")
                .orElse(null);
    }

    private Optional<TakeawayClosureJpa> closureFor(long idAgency, LocalDate date) {
        return closureRepo.findByIdAgencyAndFromDateLessThanEqualAndToDateGreaterThanEqual(idAgency, date, date)
                .stream().findFirst();
    }

    private List<SlotDto> computeSlots(long idAgency, LocalDate date, boolean publicView, boolean reserveAllowed) {
        TakeawaySlotConfigJpa cfg = config(idAgency);
        LocalDateTime now = LocalDateTime.now();

        String dayClosed = dayClosedReason(idAgency, cfg, date);
        // Il pubblico non vede nulla se l'asporto è sospeso o il giorno è chiuso
        if (publicView && (dayClosed != null || cfg.isPausedAt(now))) return List.of();

        // Range orari per il giorno della settimana
        Map<String, List<SlotConfigDto.TimeRange>> weekly = parseWeeklyHours(cfg.getWeeklyHours());
        List<SlotConfigDto.TimeRange> ranges = weekly.getOrDefault(date.getDayOfWeek().name(), List.of());
        if (ranges.isEmpty()) return List.of();

        // Overrides del giorno
        Map<LocalTime, TakeawaySlotOverrideJpa> overrides = new HashMap<>();
        for (TakeawaySlotOverrideJpa ov : overrideRepo.findByIdAgencyAndSlotDate(idAgency, date)) {
            overrides.put(ov.getSlotTime(), ov);
        }

        // Occupazione da ordini Mongo
        Map<LocalTime, int[]> liveCounts = countLiveOrdersBySlot(idAgency, date, cfg.getSlotDurationMinutes());

        List<SlotDto> result = new ArrayList<>();
        int duration = cfg.getSlotDurationMinutes();

        for (SlotConfigDto.TimeRange r : ranges) {
            LocalTime t;
            LocalTime endT;
            try {
                t = LocalTime.parse(r.getStart(), HHMM);
                endT = LocalTime.parse(r.getEnd(), HHMM);
            } catch (Exception e) {
                continue;
            }
            while (!t.isAfter(endT.minusMinutes(1))) {
                LocalDateTime slotStart = LocalDateTime.of(date, t);
                SlotDto slot = new SlotDto();
                slot.setTime(t.format(HHMM));
                slot.setMaxOrders(cfg.getMaxOrdersPerSlot());
                slot.setMaxProducts(cfg.getMaxProductsPerSlot());
                slot.setReserveOrders(reserveAllowed ? cfg.getReserveOrdersPerSlot() : 0);

                int[] live = liveCounts.getOrDefault(t, new int[]{0, 0});
                TakeawaySlotOverrideJpa ov = overrides.get(t);
                int manualOrders   = ov != null ? ov.getManualOrdersCount() : 0;
                int manualProducts = ov != null ? ov.getManualProductsCount() : 0;
                slot.setOrderCount(live[0] + manualOrders);
                slot.setProductCount(live[1] + manualProducts);
                slot.setManualOrders(manualOrders);
                slot.setManualProducts(manualProducts);

                boolean slotClosed = ov != null && ov.isClosed();
                SlotRules.Availability a = SlotRules.evaluate(false, slotClosed || dayClosed != null, slotStart, now,
                        slot.getOrderCount(), slot.getProductCount(), 1,
                        cfg.getMaxOrdersPerSlot(), cfg.getMaxProductsPerSlot(), cfg.getReserveOrdersPerSlot(), reserveAllowed);
                switch (a) {
                    case CLOSED -> {
                        slot.setStatus(SlotDto.Status.CLOSED);
                        slot.setClosedReason(dayClosed != null ? dayClosed : "SLOT");
                    }
                    case PAST -> slot.setStatus(SlotDto.Status.PAST);
                    case FULL -> slot.setStatus(SlotDto.Status.FULL);
                    case ON_REQUEST -> slot.setStatus(SlotDto.Status.ON_REQUEST);
                    default -> slot.setStatus(SlotDto.Status.AVAILABLE);
                }

                result.add(slot);
                t = t.plusMinutes(duration);
            }
        }
        return result;
    }

    // ── Prenotazione slot (race-safe) ────────────────────────────────────────

    /**
     * Valida l'orario di ritiro di un ordine asporto pubblico ("yyyy-MM-ddTHH:mm") e prenota atomicamente un posto:
     * prima nella capacità normale, poi (se consentito) nella riserva "su richiesta".
     * Lancia {@link OrderRejectedException} 409 se l'asporto è sospeso, il giorno/slot è chiuso, lo slot è passato
     * o pieno; 400 se l'orario è mancante/non valido.
     * In caso di fallimento successivo del salvataggio dell'ordine chiamare {@link #releaseSlot}.
     *
     * @param prepayment il locale richiede il prepagamento (riserva solo per slot entro 6 giorni)
     */
    public SlotReservation reservePublicSlot(long idAgency, String pickupTime, int products, boolean prepayment) {
        if (pickupTime == null || pickupTime.isBlank()) {
            throw new OrderRejectedException(400, "Orario di ritiro mancante");
        }
        LocalDateTime pickup;
        try {
            pickup = LocalDateTime.parse(pickupTime.length() > 16 ? pickupTime.substring(0, 16) : pickupTime);
        } catch (Exception e) {
            throw new OrderRejectedException(400, "Orario di ritiro non valido");
        }
        if (pickup.isBefore(LocalDateTime.now())) {
            throw new OrderRejectedException(409, "L'orario di ritiro selezionato è già passato, scegline un altro");
        }
        LocalDate date = pickup.toLocalDate();
        String hhmm = pickup.toLocalTime().format(HHMM);
        TakeawaySlotConfigJpa cfg = config(idAgency);

        if (cfg.isPausedAt(LocalDateTime.now())) {
            throw new OrderRejectedException(409, MSG_PAUSED);
        }
        if (parseClosedDates(cfg.getClosedDates()).contains(date.toString())) {
            throw new OrderRejectedException(409, MSG_DAY_CLOSED);
        }
        Optional<TakeawayClosureJpa> closure = closureFor(idAgency, date);
        if (closure.isPresent()) {
            String note = closure.get().getNote();
            throw new OrderRejectedException(409, MSG_DAY_CLOSED + (note != null ? " (" + note + ")" : ""));
        }

        boolean reserveAllowed = SlotRules.reserveAllowed(cfg.getReserveOrdersPerSlot(), prepayment, date, LocalDate.now());
        SlotDto slot = computeSlots(idAgency, date, false, reserveAllowed).stream()
                .filter(s -> hhmm.equals(s.getTime()))
                .findFirst()
                .orElseThrow(() -> new OrderRejectedException(409, "L'orario di ritiro selezionato non è disponibile"));
        switch (slot.getStatus()) {
            case CLOSED -> throw new OrderRejectedException(409, "Lo slot di ritiro selezionato è chiuso, scegline un altro");
            case PAST -> throw new OrderRejectedException(409, "L'orario di ritiro selezionato è già passato, scegline un altro");
            case FULL -> throw new OrderRejectedException(409, "Lo slot di ritiro selezionato è pieno, scegline un altro");
            default -> { }
        }

        SlotKey key = new SlotKey(idAgency, date, LocalTime.parse(hhmm, HHMM));
        ensureCounter(key, cfg.getSlotDurationMinutes());

        // Capacità normale residua al netto degli ordini manuali della dashboard.
        int maxOnlineOrders = slot.getMaxOrders() - slot.getManualOrders();
        Criteria normal = Criteria.where("_id").is(key.id()).and("orders").lt(maxOnlineOrders);
        if (slot.getMaxProducts() > 0) {
            normal = normal.and("products").lte(slot.getMaxProducts() - slot.getManualProducts() - products);
        }
        if (increment(normal, products)) {
            return new SlotReservation(key, false);
        }
        // Riserva "su richiesta": stesso contatore, condizione atomica sul totale normale + riserva.
        if (reserveAllowed) {
            Criteria reserve = Criteria.where("_id").is(key.id())
                    .and("orders").lt(maxOnlineOrders + cfg.getReserveOrdersPerSlot());
            if (increment(reserve, products)) {
                return new SlotReservation(key, true);
            }
        }
        throw new OrderRejectedException(409, "Lo slot di ritiro selezionato non ha più capacità sufficiente, scegline un altro");
    }

    private boolean increment(Criteria condition, int products) {
        Update u = new Update().inc("orders", 1).inc("products", products);
        return mongoTemplate.findAndModify(new Query(condition), u,
                FindAndModifyOptions.options().returnNew(true), TakeawaySlotCounter.class) != null;
    }

    /** Inizio dello slot di ritiro di una comanda asporto (per la scadenza dell'approvazione). null se non interpretabile. */
    public LocalDateTime slotStartFor(long idAgency, String time, LocalDateTime createdAt) {
        LocalDateTime pickup = parsePickup(time, createdAt != null ? createdAt.toLocalDate() : LocalDate.now());
        if (pickup == null) return null;
        TakeawaySlotConfigJpa cfg = config(idAgency);
        return LocalDateTime.of(pickup.toLocalDate(), snapToSlotStart(pickup.toLocalTime(), cfg.getSlotDurationMinutes()));
    }

    /**
     * Registra (senza controllo capacità) un ordine asporto creato dal cameriere nello slot corrispondente,
     * così il contatore resta allineato. Ritorna null se l'orario non è interpretabile.
     */
    public SlotKey consumeSlot(long idAgency, String time, int products) {
        try {
            LocalDateTime pickup = parsePickup(time, LocalDate.now());
            if (pickup == null) return null;
            TakeawaySlotConfigJpa cfg = config(idAgency);
            SlotKey key = new SlotKey(idAgency, pickup.toLocalDate(),
                    snapToSlotStart(pickup.toLocalTime(), cfg.getSlotDurationMinutes()));
            ensureCounter(key, cfg.getSlotDurationMinutes());
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id())),
                    new Update().inc("orders", 1).inc("products", products), TakeawaySlotCounter.class);
            return key;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore registrazione slot asporto idAgency=" + idAgency + " time=" + time, e);
            return null;
        }
    }

    /** Libera un posto nello slot (ordine non salvato o comanda DELETED). No-op se il contatore non esiste. */
    public void releaseSlot(SlotKey key, int products) {
        if (key == null) return;
        try {
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id()).and("orders").gte(1)),
                    new Update().inc("orders", -1).inc("products", -products), TakeawaySlotCounter.class);
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id()).and("products").lt(0)),
                    new Update().set("products", 0), TakeawaySlotCounter.class);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore rilascio slot asporto " + key.id(), e);
        }
    }

    /**
     * Allinea il contatore dello slot a un cambio di stato di una comanda asporto:
     * →DELETED libera il posto, DELETED→altro lo rioccupa (senza controllo capacità: è un'azione dello staff).
     * COMPLETED continua a occupare lo slot: la capacità rappresenta i ritiri pianificati nella finestra.
     */
    public void onTakeawayStatusChanged(long idAgency, String time, LocalDateTime createdAt, int products,
                                        ComandStatus oldStatus, ComandStatus newStatus) {
        if (oldStatus == newStatus) return;
        boolean toDeleted = newStatus == ComandStatus.DELETED;
        boolean fromDeleted = oldStatus == ComandStatus.DELETED;
        if (!toDeleted && !fromDeleted) return;
        try {
            LocalDateTime pickup = parsePickup(time, createdAt != null ? createdAt.toLocalDate() : LocalDate.now());
            if (pickup == null) return;
            TakeawaySlotConfigJpa cfg = config(idAgency);
            SlotKey key = new SlotKey(idAgency, pickup.toLocalDate(),
                    snapToSlotStart(pickup.toLocalTime(), cfg.getSlotDurationMinutes()));
            if (toDeleted) {
                releaseSlot(key, products);
            } else {
                // solo se il contatore esiste già: altrimenti verrà inizializzato dal conteggio live
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id())),
                        new Update().inc("orders", 1).inc("products", products), TakeawaySlotCounter.class);
            }
        } catch (Exception e) {
            ErrorLog.logger.error("Errore aggiornamento slot asporto per cambio stato", e);
        }
    }

    /** Crea il contatore dello slot inizializzandolo dal conteggio live (ordini già esistenti), se assente. */
    private void ensureCounter(SlotKey key, int duration) {
        if (mongoTemplate.exists(new Query(Criteria.where("_id").is(key.id())), TakeawaySlotCounter.class)) return;
        int[] live = countLiveOrdersBySlot(key.idAgency(), key.date(), duration)
                .getOrDefault(key.time(), new int[]{0, 0});
        try {
            mongoTemplate.insert(new TakeawaySlotCounter(key.id(), key.idAgency(), key.date().toString(),
                    key.time().format(HHMM), live[0], live[1]));
        } catch (DuplicateKeyException ignored) {
            // creato in parallelo da un'altra richiesta/istanza: va bene così
        }
    }

    /**
     * Occupazione degli slot di un giorno dai comand Mongo: solo TAKE_AWAY dell'agency, non DELETED
     * (inclusi quelli in attesa di pagamento/approvazione, che tengono occupato il posto),
     * con ritiro nel giorno richiesto (ISO "yyyy-MM-ddTHH:mm", oppure "HH:mm" creato in quel giorno).
     * I COMPLETED restano conteggiati: la capacità rappresenta i ritiri pianificati nello slot.
     */
    private Map<LocalTime, int[]> countLiveOrdersBySlot(long idAgency, LocalDate date, int duration) {
        Map<LocalTime, int[]> map = new HashMap<>();
        try {
            Criteria isoOnDay = Criteria.where("time").regex("^" + date + "T");
            Criteria hhmmCreatedOnDay = new Criteria().andOperator(
                    Criteria.where("time").regex("^\\d{1,2}:\\d{2}$"),
                    Criteria.where("createdAt").gte(date.atStartOfDay()).lt(date.plusDays(1).atStartOfDay()));
            Query q = new Query(new Criteria().andOperator(
                    Criteria.where("idAgency").is(idAgency),
                    Criteria.where("comandWaiterType").is(ComandWaiterType.TAKE_AWAY.name()),
                    Criteria.where("status").ne(ComandStatus.DELETED.name()),
                    new Criteria().orOperator(isoOnDay, hhmmCreatedOnDay)));
            mongoTemplate.find(q, ComandJpa.class).forEach(c -> {
                LocalDateTime pickup = parsePickup(c.getTime(),
                        c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate() : date);
                if (pickup == null || !pickup.toLocalDate().equals(date)) return;
                LocalTime slotStart = snapToSlotStart(pickup.toLocalTime(), duration);
                int products = c.getOrders() == null ? 0 : c.getOrders().stream()
                        .mapToInt(o -> o.getProducts() == null ? 0 :
                                o.getProducts().stream().mapToInt(p -> p.getQuantity()).sum())
                        .sum();
                map.merge(slotStart, new int[]{1, products},
                        (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
            });
        } catch (Exception e) {
            ErrorLog.logger.error("Errore lettura ordini takeaway per slots", e);
        }
        return map;
    }

    private LocalDateTime parsePickup(String pickup, LocalDate fallbackDate) {
        if (pickup == null || pickup.isBlank()) return null;
        try {
            // ISO datetime "YYYY-MM-DDTHH:mm[:ss]"
            if (pickup.length() >= 13 && pickup.charAt(10) == 'T') {
                return LocalDateTime.parse(pickup.substring(0, 16));
            }
            // Solo orario "HH:mm" → assumiamo fallbackDate (oggi)
            LocalTime t = LocalTime.parse(pickup, HHMM);
            return LocalDateTime.of(fallbackDate, t);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime snapToSlotStart(LocalTime t, int duration) {
        int totalMin = t.getHour() * 60 + t.getMinute();
        int snapped = (totalMin / duration) * duration;
        return LocalTime.of(snapped / 60, snapped % 60);
    }

    private TakeawaySlotConfigJpa config(long idAgency) {
        return configRepo.findByIdAgency(idAgency).orElseGet(() -> seedDefault(idAgency));
    }

    private TakeawaySlotConfigJpa seedDefault(long idAgency) {
        TakeawaySlotConfigJpa e = new TakeawaySlotConfigJpa(idAgency);
        // Default: pranzo 12-14 + cena 19-23 tutti i giorni tranne lunedì.
        Map<String, List<SlotConfigDto.TimeRange>> def = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d == DayOfWeek.MONDAY) { def.put(d.name(), List.of()); continue; }
            def.put(d.name(), List.of(
                    new SlotConfigDto.TimeRange("12:00", "14:00"),
                    new SlotConfigDto.TimeRange("19:00", "23:00")
            ));
        }
        try {
            e.setWeeklyHours(JSON.writeValueAsString(def));
        } catch (Exception ex) {
            ErrorLog.logger.error("Errore seed default config slot", ex);
        }
        return configRepo.save(e);
    }

    private static ClosureDto toDto(TakeawayClosureJpa c) {
        return new ClosureDto(c.getId(), c.getFromDate().toString(), c.getToDate().toString(), c.getNote());
    }

    private SlotConfigDto toDto(TakeawaySlotConfigJpa e) {
        SlotConfigDto dto = new SlotConfigDto();
        dto.setSlotDurationMinutes(e.getSlotDurationMinutes());
        dto.setMaxOrdersPerSlot(e.getMaxOrdersPerSlot());
        dto.setMaxProductsPerSlot(e.getMaxProductsPerSlot());
        dto.setReserveOrdersPerSlot(e.getReserveOrdersPerSlot());
        dto.setWeeklyHours(parseWeeklyHours(e.getWeeklyHours()));
        dto.setClosedDates(new ArrayList<>(parseClosedDates(e.getClosedDates())));
        return dto;
    }

    private Map<String, List<SlotConfigDto.TimeRange>> parseWeeklyHours(String json) {
        try {
            return JSON.readValue(json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<Map<String, List<SlotConfigDto.TimeRange>>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Set<String> parseClosedDates(String json) {
        try {
            return new LinkedHashSet<>(JSON.readValue(json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }
}
