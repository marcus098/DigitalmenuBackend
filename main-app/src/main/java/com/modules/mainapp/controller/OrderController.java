package com.modules.mainapp.controller;

import com.modules.common.dto.UserDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.ChangeStatus;
import com.modules.ordermodule.request.CheckoutRequest;
import com.modules.ordermodule.service.CheckoutService;
import com.modules.ordermodule.service.OrderComandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.modules.ordermodule.exception.OrderRejectedException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(origins = "*")
@RequestMapping("/api/orders")
@RestController
public class OrderController {
    @Autowired
    private OrderComandService orderComandService;
    @Autowired
    private CheckoutService checkoutService;

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/insertWaiter")
    public ResponseEntity<?> addOrderWaiterTable(@RequestBody AddComandWaiter addComandWaiter) {
        if (!addComandWaiter.validate()){
            return ResponseEntity.status(400).body("Payload error");
        }
        String newId = orderComandService.addOrderWaiter(addComandWaiter);
        return ResponseEntity.status(newId == null ? 400 : 200).body(newId);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/changeStatus")
    public ResponseEntity<?> changeStatus(@RequestBody ChangeStatus changeStatus) {
        if(!changeStatus.validate()){
            return ResponseEntity.status(400).body("invalid payload");
        }
        try {
            CompletableFuture<Integer> statusCompletable = orderComandService.changeStatusToComand(changeStatus.getComandId(), changeStatus.getStatus());
            int status = statusCompletable.get();
            return ResponseEntity.status(status).body(status == 200 ? "Order changed successfully" : "Order update failed");
        } catch (Exception e) {
            ErrorLog.logger.error("Errore ", e);
            return ResponseEntity.status(500).body("Errore");
        }
    }

    /** Chiusura conto in cassa: COMPLETED + importo incassato/sconto salvati sulla comanda + movimenti tessera. */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/{id}/checkout")
    public ResponseEntity<?> checkout(@PathVariable("id") String id, @RequestBody CheckoutRequest request) {
        String error = request.validate();
        if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));
        return ResponseEntity.ok(checkoutService.checkout(id, request));
    }

    /** Comande servite ma non ancora incassate: restano in cassa finché non si chiude il conto. */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/to-checkout")
    public ResponseEntity<?> toCheckout() {
        return ResponseEntity.ok(checkoutService.toCheckout());
    }

    /** Incassi di cassa per giorno (date yyyy-MM-dd, estremi inclusi, massimo un anno). */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/checkout-summary")
    public ResponseEntity<?> checkoutSummary(@RequestParam("from") String from, @RequestParam("to") String to) {
        LocalDate f, t;
        try {
            f = LocalDate.parse(from);
            t = LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Date non valide"));
        }
        if (t.isBefore(f) || f.plusYears(1).isBefore(t)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Intervallo non valido"));
        }
        return ResponseEntity.ok(checkoutService.summary(f, t));
    }

    /** Accetta un ordine "su richiesta" (AWAIT_APPROVAL → PENDING): incassa l'eventuale autorizzazione e stampa. */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") String id) {
        try {
            orderComandService.approve(id);
            return ResponseEntity.ok(Map.of("message", "Ordine accettato"));
        } catch (OrderRejectedException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore approvazione comanda " + id, e);
            return ResponseEntity.status(500).body(Map.of("message", "Errore"));
        }
    }

    /** Rifiuta un ordine "su richiesta" (→ DELETED, motivo visibile al cliente, autorizzazione annullata). */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable("id") String id, @RequestBody(required = false) Map<String, String> body) {
        try {
            orderComandService.reject(id, body != null ? body.get("reason") : null);
            return ResponseEntity.ok(Map.of("message", "Ordine rifiutato"));
        } catch (OrderRejectedException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore rifiuto comanda " + id, e);
            return ResponseEntity.status(500).body(Map.of("message", "Errore"));
        }
    }
}