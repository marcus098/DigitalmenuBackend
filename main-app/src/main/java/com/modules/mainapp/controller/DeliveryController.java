package com.modules.mainapp.controller;

import com.modules.deliverymodule.dto.DeliverySettingsDto;
import com.modules.deliverymodule.service.DeliveryOrderService;
import com.modules.deliverymodule.service.DeliverySettingsService;
import com.modules.deliverymodule.service.DeliverySlotService;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Consegna a domicilio lato locale (vedi docs/DOMICILIO.md):
 * <pre>
 *  GET/PUT /api/delivery/settings              impostazioni (titolare)
 *  GET     /api/delivery/route-info            consegna attiva, calcolo percorso, partenza (rider, camerieri, titolare)
 *  GET     /api/delivery/slots/day/{date}      fasce del giorno con occupazione (titolare, camerieri)
 *  GET     /api/delivery/orders?date=          consegne del giorno (rider, camerieri, titolare)
 *  POST    /api/delivery/orders/{id}/stage     {stage, cashCollected?}
 * </pre>
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/delivery")
@RestController
public class DeliveryController {

    @Autowired private DeliverySettingsService settingsService;
    @Autowired private DeliverySlotService slotService;
    @Autowired private DeliveryOrderService orderService;
    @Autowired private AuthenticatedUserProvider auth;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(settingsService.get(auth.getAgencyId()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody DeliverySettingsDto body) {
        return handle(() -> ResponseEntity.ok(settingsService.save(auth.getAgencyId(), body)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER','ROLE_DELIVERY')")
    @GetMapping("/route-info")
    public ResponseEntity<?> routeInfo() {
        return ResponseEntity.ok(settingsService.routeInfo(auth.getAgencyId()));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER','ROLE_DELIVERY')")
    @GetMapping("/slots/day/{date}")
    public ResponseEntity<?> slots(@PathVariable String date) {
        LocalDate d = parse(date);
        if (d == null) return PublicDeliveryController.error(400, "Data non valida (usa yyyy-MM-dd)");
        return ResponseEntity.ok(slotService.slotsForDay(auth.getAgencyId(), d, false));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER','ROLE_DELIVERY')")
    @GetMapping("/orders")
    public ResponseEntity<?> orders(@RequestParam(value = "date", required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : parse(date);
        if (d == null) return PublicDeliveryController.error(400, "Data non valida (usa yyyy-MM-dd)");
        return ResponseEntity.ok(orderService.deliveriesOfDay(d));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER','ROLE_DELIVERY')")
    @PostMapping("/orders/{id}/stage")
    public ResponseEntity<?> stage(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object stage = body == null ? null : body.get("stage");
        Object cash = body == null ? null : body.get("cashCollected");
        Boolean cashCollected = cash instanceof Boolean b ? b : null;
        return handle(() -> ResponseEntity.ok(orderService.changeStage(id, stage == null ? null : stage.toString(), cashCollected)));
    }

    private static ResponseEntity<?> handle(Supplier<ResponseEntity<?>> fn) {
        try {
            return fn.get();
        } catch (OrderRejectedException e) {
            return PublicDeliveryController.error(e.getStatus(), e.getMessage());
        }
    }

    private static LocalDate parse(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
