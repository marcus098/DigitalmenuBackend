package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/reservations")
@RestController
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    public ResponseEntity<?> getAll() {
        List<ReservationJpa> list = reservationService.getAllForAgency();
        return ResponseEntity.ok(new DataResponse<>(list));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    public ResponseEntity<?> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(new DataResponse<>(reservationService.getForAgencyBetween(from, to)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    public ResponseEntity<?> updateStatus(@PathVariable long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body("Missing status");
        return reservationService.updateStatus(id, status)
                .map(r -> ResponseEntity.ok(new DataResponse<>(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable long id) {
        boolean deleted = reservationService.delete(id);
        return deleted ? ResponseEntity.ok(new DataResponse<>(true)) : ResponseEntity.notFound().build();
    }
}
