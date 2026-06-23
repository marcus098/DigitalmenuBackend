package com.modules.mainapp.controller;

import com.modules.ordermodule.service.TableSessionService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/tables/{tableId}/session")
@RestController
public class TableSessionWaiterController {

    @Autowired
    private TableSessionService tableSessionService;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PostMapping("/open")
    public ResponseEntity<?> open(@PathVariable long tableId, @RequestBody OpenReq req) {
        long idAgency = authUserProvider.getAgencyId();
        int seats = req == null ? 0 : req.getSeats();
        Map<String, Object> resp = tableSessionService.openSession(tableId, idAgency, seats);
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PatchMapping("/seats")
    public ResponseEntity<?> seats(@PathVariable long tableId, @RequestBody OpenReq req) {
        long idAgency = authUserProvider.getAgencyId();
        int seats = req == null ? 0 : req.getSeats();
        Map<String, Object> resp = tableSessionService.updateSeats(tableId, idAgency, seats);
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PostMapping("/close")
    public ResponseEntity<?> close(@PathVariable long tableId) {
        long idAgency = authUserProvider.getAgencyId();
        Map<String, Object> resp = tableSessionService.closeSession(tableId, idAgency);
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @PostMapping("/force-submit")
    public ResponseEntity<?> forceSubmit(@PathVariable long tableId) {
        long idAgency = authUserProvider.getAgencyId();
        if (!tableSessionService.isAgencyOwner(tableId, idAgency)) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        }
        Map<String, Object> resp = tableSessionService.forceSubmit(tableId);
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    @GetMapping("")
    public ResponseEntity<?> getState(@PathVariable long tableId) {
        long idAgency = authUserProvider.getAgencyId();
        if (!tableSessionService.isAgencyOwner(tableId, idAgency)) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        }
        Map<String, Object> resp = tableSessionService.getWaiterStateByTableId(tableId);
        if (resp == null) return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
        return ResponseEntity.ok(resp);
    }

    public static class OpenReq {
        private int seats;
        public int getSeats() { return seats; }
        public void setSeats(int seats) { this.seats = seats; }
    }
}
