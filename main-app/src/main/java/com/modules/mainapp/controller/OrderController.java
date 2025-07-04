package com.modules.mainapp.controller;

import com.modules.common.dto.UserDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.ChangeStatus;
import com.modules.ordermodule.service.OrderComandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(origins = "*")
@RequestMapping("/api/orders")
@RestController
public class OrderController {
    @Autowired
    private OrderComandService orderComandService;

    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<?> handleDeserializationException(HttpMessageNotReadableException ex) {
            return ResponseEntity.badRequest().body("Errore nel parsing del JSON: " + ex.getMessage());
        }
    }

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
}