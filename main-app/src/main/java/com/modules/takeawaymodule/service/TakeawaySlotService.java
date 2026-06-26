package com.modules.takeawaymodule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import com.modules.takeawaymodule.model.TakeawaySlotConfigJpa;
import com.modules.takeawaymodule.model.TakeawaySlotOverrideJpa;
import com.modules.takeawaymodule.repository.TakeawaySlotConfigRepository;
import com.modules.takeawaymodule.repository.TakeawaySlotOverrideRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private MongoComandRepository comandRepo;

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

    private Map<LocalTime, int[]> countLiveOrdersBySlot(long idAgency, LocalDate date, int duration) {
        Map<LocalTime, int[]> map = new HashMap<>();
        try {
            // Tutti i comand dell'agency, filtriamo i takeaway con pickup parseabile sul giorno target.
            comandRepo.findByIdAgency(idAgency).forEach(c -> {
                if (!(c instanceof com.modules.common.model.ComandFromWaiter cfw)) return;
                if (cfw.getComandWaiterType() != com.modules.common.model.enums.ComandWaiterType.TAKE_AWAY) return;
                LocalDateTime pickup = parsePickup(cfw.getTime(), date);
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
