package com.modules.deliverymodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.deliverymodule.model.DeliverySettingsJpa;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import com.modules.takeawaymodule.model.TakeawaySlotCounter;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import com.modules.takeawaymodule.service.TakeawaySlotService.SlotKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fasce orarie della consegna a domicilio: stessa logica degli slot asporto (orari settimanali, contatore atomico
 * su Mongo, chiusure e "Sospendi asporto" condivisi) ma con config e capienza proprie:
 * {@code slotMinutes}, {@code deliveriesPerSlot} (in consegne, nessuna riserva "su richiesta") e {@code leadMinutes}
 * (preavviso minimo). Orari: {@code weeklyHours} della consegna, oppure quelli dell'asporto se non impostati.
 * Contano le comande HOME non DELETED (online + cameriere).
 */
@Service
public class DeliverySlotService {

    public static final String COUNTERS = "delivery_slot_counters";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired private TakeawaySlotService takeawaySlotService;
    @Autowired private DeliverySettingsService settingsService;
    @Autowired private MongoTemplate mongoTemplate;

    /** Stato di una fascia (regola pura). */
    public static SlotDto.Status status(boolean dayClosed, LocalDateTime slotStart, LocalDateTime now, int leadMinutes,
                                        int currentDeliveries, int capacity) {
        if (dayClosed) return SlotDto.Status.CLOSED;
        if (slotStart.isBefore(now.plusMinutes(Math.max(0, leadMinutes)))) return SlotDto.Status.PAST;
        if (currentDeliveries >= capacity) return SlotDto.Status.FULL;
        return SlotDto.Status.AVAILABLE;
    }

