package com.modules.takeawaymodule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import com.modules.takeawaymodule.model.TakeawaySlotConfigJpa;
import com.modules.takeawaymodule.model.TakeawaySlotCounter;
import com.modules.takeawaymodule.model.TakeawaySlotOverrideJpa;
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

    @Autowired private TakeawaySlotConfigRepository configRepo;
    @Autowired private TakeawaySlotOverrideRepository overrideRepo;
    @Autowired private MongoTemplate mongoTemplate;

    /** Identifica uno slot prenotato (agency, giorno, orario di inizio slot). */
    public record SlotKey(long idAgency, LocalDate date, LocalTime time) {
        public String id() {
            return idAgency + "|" + date + "|" + time.format(HHMM);
        }
    }

    // ── Config ───────────────────────────────────────────────────────────────

    public SlotConfigDto getConfig(long idAgency) {
        TakeawaySlotConfigJpa entity = configRepo.findByIdAgency(idAgency)
                .orElseGet(() -> seedDefault(idAgency));
        return toDto(entity);
    }

    @Transactional
    public SlotConfigDto saveConfig(long idAgency, SlotConfigDto dto) {
        TakeawaySlotConfigJpa entity = configRepo.findByIdAgency(idAgency)
                .orElseGet(() -> new TakeawaySlotConfigJpa(idAgency));
        entity.setSlotDurationMinutes(Math.max(5, Math.min(120, dto.getSlotDurationMinutes())));
        entity.setMaxOrdersPerSlot(Math.max(1, dto.getMaxOrdersPerSlot()));
        entity.setMaxProductsPerSlot(Math.max(1, dto.getMaxProductsPerSlot()));
        try {
            entity.setWeeklyHours(JSON.writeValueAsString(dto.getWeeklyHours() != null ? dto.getWeeklyHours() : Map.of()));
            entity.setClosedDates(JSON.writeValueAsString(dto.getClosedDates() != null ? dto.getClosedDates() : List.of()));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore serializzazione config slot", e);
        }
        configRepo.save(entity);
        return toDto(entity);
    }

    // ── Slot del giorno ──────────────────────────────────────────────────────

    /** Slot per la dashboard (admin): mostra anche i CLOSED e i PAST. */
    public List<SlotDto> getSlotsForDay(long idAgency, LocalDate date) {
        return computeSlots(idAgency, date, false);
    }

    /** Slot per il cliente pubblico: nasconde i CLOSED/PAST/FULL. */
    public List<SlotDto> getAvailableSlotsForDay(long idAgency, LocalDate date) {
        return computeSlots(idAgency, date, true).stream()
                .filter(s -> s.getStatus() == SlotDto.Status.AVAILABLE)
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

    private List<SlotDto> computeSlots(long idAgency, LocalDate date, boolean publicView) {
        TakeawaySlotConfigJpa cfg = configRepo.findByIdAgency(idAgency).orElseGet(() -> seedDefault(idAgency));

        // Chiusura straordinaria?
        Set<String> closedDates = parseClosedDates(cfg.getClosedDates());
        String dateKey = date.toString();
        if (closedDates.contains(dateKey)) return List.of();

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

        // Build dei singoli slot
        List<SlotDto> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
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

                int[] live = liveCounts.getOrDefault(t, new int[]{0, 0});
                TakeawaySlotOverrideJpa ov = overrides.get(t);
                int manualOrders   = ov != null ? ov.getManualOrdersCount() : 0;
                int manualProducts = ov != null ? ov.getManualProductsCount() : 0;
                slot.setOrderCount(live[0] + manualOrders);
                slot.setProductCount(live[1] + manualProducts);
                slot.setManualOrders(manualOrders);
                slot.setManualProducts(manualProducts);

                boolean isClosed = ov != null && ov.isClosed();
                boolean isPast = slotStart.isBefore(now);
                boolean isFull = slot.getOrderCount() >= slot.getMaxOrders()
                              || slot.getProductCount() >= slot.getMaxProducts();

                if (isClosed)      slot.setStatus(SlotDto.Status.CLOSED);
                else if (isPast)   slot.setStatus(SlotDto.Status.PAST);
                else if (isFull)   slot.setStatus(SlotDto.Status.FULL);
                else               slot.setStatus(SlotDto.Status.AVAILABLE);

                // Per la view admin teniamo tutto; per il pubblico filtriamo dopo.
                result.add(slot);
                t = t.plusMinutes(duration);
            }
        }
        return result;
    }

    // ── Prenotazione slot (race-safe) ────────────────────────────────────────

    /**
     * Valida l'orario di ritiro di un ordine asporto pubblico ("yyyy-MM-ddTHH:mm") e prenota
     * atomicamente un posto nello slot. Lancia {@link OrderRejectedException} (409) se lo slot non esiste,
     * è chiuso, è passato o è pieno; (400) se l'orario è mancante/non valido.
     * In caso di fallimento successivo del salvataggio dell'ordine chiamare {@link #releaseSlot}.
     */
    public SlotKey reservePublicSlot(long idAgency, String pickupTime, int products) {
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
        TakeawaySlotConfigJpa cfg = configRepo.findByIdAgency(idAgency).orElseGet(() -> seedDefault(idAgency));

        SlotDto slot = computeSlots(idAgency, date, true).stream()
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

        // Capacità residua al netto degli ordini manuali della dashboard.
        int maxOnlineOrders = slot.getMaxOrders() - slot.getManualOrders();
        int maxOnlineProductsBefore = slot.getMaxProducts() - slot.getManualProducts() - products;
        Query q = new Query(Criteria.where("_id").is(key.id())
                .and("orders").lt(maxOnlineOrders)
                .and("products").lte(maxOnlineProductsBefore));
        Update u = new Update().inc("orders", 1).inc("products", products);
        TakeawaySlotCounter updated = mongoTemplate.findAndModify(q, u,
                FindAndModifyOptions.options().returnNew(true), TakeawaySlotCounter.class);
        if (updated == null) {
            throw new OrderRejectedException(409, "Lo slot di ritiro selezionato non ha più capacità sufficiente, scegline un altro");
        }
        return key;
    }

    /**
     * Registra (senza controllo capacità) un ordine asporto creato dal cameriere nello slot corrispondente,
     * così il contatore resta allineato. Ritorna null se l'orario non è interpretabile.
     */
    public SlotKey consumeSlot(long idAgency, String time, int products) {
        try {
            LocalDateTime pickup = parsePickup(time, LocalDate.now());
            if (pickup == null) return null;
            TakeawaySlotConfigJpa cfg = configRepo.findByIdAgency(idAgency).orElseGet(() -> seedDefault(idAgency));
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
            TakeawaySlotConfigJpa cfg = configRepo.findByIdAgency(idAgency).orElseGet(() -> seedDefault(idAgency));
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
     * Occupazione degli slot di un giorno dai comand Mongo: solo TAKE_AWAY dell'agency, non DELETED,
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

    private SlotConfigDto toDto(TakeawaySlotConfigJpa e) {
        SlotConfigDto dto = new SlotConfigDto();
        dto.setSlotDurationMinutes(e.getSlotDurationMinutes());
        dto.setMaxOrdersPerSlot(e.getMaxOrdersPerSlot());
        dto.setMaxProductsPerSlot(e.getMaxProductsPerSlot());
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
