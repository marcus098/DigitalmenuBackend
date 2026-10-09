package com.modules.printmodule.controller;

import com.modules.printmodule.model.PrintJobDoc;
import com.modules.printmodule.model.PrinterDoc;
import com.modules.printmodule.model.PrinterType;
import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.repository.PrinterRepository;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.PrinterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Protocollo Star CloudPRNT. URL da configurare nella stampante (web config → CloudPRNT → Server URL):
 *   {app.public-base-url}/api/printers/cloudprnt/{deviceToken}
 * Il token nel path autentica la stampante (endpoint permitAll in SecurityConf); il MAC viene verificato
 * se configurato, altrimenti appreso al primo contatto.
 *
 *   POST   → poll stato: risposta {"jobReady":bool, "mediaTypes":[...], "jobToken":..., "deleteMethod":"DELETE"}
 *   GET    ?mac=&type=&token=      → contenuto del job (job → SENT, attempts++)
 *   DELETE ?mac=&code=&token=      → conferma: code 2xx → PRINTED, altro → retry (FAILED dopo 5 tentativi)
 */
@RestController
@CrossOrigin(origins = "*")
public class CloudPrntController {

    static final String STARPRNT = "application/vnd.star.starprnt";
    static final String TEXT = "text/plain";
    static final List<String> MEDIA_TYPES = List.of(STARPRNT, TEXT);

    private final PrinterService printerService;
    private final PrintJobService printJobService;
    private final PrinterRepository printerRepository;

    public CloudPrntController(PrinterService printerService, PrintJobService printJobService, PrinterRepository printerRepository) {
        this.printerService = printerService;
        this.printJobService = printJobService;
        this.printerRepository = printerRepository;
    }

    @PostMapping(value = "/api/printers/cloudprnt/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> poll(@PathVariable("token") String token,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Optional<PrinterDoc> opt = printerService.findByDeviceToken(token, PrinterType.STAR_CLOUDPRNT);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PrinterDoc printer = opt.get();

        String mac = body != null && body.get("printerMAC") != null ? String.valueOf(body.get("printerMAC")) : null;
        if (!macAllowed(printer, mac)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String learnMac = isBlank(printer.getMacAddress()) ? normalized(mac) : null;
        String status = body != null && body.get("statusCode") != null ? String.valueOf(body.get("statusCode")) : null;
        printerRepository.touchIfStale(printer, learnMac, status);

        Map<String, Object> resp = new LinkedHashMap<>();
        Optional<PrintJobDoc> next = printer.isEnabled() ? printJobService.peekNext(printer) : Optional.empty();
        if (next.isPresent()) {
            resp.put("jobReady", true);
            resp.put("mediaTypes", MEDIA_TYPES);
            resp.put("jobToken", next.get().getId());
            resp.put("deleteMethod", "DELETE");
        } else {
            resp.put("jobReady", false);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/printers/cloudprnt/{token}")
    public ResponseEntity<byte[]> fetch(@PathVariable("token") String token,
                                        @RequestParam(value = "mac", required = false) String mac,
                                        @RequestParam(value = "type", required = false) String type,
                                        @RequestParam(value = "token", required = false) String jobToken,
                                        @RequestParam(value = "delete", required = false) String delete,
                                        @RequestParam(value = "code", required = false) String code) {
        if (delete != null) { // deleteMethod=GET (firmware vecchi): GET ...&delete&code=
            ResponseEntity<Void> r = confirm(token, mac, code, jobToken);
            return ResponseEntity.status(r.getStatusCode()).build();
        }
        Optional<PrinterDoc> opt = printerService.findByDeviceToken(token, PrinterType.STAR_CLOUDPRNT);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PrinterDoc printer = opt.get();
        if (!macAllowed(printer, mac)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Optional<PrintJobDoc> job = printJobService.claim(printer, jobToken);
        if (job.isEmpty()) return ResponseEntity.notFound().build();

        PrintJobDoc j = job.get();
        boolean star = type != null && type.trim().equalsIgnoreCase(STARPRNT);
        byte[] bytes = star
                ? PrinterEncoder.starPrnt(j.getContent(), j.getCopies())
                : PrinterEncoder.plainText(j.getContent(), printer.charsPerLine(), j.getCopies());
        ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(MediaType.parseMediaType(star ? STARPRNT : TEXT));
        if (!star) b.header("X-Star-Cut", "partial; feed=true");
        return b.body(bytes);
    }

    @DeleteMapping("/api/printers/cloudprnt/{token}")
    public ResponseEntity<Void> confirm(@PathVariable("token") String token,
                                        @RequestParam(value = "mac", required = false) String mac,
                                        @RequestParam(value = "code", required = false) String code,
                                        @RequestParam(value = "token", required = false) String jobToken) {
        Optional<PrinterDoc> opt = printerService.findByDeviceToken(token, PrinterType.STAR_CLOUDPRNT);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PrinterDoc printer = opt.get();
        if (!macAllowed(printer, mac)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        boolean ok = code == null || code.trim().startsWith("2");
        printJobService.complete(printer, jobToken, ok, ok ? null : "CloudPRNT code " + code.trim());
        return ResponseEntity.ok().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Se la stampante ha un MAC configurato, la richiesta deve averlo uguale. */
    private static boolean macAllowed(PrinterDoc printer, String mac) {
        if (isBlank(printer.getMacAddress())) return true;
        String n = normalized(mac);
        return n != null && n.equals(printer.getMacAddress());
    }

    private static String normalized(String mac) {
        String n = PrinterService.normalizeMac(mac);
        return isBlank(n) ? null : n;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
