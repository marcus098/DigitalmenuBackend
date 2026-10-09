package com.modules.printmodule.controller;

import com.modules.printmodule.dto.PrintJobDto;
import com.modules.printmodule.dto.PrinterDto;
import com.modules.printmodule.dto.PrinterRequest;
import com.modules.printmodule.model.PrintJobDoc;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.PrinterService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestione stampanti comande dalla dashboard (ADMIN, scope = agency del JWT; id di altre agency → 404).
 *
 *   GET    /api/printers                         → lista
 *   POST   /api/printers                         → crea (risposta con deviceToken + setupUrl, mostrati una volta)
 *   PUT    /api/printers/{id}                    → modifica
 *   DELETE /api/printers/{id}                    → elimina (e i suoi job)
 *   POST   /api/printers/{id}/regenerate-token   → nuovo deviceToken (il vecchio smette di funzionare)
 *   POST   /api/printers/{id}/test               → accoda scontrino di prova
 *   GET    /api/printers/{id}/jobs?limit=20      → ultimi job con stato
 *   POST   /api/printers/reprint/{comandId}?printerId=  → ristampa (default: tutte le stampanti pertinenti)
 */
@RestController
@CrossOrigin(origins = "*")
public class PrinterAdminController {

    private final PrinterService printerService;
    private final PrintJobService printJobService;
    private final AuthenticatedUserProvider auth;

    public PrinterAdminController(PrinterService printerService, PrintJobService printJobService, AuthenticatedUserProvider auth) {
        this.printerService = printerService;
        this.printJobService = printJobService;
        this.auth = auth;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/printers")
    public ResponseEntity<List<PrinterDto>> list() {
        return ResponseEntity.ok(printerService.list(auth.getAgencyId()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/printers")
    public ResponseEntity<PrinterDto> create(@RequestBody PrinterRequest body) {
        try {
            return ResponseEntity.ok(printerService.create(auth.getAgencyId(), body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/api/printers/{id}")
    public ResponseEntity<PrinterDto> update(@PathVariable("id") String id, @RequestBody PrinterRequest body) {
        try {
            return printerService.update(auth.getAgencyId(), id, body)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/api/printers/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        return printerService.delete(auth.getAgencyId(), id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/printers/{id}/regenerate-token")
    public ResponseEntity<PrinterDto> regenerateToken(@PathVariable("id") String id) {
        return printerService.regenerateToken(auth.getAgencyId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/printers/{id}/test")
    public ResponseEntity<PrintJobDto> test(@PathVariable("id") String id) {
        return printerService.find(auth.getAgencyId(), id)
                .map(p -> {
                    PrintJobDoc job = printJobService.enqueueTest(p);
                    return ResponseEntity.ok(PrintJobService.toDto(job, p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/printers/{id}/jobs")
    public ResponseEntity<List<PrintJobDto>> jobs(@PathVariable("id") String id,
                                                  @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return printerService.find(auth.getAgencyId(), id)
                .map(p -> ResponseEntity.ok(printJobService.recentJobs(p, limit)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/api/printers/reprint/{comandId}")
    public ResponseEntity<Map<String, Integer>> reprint(@PathVariable("comandId") String comandId,
                                                        @RequestParam(value = "printerId", required = false) String printerId) {
        return printJobService.reprint(comandId, auth.getAgencyId(), printerId)
                .map(n -> ResponseEntity.ok(Map.of("enqueued", n)))
                .orElse(ResponseEntity.notFound().build());
    }
}
