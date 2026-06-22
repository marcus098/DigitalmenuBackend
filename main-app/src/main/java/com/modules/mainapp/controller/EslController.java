package com.modules.mainapp.controller;

import com.modules.mainapp.esl.EslConfigJpa;
import com.modules.mainapp.esl.EslService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/esl")
@RestController
public class EslController {

    @Autowired
    private EslService eslService;

    /** Get all ESL configurations for this agency's tables */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/configs")
    public ResponseEntity<List<EslConfigJpa>> getConfigs() {
        return ResponseEntity.ok(eslService.getConfigsForAgency());
    }

    /**
     * Create or update ESL config for a table.
     * Body: { "tableId": 1, "eslTagMac": "AA:BB:CC:DD:EE:FF", "eslApUrl": "http://192.168.1.50" }
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/configs")
    public ResponseEntity<EslConfigJpa> saveConfig(@RequestBody Map<String, Object> body) {
        Long tableId  = Long.valueOf(body.get("tableId").toString());
        String mac    = (String) body.get("eslTagMac");
        String apUrl  = (String) body.getOrDefault("eslApUrl", "");
        return ResponseEntity.ok(eslService.saveConfig(tableId, mac, apUrl));
    }

    /** Delete ESL config for a table */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/configs/{tableId}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long tableId) {
        eslService.deleteConfig(tableId);
        return ResponseEntity.ok().build();
    }

    /** Manually trigger a QR push to the e-ink tag (useful after initial setup) */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/push/{tableId}")
    public ResponseEntity<Map<String, Object>> pushTag(@PathVariable Long tableId) {
        boolean ok = eslService.manualPush(tableId);
        return ResponseEntity.status(ok ? 200 : 500).body(Map.of("success", ok));
    }
}
