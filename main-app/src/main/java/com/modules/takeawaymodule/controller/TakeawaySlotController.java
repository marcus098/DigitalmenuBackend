package com.modules.takeawaymodule.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.service.PrepaymentPolicy;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.dto.ClosureDto;
import com.modules.takeawaymodule.dto.PauseStatusDto;
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
import java.util.Map;

/**
 * Endpoint per la gestione degli slot asporto.
 *
 *  Dashboard (auth required, ADMIN):
 *   GET  /api/takeaway/slots/config         → config settimanale + giorni chiusi + riserva
 *   PUT  /api/takeaway/slots/config         → salva config
 *   GET  /api/takeaway/slots/day/{date}     → slot del giorno (admin view: include closed/past/full)
 *   POST /api/takeaway/slots/day/{date}/close | /open          → chiude/riapre l'intera giornata
 *   POST /api/takeaway/slots/day/{date}/time/{time}/close | /open
 *   POST /api/takeaway/slots/day/{date}/time/{time}/manual?products=N
 *   POST /api/takeaway/slots/day/{date}/time/{time}/reset-manual
 *   GET  /api/takeaway/closures              → chiusure (giorni/periodi) non terminate
 *   POST /api/takeaway/closures {from,to,note} → nuova chiusura (es. "Ferie")
 *   DELETE /api/takeaway/closures/{id}
 *  Dashboard (ADMIN o WAITER):
 *   GET  /api/takeaway/pause                 → stato "Sospendi asporto"
 *   POST /api/takeaway/pause {minutes?}      → sospende (minutes assente = fino a ripresa manuale)
 *   POST /api/takeaway/resume
 *
 *  Pubblico (cart cliente):
 *   GET  /api/public/takeaway/slots/{localname}/day/{date}  → slot AVAILABLE e ON_REQUEST ("su richiesta")
 *   GET  /api/public/takeaway/status/{localname}            → {paused, pausedUntil}
 */
@RestController
@CrossOrigin(origins = "*")
public class TakeawaySlotController {

    @Autowired private TakeawaySlotService service;
    @Autowired private AuthenticatedUserProvider auth;
    @Autowired private UserRepository userRepository;
    @Autowired private PrepaymentPolicy prepaymentPolicy;

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
    @PostMapping("/api/takeaway/slots/day/{date}/close")
    public ResponseEntity<?> closeDay(@PathVariable("date") String date) {
        LocalDate d = parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        service.closeDay(auth.getAgencyId(), d);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/slots/day/{date}/open")
    public ResponseEntity<?> openDay(@PathVariable("date") String date) {
        LocalDate d = parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        try {
            service.openDay(auth.getAgencyId(), d);
            return ResponseEntity.ok().build();
        } catch (OrderRejectedException e) {
            return error(e);
        }
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

    // ── Chiusure ─────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/takeaway/closures")
    public ResponseEntity<List<ClosureDto>> listClosures() {
        return ResponseEntity.ok(service.listClosures(auth.getAgencyId()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/takeaway/closures")
    public ResponseEntity<?> addClosure(@RequestBody ClosureDto body) {
        LocalDate from = body != null ? parseDate(body.from()) : null;
        LocalDate to = body != null ? parseDate(body.to() != null ? body.to() : body.from()) : null;
        if (from == null || to == null) return ResponseEntity.badRequest().body(Map.of("message", "Date non valide"));
        try {
            return ResponseEntity.ok(service.addClosure(auth.getAgencyId(), from, to, body.note()));
        } catch (OrderRejectedException e) {
            return error(e);
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/api/takeaway/closures/{id}")
    public ResponseEntity<Void> deleteClosure(@PathVariable("id") long id) {
        return service.deleteClosure(auth.getAgencyId(), id)
                ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // ── Sospendi asporto ─────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @GetMapping("/api/takeaway/pause")
    public ResponseEntity<PauseStatusDto> pauseStatus() {
        return ResponseEntity.ok(service.getPauseStatus(auth.getAgencyId()));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PostMapping("/api/takeaway/pause")
    public ResponseEntity<PauseStatusDto> pause(@RequestBody(required = false) Map<String, Integer> body) {
        Integer minutes = body != null ? body.get("minutes") : null;
        return ResponseEntity.ok(service.pause(auth.getAgencyId(), minutes));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PostMapping("/api/takeaway/resume")
    public ResponseEntity<PauseStatusDto> resume() {
        return ResponseEntity.ok(service.resume(auth.getAgencyId()));
    }

    // ── Pubblico (cart cliente) ──────────────────────────────────────────────

    @GetMapping("/api/public/takeaway/slots/{localname}/day/{date}")
    public ResponseEntity<List<SlotDto>> getAvailableForDay(
            @PathVariable("localname") String localname,
            @PathVariable("date") String date) {
        LocalDate d = parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(u -> ResponseEntity.ok(service.getAvailableSlotsForDay(u.getIdAgency(), d,
                        prepaymentPolicy.requiresPrepayment(u.getIdAgency(), PrepaymentPolicy.Channel.TAKEAWAY))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/public/takeaway/status/{localname}")
    public ResponseEntity<PauseStatusDto> publicStatus(@PathVariable("localname") String localname) {
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(u -> ResponseEntity.ok(service.getPauseStatus(u.getIdAgency())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ResponseEntity<?> error(OrderRejectedException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private LocalDate parseDate(String s) {
        if (s == null) return null;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }
    private LocalTime parseTime(String s) {
        try { return LocalTime.parse(s); } catch (DateTimeParseException e) { return null; }
    }
}
