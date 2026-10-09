package com.modules.printmodule.controller;

import com.modules.printmodule.dto.BridgeDtos;
import com.modules.printmodule.model.PrintJobDoc;
import com.modules.printmodule.model.PrinterDoc;
import com.modules.printmodule.model.PrinterType;
import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.repository.PrinterRepository;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.PrinterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Optional;

/**
 * API pull per il bridge ESC/POS (print-bridge/bridge.js sul PC cassa). Autenticato dal deviceToken nel path
 * (endpoint permitAll in SecurityConf).
 *
 *   GET  /api/printers/bridge/{deviceToken}/next              → 200 {jobId, kind, comandId, escposBase64} | 204
 *   POST /api/printers/bridge/{deviceToken}/jobs/{jobId}/ack  body {ok, error?} → 200
 */
@RestController
@CrossOrigin(origins = "*")
public class PrintBridgeController {

    private final PrinterService printerService;
    private final PrintJobService printJobService;
    private final PrinterRepository printerRepository;

    public PrintBridgeController(PrinterService printerService, PrintJobService printJobService, PrinterRepository printerRepository) {
        this.printerService = printerService;
        this.printJobService = printJobService;
        this.printerRepository = printerRepository;
    }

    @GetMapping(value = "/api/printers/bridge/{token}/next", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BridgeDtos.BridgeJob> next(@PathVariable("token") String token) {
        Optional<PrinterDoc> opt = printerService.findByDeviceToken(token, PrinterType.ESCPOS_BRIDGE);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PrinterDoc printer = opt.get();
        printerRepository.touchIfStale(printer, null, "bridge online");
        if (!printer.isEnabled()) return ResponseEntity.noContent().build();

        Optional<PrintJobDoc> job = printJobService.claim(printer, null);
        if (job.isEmpty()) return ResponseEntity.noContent().build();
        PrintJobDoc j = job.get();
        String b64 = Base64.getEncoder().encodeToString(PrinterEncoder.escPos(j.getContent(), j.getCopies()));
        return ResponseEntity.ok(new BridgeDtos.BridgeJob(j.getId(), String.valueOf(j.getKind()), j.getComandId(), b64));
    }

    @PostMapping("/api/printers/bridge/{token}/jobs/{jobId}/ack")
    public ResponseEntity<Void> ack(@PathVariable("token") String token,
                                    @PathVariable("jobId") String jobId,
                                    @RequestBody(required = false) BridgeDtos.BridgeAck body) {
        Optional<PrinterDoc> opt = printerService.findByDeviceToken(token, PrinterType.ESCPOS_BRIDGE);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        boolean ok = body != null && body.ok();
        Optional<PrintJobDoc> job = printJobService.complete(opt.get(), jobId, ok,
                ok ? null : (body != null && body.error() != null ? body.error() : "Errore bridge"));
        return job.isPresent() ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
