package com.modules.mainapp.controller;

import com.modules.takeawaymodule.dto.PublicSlotDto;
import com.modules.authmodule.repository.UserRepository;
import com.modules.deliverymodule.dto.DeliveryOrderRequest;
import com.modules.deliverymodule.geo.GeocodingService;
import com.modules.deliverymodule.model.DeliverySettingsJpa;
import com.modules.deliverymodule.service.DeliveryOrderService;
import com.modules.deliverymodule.service.DeliveryRules;
import com.modules.deliverymodule.service.DeliverySettingsService;
import com.modules.deliverymodule.service.DeliverySlotService;
import com.modules.deliverymodule.otp.OtpService;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.ordermodule.exception.OrderRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Endpoint pubblici della consegna a domicilio (vedi docs/DOMICILIO.md):
 * <pre>
 *  GET  /api/public/delivery/{localname}                 impostazioni per il menu cliente
 *  GET  /api/public/delivery/{localname}/slots/{date}    fasce disponibili del giorno (yyyy-MM-dd)
 *  POST /api/public/orders/delivery/{localname}          ordine a domicilio
 *  POST /api/public/otp/send   {localname, phone}        invio SMS
 *  POST /api/public/otp/verify {localname, phone, code}  → {phoneToken, expiresAt}
 *  GET  /api/public/geo/search?localname=&q=             max 5 indirizzi vicini al locale
 *  GET  /api/public/geo/reverse?localname=&lat=&lng=     indirizzo del pin
 * </pre>
 * Errori: {"message": "..."} in italiano.
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/public")
@RestController
public class PublicDeliveryController {

    private static final long TEN_MIN = 10 * 60 * 1000L;

    @Autowired private UserRepository userRepository;
    @Autowired private DeliverySettingsService settingsService;
    @Autowired private DeliverySlotService slotService;
    @Autowired private DeliveryOrderService orderService;
    @Autowired private OtpService otpService;
    @Autowired private GeocodingService geocodingService;
    @Autowired private IpRateLimiter rateLimiter;

    /** Ordini a domicilio per IP ogni 10 minuti (anche quelli rifiutati). */
    @org.springframework.beans.factory.annotation.Value("${app.delivery.orders-per-ip-per-10min:10}")
    private int ordersPerIp = 10;

    @GetMapping("/delivery/{localname}")
    public ResponseEntity<?> info(@PathVariable String localname) {
        return withAgency(localname, id -> ResponseEntity.ok(settingsService.publicInfo(id)));
    }

    @GetMapping("/delivery/{localname}/slots/{date}")
    public ResponseEntity<?> slots(@PathVariable String localname, @PathVariable String date) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return error(400, "Data non valida (usa yyyy-MM-dd)");
        }
        return withAgency(localname, id -> ResponseEntity.ok(PublicSlotDto.of(slotService.slotsForDay(id, d, true))));
    }

    @PostMapping("/orders/delivery/{localname}")
    public ResponseEntity<?> order(@PathVariable String localname, @RequestBody DeliveryOrderRequest request,
                                   HttpServletRequest http) {
        if (!rateLimiter.isAllowed("delivery-order:" + http.getRemoteAddr(), ordersPerIp, TEN_MIN)) {
            return error(429, "Troppe richieste. Riprova tra qualche minuto.");
        }
        return withAgency(localname, id -> ResponseEntity.ok(orderService.create(id, request).body()));
    }

    // ── OTP ──────────────────────────────────────────────────────────────────

    @PostMapping("/otp/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String localname = body == null ? null : body.get("localname");
        return withAgency(localname, id -> {
            otpService.send(id, body.get("phone"), http.getRemoteAddr());
            return ResponseEntity.ok(Map.of("sent", true));
        });
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String localname = body == null ? null : body.get("localname");
        return withAgency(localname, id -> {
            OtpService.Verified v = otpService.verify(id, body.get("phone"), body.get("code"), http.getRemoteAddr());
            return ResponseEntity.ok(Map.of("phoneToken", v.phoneToken(), "expiresAt", v.expiresAt().toString()));
        });
    }

    // ── Geocodifica ──────────────────────────────────────────────────────────

    @GetMapping("/geo/search")
    public ResponseEntity<?> search(@RequestParam String localname, @RequestParam String q, HttpServletRequest http) {
        if (!rateLimiter.isAllowed("geo:" + http.getRemoteAddr(), 30, TEN_MIN)) {
            return error(429, "Troppe ricerche. Riprova tra qualche minuto.");
        }
        return withAgency(localname, id -> {
            DeliverySettingsJpa s = settingsService.settings(id);
            double radius = DeliveryRules.maxKm(DeliverySettingsService.zones(s));
            return ResponseEntity.ok(geocodingService.search(q, s.getOriginLat(), s.getOriginLng(), radius > 0 ? radius : 10));
        });
    }

    @GetMapping("/geo/reverse")
    public ResponseEntity<?> reverse(@RequestParam String localname, @RequestParam double lat, @RequestParam double lng,
                                     HttpServletRequest http) {
        if (!rateLimiter.isAllowed("geo:" + http.getRemoteAddr(), 30, TEN_MIN)) {
            return error(429, "Troppe ricerche. Riprova tra qualche minuto.");
        }
        return withAgency(localname, id -> {
            GeocodingService.GeoResult g = geocodingService.reverse(lat, lng);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", g.label());
            m.put("lat", g.lat());
            m.put("lng", g.lng());
            m.put("street", g.street());
            m.put("houseNumber", g.houseNumber());
            m.put("city", g.city());
            m.put("postcode", g.postcode());
            return ResponseEntity.ok(m);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<?> withAgency(String localname, LongFunction<ResponseEntity<?>> fn) {
        if (localname == null || localname.isBlank()) return error(400, "Locale mancante");
        Long id = userRepository.findByUsernameAndDeleted(localname, false).map(u -> u.getIdAgency()).orElse(null);
        if (id == null || id <= 0) return error(404, "Locale non trovato");
        try {
            return fn.apply(id);
        } catch (OrderRejectedException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    static ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