    /** Orari d'inizio delle fasce di un giorno (minuti dalla mezzanotte), come gli slot asporto. */
    public static List<LocalTime> slotStarts(List<SlotConfigDto.TimeRange> ranges, int duration) {
        List<LocalTime> out = new ArrayList<>();
        if (ranges == null || duration <= 0) return out;
        for (SlotConfigDto.TimeRange r : ranges) {
            int startMin, endMin;
            try {
                startMin = LocalTime.parse(r.getStart(), HHMM).toSecondOfDay() / 60;
                endMin = LocalTime.parse(r.getEnd(), HHMM).toSecondOfDay() / 60;
            } catch (Exception e) {
                continue;
            }
            if (endMin <= startMin) endMin = 24 * 60;
            for (int m = startMin; m < endMin; m += duration) out.add(LocalTime.of(m / 60, m % 60));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private Map<String, List<SlotConfigDto.TimeRange>> weekly(long idAgency, DeliverySettingsJpa s) {
        Map<String, List<SlotConfigDto.TimeRange>> own = DeliverySettingsService.weeklyHours(s);
        if (own != null) return own;
        Map<String, List<SlotConfigDto.TimeRange>> takeaway = takeawaySlotService.getConfig(idAgency).getWeeklyHours();
        return takeaway != null ? takeaway : Map.of();
    }

    /**
     * Fasce del giorno. publicView: solo AVAILABLE, nessuna se la consegna non è attiva, è sospesa o il giorno è chiuso.
     */
    public List<SlotDto> slotsForDay(long idAgency, LocalDate date, boolean publicView) {
        DeliverySettingsJpa s = settingsService.settings(idAgency);
        LocalDateTime now = LocalDateTime.now();
        boolean paused = takeawaySlotService.isPaused(idAgency);
        String closed = takeawaySlotService.dayClosedMessage(idAgency, date);
        if (publicView && (!s.isEnabled() || paused || closed != null)) return List.of();

        List<LocalTime> starts = slotStarts(weekly(idAgency, s).getOrDefault(date.getDayOfWeek().name(), List.of()), s.getSlotMinutes());
        Map<LocalTime, Integer> live = countLive(idAgency, date, s.getSlotMinutes());
        List<SlotDto> out = new ArrayList<>();
        for (LocalTime t : starts) {
            int count = live.getOrDefault(t, 0);
            SlotDto dto = new SlotDto();
            dto.setTime(t.format(HHMM));
            dto.setOrderCount(count);
            dto.setMaxOrders(s.getDeliveriesPerSlot());
            dto.setStatus(status(closed != null, LocalDateTime.of(date, t), now, s.getLeadMinutes(), count, s.getDeliveriesPerSlot()));
            if (dto.getStatus() == SlotDto.Status.CLOSED) dto.setClosedReason("DAY");
            if (paused && dto.getStatus() == SlotDto.Status.AVAILABLE) {
                dto.setStatus(SlotDto.Status.CLOSED);
                dto.setClosedReason("PAUSED");
            }
            if (!publicView || dto.getStatus() == SlotDto.Status.AVAILABLE) out.add(dto);
        }
        return out;
    }

    /**
     * Prenota atomicamente un posto nella fascia di consegna ("yyyy-MM-ddTHH:mm", inizio di una fascia).
     * @throws OrderRejectedException 400 con messaggio in italiano (fascia mancante, passata, chiusa o piena)
     */
    public SlotKey reserve(long idAgency, DeliverySettingsJpa s, String deliveryTime) {
        if (deliveryTime == null || deliveryTime.isBlank()) throw new OrderRejectedException(400, "Orario di consegna mancante");
        LocalDateTime when;
        try {
            when = LocalDateTime.parse(deliveryTime.length() > 16 ? deliveryTime.substring(0, 16) : deliveryTime);
        } catch (Exception e) {
            throw new OrderRejectedException(400, "Orario di consegna non valido");
        }
        if (takeawaySlotService.isPaused(idAgency)) {
            throw new OrderRejectedException(400, "Le consegne sono momentaneamente sospese: riprova più tardi");
        }
        String closed = takeawaySlotService.dayClosedMessage(idAgency, when.toLocalDate());
        if (closed != null) throw new OrderRejectedException(400, closed);

        LocalDate date = when.toLocalDate();
        LocalTime time = when.toLocalTime().withSecond(0).withNano(0);
        List<LocalTime> starts = slotStarts(weekly(idAgency, s).getOrDefault(date.getDayOfWeek().name(), List.of()), s.getSlotMinutes());
        if (!starts.contains(time)) {
            throw new OrderRejectedException(400, "La fascia di consegna selezionata non è disponibile, scegline un'altra");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now().plusMinutes(Math.max(0, s.getLeadMinutes())))) {
            throw new OrderRejectedException(400, "La fascia di consegna selezionata non è più prenotabile, scegline un'altra");
        }
        SlotKey key = new SlotKey(idAgency, date, time);
        ensureCounter(key, s.getSlotMinutes());
        Update u = new Update().inc("orders", 1).inc("products", 0);
        boolean ok = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(key.id()).and("orders").lt(s.getDeliveriesPerSlot())), u,
                FindAndModifyOptions.options().returnNew(true), TakeawaySlotCounter.class, COUNTERS) != null;
        if (!ok) throw new OrderRejectedException(400, "La fascia di consegna selezionata è piena, scegline un'altra");
        return key;
    }

    /** Ordine a domicilio del cameriere: registra l'occupazione senza controllo capacità. null se l'orario non è leggibile. */
    public SlotKey consume(long idAgency, String time) {
        try {
            DeliverySettingsJpa s = settingsService.settings(idAgency);
            LocalDateTime when = parse(time, LocalDate.now());
            if (when == null) return null;
            SlotKey key = new SlotKey(idAgency, when.toLocalDate(), snap(when.toLocalTime(), s.getSlotMinutes()));
            ensureCounter(key, s.getSlotMinutes());
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id())), new Update().inc("orders", 1),
                    TakeawaySlotCounter.class, COUNTERS);
            return key;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore registrazione fascia consegna idAgency=" + idAgency, e);
            return null;
        }
    }

    public void release(SlotKey key) {
        if (key == null) return;
        try {
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id()).and("orders").gte(1)),
                    new Update().inc("orders", -1), TakeawaySlotCounter.class, COUNTERS);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore rilascio fascia consegna " + key.id(), e);
        }
    }

    /** →DELETED libera il posto, DELETED→altro lo rioccupa (azione dello staff, senza controllo capacità). */
    public void onStatusChanged(long idAgency, String time, LocalDateTime createdAt, ComandStatus oldStatus, ComandStatus newStatus) {
        if (oldStatus == newStatus) return;
        boolean toDeleted = newStatus == ComandStatus.DELETED;
        boolean fromDeleted = oldStatus == ComandStatus.DELETED;
        if (!toDeleted && !fromDeleted) return;
        try {
            DeliverySettingsJpa s = settingsService.settings(idAgency);
            LocalDateTime when = parse(time, createdAt != null ? createdAt.toLocalDate() : LocalDate.now());
            if (when == null) return;
            SlotKey key = new SlotKey(idAgency, when.toLocalDate(), snap(when.toLocalTime(), s.getSlotMinutes()));
            if (toDeleted) release(key);
            else mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(key.id())), new Update().inc("orders", 1),
                    TakeawaySlotCounter.class, COUNTERS);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore aggiornamento fascia consegna per cambio stato", e);
        }
    }

    private void ensureCounter(SlotKey key, int duration) {
        if (mongoTemplate.exists(new Query(Criteria.where("_id").is(key.id())), TakeawaySlotCounter.class, COUNTERS)) return;
        int live = countLive(key.idAgency(), key.date(), duration).getOrDefault(key.time(), 0);
        try {
            mongoTemplate.insert(new TakeawaySlotCounter(key.id(), key.idAgency(), key.date().toString(),
                    key.time().format(HHMM), live, 0), COUNTERS);
        } catch (DuplicateKeyException ignored) {
            // creato in parallelo
        }
    }

    /** Consegne per fascia di un giorno dai comand Mongo: HOME dell'agency, non DELETED, con orario in quel giorno. */
    private Map<LocalTime, Integer> countLive(long idAgency, LocalDate date, int duration) {
        Map<LocalTime, Integer> map = new HashMap<>();
        try {
            Criteria isoOnDay = Criteria.where("time").regex("^" + date + "T");
            Criteria hhmmCreatedOnDay = new Criteria().andOperator(
                    Criteria.where("time").regex("^\\d{1,2}:\\d{2}$"),
                    Criteria.where("createdAt").gte(date.atStartOfDay()).lt(date.plusDays(1).atStartOfDay()));
            Query q = new Query(new Criteria().andOperator(
                    Criteria.where("idAgency").is(idAgency),
                    Criteria.where("comandWaiterType").is(ComandWaiterType.HOME.name()),
                    Criteria.where("status").ne(ComandStatus.DELETED.name()),
                    new Criteria().orOperator(isoOnDay, hhmmCreatedOnDay)));
            q.fields().include("time").include("createdAt");
            mongoTemplate.find(q, ComandJpa.class, "comand").forEach(c -> {
                LocalDateTime when = parse(c.getTime(), c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate() : date);
                if (when == null || !when.toLocalDate().equals(date)) return;
                map.merge(snap(when.toLocalTime(), duration), 1, Integer::sum);
            });
        } catch (Exception e) {
            ErrorLog.logger.error("Errore lettura consegne per fasce", e);
        }
        return map;
    }

    static LocalDateTime parse(String time, LocalDate fallbackDate) {
        if (time == null || time.isBlank()) return null;
        try {
            if (time.length() >= 13 && time.charAt(10) == 'T') return LocalDateTime.parse(time.substring(0, 16));
            return LocalDateTime.of(fallbackDate, LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("H:mm")));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime snap(LocalTime t, int duration) {
        int d = Math.max(1, duration);
        int total = t.getHour() * 60 + t.getMinute();
        int snapped = (total / d) * d;
        return LocalTime.of(snapped / 60, snapped % 60);
    }
}
