package com.modules.mainapp.controller;

import com.modules.common.dto.TableDto;
import com.modules.common.responses.DataResponse;
import com.modules.mainapp.esl.EslService;
import com.modules.ordermodule.service.TableSessionService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.tablemodule.requests.AddTable;
import com.modules.tablemodule.requests.UpdateTables;
import com.modules.tablemodule.service.TableService;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/tables")
@RestController
public class TableController {
    @Autowired
    private TableService tableService;

    @Autowired
    private EslService eslService;

    @Autowired
    private TableSessionService tableSessionService;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add")
    public ResponseEntity<DataResponse<TableDto>> addTable(@RequestBody AddTable addTable) {
        TableDto tableDto = tableService.addTable(addTable);
        return ResponseEntity.status(tableDto == null ? 400 : 200).body(new DataResponse<TableDto>(null, tableDto));
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/delete/{idTable}")
    public ResponseEntity<DataResponse<String>> deleteTable(@PathVariable("idTable") @Min(1) long idTable) {
        int status = tableService.deleteTable(idTable);
        return ResponseEntity.status(status).body(new DataResponse<String>(null, status == 200 ? "Success" : "Error"));
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/deleteAndClose/{idTable}")
    public ResponseEntity<DataResponse<String>> deleteAndCloseTable(@PathVariable("idTable") @Min(1) long idTable) {
        int status = tableService.deleteTableAndCloseComands(idTable);
        return ResponseEntity.status(status).body(new DataResponse<String>(null, status == 200 ? "Success" : "Error"));
    }


    // da chiamare quando i clienti lasciano il tavolo. Rigenera il nuovo codice per accedere al tavolo
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/free/{idTable}")
    public ResponseEntity<DataResponse<String>> freeTable(@PathVariable("idTable") @Min(1) long idTable) {
        String sessionId = tableSessionService.currentSessionId(idTable, authUserProvider.getAgencyId());
        String statusMessage = tableService.setBusy(idTable, false, 1);
        closeSession(sessionId, statusMessage);
        int status = 400;
        if (statusMessage.equals("404")) {
            status = 404;
        } else if (statusMessage.equals("comands")) {
            status = 402;
        } else if (statusMessage.equals("400")) {
            status = 400;
        }else{
            status = 200;
        }

        return ResponseEntity.status(status).body(new DataResponse<String>(null, statusMessage));
    }


    // da chiamare quando i clienti lasciano il tavolo. Rigenera il nuovo codice per accedere al tavolo e chiude le comande aperte
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/freeAndClose/{idTable}")
    public ResponseEntity<DataResponse<String>> freeAndCloseTable(@PathVariable("idTable") @Min(1) long idTable) {
        String sessionId = tableSessionService.currentSessionId(idTable, authUserProvider.getAgencyId());
        String statusMessage = tableService.setBusyAndCloseComands(idTable, false, 1);
        closeSession(sessionId, statusMessage);
        return ResponseEntity.status(statusMessage == null ? 400 : 200).body(new DataResponse<String>(null, statusMessage));
    }


    // da chiamare quando i clienti occupano un tavolo. Serve il numero dei posti.
    // Passa dalla stessa apertura sessione di POST /{id}/session/open: senza sessione Mongo i clienti
    // resterebbero bloccati sulla pagina del codice di accesso. Restituisce il codice di accesso (4 caratteri).
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/takes/{idTable}/{seats}")
    public ResponseEntity<DataResponse<String>> takesTable(@PathVariable("idTable") @Min(1) long idTable, @PathVariable("seats") @Min(1) int seats) {
        Map<String, Object> resp = tableSessionService.openSession(idTable, authUserProvider.getAgencyId(), seats);
        int status = TableSessionService.extractStatus(resp);
        if (status != 200) {
            return ResponseEntity.status(status).body(new DataResponse<String>(null, String.valueOf(resp.get("error"))));
        }
        eslService.updateTableTag(idTable);
        return ResponseEntity.ok(new DataResponse<String>(null, String.valueOf(resp.get("accessCode"))));
    }

    /** Dopo una liberazione riuscita chiude anche la sessione al tavolo (Mongo), così i clienti vengono avvisati via SSE. */
    private void closeSession(String sessionId, String freeResult) {
        if (sessionId == null || freeResult == null || freeResult.equals("404") || freeResult.equals("400") || freeResult.equals("comands")) return;
        tableSessionService.closeSessionById(sessionId, authUserProvider.getAgencyId());
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/updateTablesPosition")
    public ResponseEntity<DataResponse<List<TableDto>>> updateTablesPosition(@RequestBody UpdateTables updateTable) {
        List<TableDto> tables = tableService.updateTablesPosition(updateTable);
        return ResponseEntity.status(tables == null ? 400 : 200).body(new DataResponse<List<TableDto>>(null, tables));
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/updateTable")
    public ResponseEntity<DataResponse<TableDto>> updateTable(@RequestBody TableDto tableDto) {
        TableDto table = tableService.updateTable(tableDto);
        return ResponseEntity.status(table == null ? 400 : 200).body(new DataResponse<TableDto>(null, table));
    }


}
