package com.modules.printmodule.controller;

import com.modules.printmodule.model.PrinterType;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.PrinterService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * Stampa dal tablet Android tramite l'app RawBT (stampanti TABLET_RAWBT). Il server produce solo i byte ESC/POS:
 * è il browser del tablet a passarli a RawBT con un intent "rawbt:base64,...", su gesto dell'utente.
 *
 *   GET /api/printers/tablet/status                         → {enabled, queuedPrinters} (ADMIN, WAITER)
 *   GET /api/printers/tablet/ticket/{comandId}?reprint=false → {escposBase64, tickets} | 204 nulla da stampare
 *                                                              | 404 comanda di altra agency | 409 DELETED/AWAIT_PAYMENT
 *   GET /api/printers/{id}/tablet-test                      → {escposBase64} scontrino di prova (ADMIN)
 */
@RestController
@CrossOrigin(origins = "*")
public class TabletPrintController {

    private final PrinterService printerService;
    private final PrintJobService printJobService;
    private final AuthenticatedUserProvider auth;

    public TabletPrintController(PrinterService printerService, PrintJobService printJobService, AuthenticatedUserProvider auth) {
        this.printerService = printerService;
        this.printJobService = printJobService;
        this.auth = auth;
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/api/printers/tablet/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        long idAgency = auth.getAgencyId();
        return ResponseEntity.ok(Map.of(
                "enabled", !printJobService.tabletPrinters(idAgency).isEmpty(),
                "queuedPrinters", printJobService.hasQueuedPrinters(idAgency)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/api/printers/tablet/ticket/{comandId}")
    public ResponseEntity<Map<String, Object>> ticket(@PathVariable("comandId") String comandId,
                                                      @RequestParam(value = "reprint", defaultValue = "false") boolean reprint) {
        PrintJobService.TabletTicket t = printJobService.tabletTicket(comandId, auth.getAgencyId(), reprint);
        return switch (t.outcome()) {
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case NO_CONTENT -> ResponseEntity.noContent().build();
            case OK -> ResponseEntity.ok(Map.of(
                    "escposBase64", Base64.getEncoder().encodeToString(t.escPos()),
                    "tickets", t.tickets()));
        };
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/api/printers/{id}/tablet-test")
    public ResponseEntity<Map<String, String>> tabletTest(@PathVariable("id") String id) {
        return printerService.find(auth.getAgencyId(), id)
                .map(p -> p.getType() != PrinterType.TABLET_RAWBT
                        ? ResponseEntity.status(HttpStatus.CONFLICT).<Map<String, String>>build()
                        : ResponseEntity.ok(Map.of("escposBase64", Base64.getEncoder().encodeToString(printJobService.tabletTest(p)))))
                .orElse(ResponseEntity.notFound().build());
    }
}
