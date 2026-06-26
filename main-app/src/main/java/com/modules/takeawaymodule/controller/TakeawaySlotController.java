package com.modules.takeawaymodule.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Endpoint per la gestione degli slot asporto.
 *
 *  Dashboard (auth required, ADMIN):
 *   GET  /api/takeaway/slots/config         → config settimanale + giorni chiusi
 *   PUT  /api/takeaway/slots/config         → salva config
 *   GET  /api/takeaway/slots/day/{date}     → slot del giorno (admin view: include closed/past)
 *   POST /api/takeaway/slots/day/{date}/time/{time}/close
 *   POST /api/takeaway/slots/day/{date}/time/{time}/open
 *   POST /api/takeaway/slots/day/{date}/time/{time}/manual?products=N
 *   POST /api/takeaway/slots/day/{date}/time/{time}/reset-manual
 *
 *  Pubblico (cart cliente):
 *   GET  /api/public/takeaway/slots/{localname}/day/{date}  → solo slot AVAILABLE
 */
@RestController
@CrossOrigin(origins = "*")
public class TakeawaySlotController {

    @Autowired private TakeawaySlotService service;
    @Autowired private AuthenticatedUserProvider auth;
    @Autowired private UserRepository userRepository;

    // ── Admin ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/takeaway/slots/config")
    public ResponseEntity<SlotConfigDto> getConfig() {
        return ResponseEntity.ok(service.getConfig(auth.getAgencyId()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/api/takeaway/slots/config")
    public ResponseEntity<SlotConfigDto> saveConfig(@RequestBody SlotConfigDto body) {
        return ResponseEntity.ok(service.saveConfig(auth.getAgencyId(), body));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/takeaway/slots/day/{date}")
    public ResponseEntity<List<SlotDto>> getDay(@PathVariable("date") String date) {
        LocalDate d = parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(service.getSlotsForDay(auth.getAgencyId(), d));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/slots/day/{date}/time/{time}/close")
    public ResponseEntity<Void> close(@PathVariable("date") String date, @PathVariable("time") String time) {
        LocalDate d = parseDate(date); LocalTime t = parseTime(time);
        if (d == null || t == null) return ResponseEntity.badRequest().build();
        service.closeSlot(auth.getAgencyId(), d, t);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/slots/day/{date}/time/{time}/open")
    public ResponseEntity<Void> open(@PathVariable("date") String date, @PathVariable("time") String time) {
        LocalDate d = parseDate(date); LocalTime t = parseTime(time);
        if (d == null || t == null) return ResponseEntity.badRequest().build();
        service.openSlot(auth.getAgencyId(), d, t);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/slots/day/{date}/time/{time}/manual")
    public ResponseEntity<Void> addManual(
            @PathVariable("date") String date,
            @PathVariable("time") String time,
            @RequestParam(value = "products", defaultValue = "1") int products) {
        LocalDate d = parseDate(date); LocalTime t = parseTime(time);
        if (d == null || t == null) return ResponseEntity.badRequest().build();
        service.addManualOrder(auth.getAgencyId(), d, t, products);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/slots/day/{date}/time/{time}/reset-manual")
    public ResponseEntity<Void> resetManual(@PathVariable("date") String date, @PathVariable("time") String time) {
        LocalDate d = parseDate(date); LocalTime t = parseTime(time);
        if (d == null || t == null) return ResponseEntity.badRequest().build();
        service.resetManualOrders(auth.getAgencyId(), d, t);
        return ResponseEntity.ok().build();
    }

    // ── Pubblico (cart cliente) ──────────────────────────────────────────────

    @GetMapping("/api/public/takeaway/slots/{localname}/day/{date}")
    public ResponseEntity<List<SlotDto>> getAvailableForDay(
            @PathVariable("localname") String localname,
            @PathVariable("date") String date) {
        LocalDate d = parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(u -> ResponseEntity.ok(service.getAvailableSlotsForDay(u.getIdAgency(), d)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LocalDate parseDate(String s) {
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }
    private LocalTime parseTime(String s) {
        try { return LocalTime.parse(s); } catch (DateTimeParseException e) { return null; }
    }
}
