package com.modules.deliverymodule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.deliverymodule.dto.DeliverySettingsDto;
import com.modules.deliverymodule.dto.DeliveryZone;
import com.modules.deliverymodule.dto.PublicDeliveryInfo;
import com.modules.deliverymodule.model.DeliverySettingsJpa;
import com.modules.deliverymodule.otp.OtpSettings;
import com.modules.deliverymodule.repository.DeliverySettingsRepository;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.service.PrepaymentPolicy;
import com.modules.stylemodule.repository.StyleRepository;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Impostazioni della consegna a domicilio (titolare) e vista pubblica per il menu cliente. */
@Service
public class DeliverySettingsService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DAYS = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    @Autowired private DeliverySettingsRepository repository;
    @Autowired private PrepaymentPolicy prepaymentPolicy;
    @Autowired private TakeawaySlotService takeawaySlotService;
    @Autowired(required = false) private StyleRepository styleRepository;
    /** Solo configurazione (nessuna dipendenza da servizi): niente cicli tra bean. */
    @Autowired(required = false) private OtpSettings otpSettings;

    /** Impostazioni lette (nessuna riga = valori predefiniti, consegna disattivata). */
    public DeliverySettingsJpa settings(long idAgency) {
        return repository.findByIdAgency(idAgency).orElseGet(() -> new DeliverySettingsJpa(idAgency));
    }

    /** Verifica SMS del telefono attiva sul server (stessa regola di OtpService.enabled). */
    public boolean phoneVerificationEnabled() {
        return otpSettings != null && otpSettings.verificationEnabled();
    }

    public boolean onlineAvailable(long idAgency) {
        try {
            return prepaymentPolicy.onlinePaymentAvailable(idAgency);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore lettura pagamenti online idAgency=" + idAgency, e);
            return false;
        }
    }

    public DeliverySettingsDto get(long idAgency) {
        DeliverySettingsJpa e = settings(idAgency);
        DeliverySettingsDto dto = toDto(e);
        // Primo accesso: proponi l'indirizzo del locale (il titolare lo geocodifica e sposta il pin)
        if (dto.getOriginAddress() == null && styleRepository != null) {
            try {
                styleRepository.findByIdAgencyAndDeleted(idAgency, false)
                        .map(s -> s.getAddress())
                        .filter(a -> a != null && !a.isBlank())
                        .ifPresent(dto::setOriginAddress);
            } catch (Exception ex) {
                ErrorLog.logger.warn("Indirizzo del locale non leggibile idAgency={}", idAgency);
            }
        }
        dto.setOnlinePaymentAvailable(onlineAvailable(idAgency));
        dto.setPhoneVerification(phoneVerificationEnabled());
        return dto;
    }

    /** @throws OrderRejectedException 400 con messaggio in italiano se i dati non sono validi */
    @Transactional
    public DeliverySettingsDto save(long idAgency, DeliverySettingsDto dto) {
        if (dto == null) throw new OrderRejectedException(400, "Dati mancanti");
        List<DeliveryZone> zones;
        try {
            zones = DeliveryRules.normalizeZones(dto.getZones());
        } catch (IllegalArgumentException ex) {
            throw new OrderRejectedException(400, ex.getMessage());
        }
        DeliveryRules.PaymentMode mode = parseMode(dto.getPaymentMode());
        if (mode == null) throw new OrderRejectedException(400, "Modalità di pagamento non valida");
        List<String> methods = DeliveryRules.normalizeMethods(dto.getOnDeliveryMethods());
        if (mode != DeliveryRules.PaymentMode.ONLINE_REQUIRED && methods.isEmpty()) {
            throw new OrderRejectedException(400, "Scegli almeno un metodo di pagamento alla consegna (contanti o POS)");
        }
        boolean online = onlineAvailable(idAgency);
        if (mode == DeliveryRules.PaymentMode.ONLINE_REQUIRED && !online) {
            throw new OrderRejectedException(400, "Per richiedere il pagamento online attiva prima i pagamenti online (Stripe o SumUp)");
        }
        boolean hasOrigin = DeliveryRules.validCoordinates(dto.getOriginLat(), dto.getOriginLng());
        if ((dto.getOriginLat() != null || dto.getOriginLng() != null) && !hasOrigin) {
            throw new OrderRejectedException(400, "Coordinate del punto di partenza non valide");
        }
        if (dto.isEnabled()) {
            if (!hasOrigin) throw new OrderRejectedException(400, "Imposta il punto di partenza delle consegne");
            if (zones.isEmpty()) throw new OrderRejectedException(400, "Aggiungi almeno una zona di consegna");
        }
        if (dto.getSlotMinutes() < 5 || dto.getSlotMinutes() > 120) {
            throw new OrderRejectedException(400, "La durata della fascia deve essere tra 5 e 120 minuti");
        }
        if (dto.getDeliveriesPerSlot() < 1 || dto.getDeliveriesPerSlot() > 50) {
            throw new OrderRejectedException(400, "Le consegne per fascia devono essere tra 1 e 50");
        }
        if (dto.getLeadMinutes() < 0 || dto.getLeadMinutes() > 24 * 60) {
            throw new OrderRejectedException(400, "Il preavviso deve essere tra 0 e 1440 minuti");
        }
        Long freeOver = dto.getFreeDeliveryOverCents();
        if (freeOver != null && (freeOver < 0 || freeOver > DeliveryRules.MAX_MIN_ORDER_CENTS * 10)) {
            throw new OrderRejectedException(400, "Soglia per la consegna gratuita non valida");
        }
        String address = dto.getOriginAddress() == null || dto.getOriginAddress().isBlank() ? null : dto.getOriginAddress().strip();
        if (address != null && address.length() > 300) address = address.substring(0, 300);

        DeliverySettingsJpa e = repository.findByIdAgency(idAgency).orElseGet(() -> new DeliverySettingsJpa(idAgency));
        e.setEnabled(dto.isEnabled());
        e.setOriginLat(hasOrigin ? dto.getOriginLat() : null);
        e.setOriginLng(hasOrigin ? dto.getOriginLng() : null);
        e.setOriginAddress(address);
        e.setFreeDeliveryOverCents(freeOver == null || freeOver <= 0 ? null : freeOver);
        e.setPaymentMode(mode.name());
        e.setSlotMinutes(dto.getSlotMinutes());
        e.setDeliveriesPerSlot(dto.getDeliveriesPerSlot());
        e.setLeadMinutes(dto.getLeadMinutes());
        e.setRequirePhoneOtpTakeaway(dto.isRequirePhoneOtpTakeaway());
        e.setRoutePlanning(dto.isRoutePlanning());
        try {
            e.setZones(JSON.writeValueAsString(zones));
            e.setOnDeliveryMethods(JSON.writeValueAsString(methods));
            e.setWeeklyHours(dto.getWeeklyHours() == null ? null : JSON.writeValueAsString(validateWeekly(dto.getWeeklyHours())));
        } catch (OrderRejectedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OrderRejectedException(400, "Impostazioni non valide");
        }
        repository.save(e);
        DeliverySettingsDto out = toDto(e);
        out.setOnlinePaymentAvailable(online);
        out.setPhoneVerification(phoneVerificationEnabled());
        return out;
    }

    public PublicDeliveryInfo publicInfo(long idAgency) {
        DeliverySettingsJpa e = settings(idAgency);
        boolean online = onlineAvailable(idAgency);
        List<DeliveryZone> zones = zones(e);
        List<String> methods = methods(e);
        boolean paused;
        try {
            paused = takeawaySlotService.isPaused(idAgency);
        } catch (Exception ex) {
            paused = false;
        }
        boolean phoneVerification = phoneVerificationEnabled();
        return new PublicDeliveryInfo(e.isEnabled(), e.getOriginLat(), e.getOriginLng(), e.getOriginAddress(), zones,
                e.getFreeDeliveryOverCents(), mode(e).name(), methods, e.getSlotMinutes(), e.getDeliveriesPerSlot(),
                e.getLeadMinutes(), phoneVerification && e.isRequirePhoneOtpTakeaway(),
                DeliveryRules.paymentOptions(mode(e), methods, online), online, paused, DeliveryRules.maxKm(zones),
                phoneVerification);
    }

    /**
     * Vista per rider e camerieri (GET /api/delivery/route-info): consegna attiva, calcolo del percorso e
     * punto di partenza. Niente zone, tariffe o pagamenti.
     */
    public Map<String, Object> routeInfo(long idAgency) {
        DeliverySettingsJpa e = settings(idAgency);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", e.isEnabled());
        m.put("routePlanning", e.isRoutePlanning());
        m.put("originLat", e.getOriginLat());
        m.put("originLng", e.getOriginLng());
        m.put("originAddress", e.getOriginAddress());
        return m;
    }

    // ── Lettura dei JSON ─────────────────────────────────────────────────────

    public static List<DeliveryZone> zones(DeliverySettingsJpa e) {
        try {
            List<DeliveryZone> z = JSON.readValue(e.getZones() == null ? "[]" : e.getZones(), new TypeReference<List<DeliveryZone>>() {});
            z.sort(Comparator.comparingDouble(DeliveryZone::getMaxKm));
            return z;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public static List<String> methods(DeliverySettingsJpa e) {
        try {
            return DeliveryRules.normalizeMethods(JSON.readValue(e.getOnDeliveryMethods() == null ? "[]" : e.getOnDeliveryMethods(),
                    new TypeReference<List<String>>() {}));
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static DeliveryRules.PaymentMode mode(DeliverySettingsJpa e) {
        DeliveryRules.PaymentMode m = parseMode(e.getPaymentMode());
        return m == null ? DeliveryRules.PaymentMode.ON_DELIVERY_ONLY : m;
    }

    /** null = stessi orari dell'asporto. */
    public static Map<String, List<SlotConfigDto.TimeRange>> weeklyHours(DeliverySettingsJpa e) {
        if (e.getWeeklyHours() == null || e.getWeeklyHours().isBlank()) return null;
        try {
            return JSON.readValue(e.getWeeklyHours(), new TypeReference<Map<String, List<SlotConfigDto.TimeRange>>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private static DeliveryRules.PaymentMode parseMode(String s) {
        if (s == null) return null;
        try {
            return DeliveryRules.PaymentMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Map<String, List<SlotConfigDto.TimeRange>> validateWeekly(Map<String, List<SlotConfigDto.TimeRange>> in) {
        Map<String, List<SlotConfigDto.TimeRange>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<SlotConfigDto.TimeRange>> en : in.entrySet()) {
            String day = en.getKey() == null ? "" : en.getKey().trim().toUpperCase(Locale.ROOT);
            if (!DAYS.contains(day)) throw new OrderRejectedException(400, "Giorno non valido negli orari: " + en.getKey());
            List<SlotConfigDto.TimeRange> ranges = new ArrayList<>();
            if (en.getValue() != null) for (SlotConfigDto.TimeRange r : en.getValue()) {
                if (r == null || !isHhMm(r.getStart()) || !isHhMm(r.getEnd())) {
                    throw new OrderRejectedException(400, "Orario non valido (usa HH:mm)");
                }
                ranges.add(r);
            }
            out.put(day, ranges);
        }
        return out;
    }

    private static boolean isHhMm(String s) {
        return s != null && s.matches("([01]\\d|2[0-3]):[0-5]\\d");
    }

    private DeliverySettingsDto toDto(DeliverySettingsJpa e) {
        DeliverySettingsDto d = new DeliverySettingsDto();
        d.setEnabled(e.isEnabled());
        d.setOriginLat(e.getOriginLat());
        d.setOriginLng(e.getOriginLng());
        d.setOriginAddress(e.getOriginAddress());
        d.setZones(zones(e));
        d.setFreeDeliveryOverCents(e.getFreeDeliveryOverCents());
        d.setPaymentMode(mode(e).name());
        d.setOnDeliveryMethods(methods(e));
        d.setSlotMinutes(e.getSlotMinutes());
        d.setDeliveriesPerSlot(e.getDeliveriesPerSlot());
        d.setLeadMinutes(e.getLeadMinutes());
        d.setRequirePhoneOtpTakeaway(e.isRequirePhoneOtpTakeaway());
        d.setRoutePlanning(e.isRoutePlanning());
        d.setWeeklyHours(weeklyHours(e));
        return d;
    }
}
