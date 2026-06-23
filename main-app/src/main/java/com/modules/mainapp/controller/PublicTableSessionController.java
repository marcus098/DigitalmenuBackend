package com.modules.mainapp.controller;

import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.service.TableSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/public/sessions")
@RestController
public class PublicTableSessionController {

    @Autowired
    private TableSessionService tableSessionService;

    @GetMapping("/table/{tableId}")
    public ResponseEntity<?> getTable(
            @PathVariable long tableId,
            @RequestParam("localname") String localname) {
        if (tableId <= 0 || localname == null || localname.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_PAYLOAD"));
        }
        Map<String, Object> info = tableSessionService.getPublicTableInfo(tableId, localname);
        if (info == null) return ResponseEntity.status(404).body(Map.of("error", "TABLE_NOT_FOUND"));
        return ResponseEntity.ok(info);
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinReq req) {
        if (req == null || req.getTableId() <= 0 || req.getClientSessionId() == null
                || req.getClientSessionId().isBlank() || req.getCode() == null) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_PAYLOAD"));
        }
        Map<String, Object> resp = tableSessionService.join(req.getTableId(), req.getCode(),
                req.getLocalname(), req.getClientSessionId());
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping("/{sessionId}/state")
    public ResponseEntity<?> state(
            @PathVariable String sessionId,
            @RequestParam("clientSessionId") String clientSessionId) {
        Map<String, Object> resp = tableSessionService.getState(sessionId, clientSessionId);
        if (resp == null) return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{sessionId}/ready")
    public ResponseEntity<?> ready(@PathVariable String sessionId, @RequestBody ReadyReq req) {
        if (req == null || req.getClientSessionId() == null || req.getClientSessionId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_PAYLOAD"));
        }
        Map<String, Object> resp = tableSessionService.setReady(sessionId, req.getClientSessionId(), req.getDraftOrder());
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PostMapping("/{sessionId}/not-ready")
    public ResponseEntity<?> notReady(@PathVariable String sessionId, @RequestBody NotReadyReq req) {
        if (req == null || req.getClientSessionId() == null || req.getClientSessionId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_PAYLOAD"));
        }
        Map<String, Object> resp = tableSessionService.setNotReady(sessionId, req.getClientSessionId());
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<?> submit(@PathVariable String sessionId, @RequestBody NotReadyReq req) {
        String cs = req == null ? null : req.getClientSessionId();
        if (cs == null || cs.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_PAYLOAD"));
        }
        Map<String, Object> resp = tableSessionService.submit(sessionId, cs);
        int status = TableSessionService.extractStatus(resp);
        return ResponseEntity.status(status).body(resp);
    }

    // ----------- request DTOs -----------

    public static class JoinReq {
        private long tableId;
        private String code;
        private String localname;
        private String clientSessionId;
        public long getTableId() { return tableId; }
        public void setTableId(long tableId) { this.tableId = tableId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLocalname() { return localname; }
        public void setLocalname(String localname) { this.localname = localname; }
        public String getClientSessionId() { return clientSessionId; }
        public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
    }

    public static class ReadyReq {
        private String clientSessionId;
        private List<AddComandOrder> draftOrder;
        public String getClientSessionId() { return clientSessionId; }
        public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
        public List<AddComandOrder> getDraftOrder() { return draftOrder; }
        public void setDraftOrder(List<AddComandOrder> draftOrder) { this.draftOrder = draftOrder; }
    }

    public static class NotReadyReq {
        private String clientSessionId;
        public String getClientSessionId() { return clientSessionId; }
        public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
    }
}
