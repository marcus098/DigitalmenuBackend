package com.modules.mainapp.controller;

import com.modules.cardmodule.requests.AddCard;
import com.modules.cardmodule.service.CardService;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.DataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@CrossOrigin
@RequestMapping("/api/cards")
public class CardController {
    @Autowired
    private CardService cardService;

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/info/{code}")
    public ResponseEntity<?> getCardInfo(@PathVariable("code") String code) {
        return ResponseEntity.ok(new DataResponse<>(cardService.getCardInfo(code.toUpperCase())));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/getall")
    public ResponseEntity<?> getCardsyIdAgency() {
        return ResponseEntity.ok(new DataResponse<>(cardService.getAllCards()));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add")
    public ResponseEntity<?> addCard(@RequestBody AddCard addCard) {
        try {
            return ResponseEntity.ok(new DataResponse<>(cardService.addCard(addCard)));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore addCard: ", e);
            return ResponseEntity.badRequest().body("Error");
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/addPoints/{id}/{quantity}")
    public ResponseEntity<?> addPoints(@PathVariable("id") long id, @PathVariable("quantity") int quantity) {
        if(quantity <= 0) return ResponseEntity.badRequest().body("Errore quantita");
        return ResponseEntity.ok(new DataResponse<>(cardService.addPoints(id, quantity)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/send/{id}/{email}")
    public ResponseEntity<?> sendCardByEmail(@PathVariable("id") long id, @PathVariable("email") String email) {
        return ResponseEntity.ok(new DataResponse<>(cardService.sendCardByEmail(email, id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/delete/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable("id") long id) {
        return ResponseEntity.ok(new DataResponse<>(cardService.deleteCard(id)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/reset/{id}")
    public ResponseEntity<?> resetPoints(@PathVariable("id") long id){
        return ResponseEntity.ok(new DataResponse<>(cardService.resetPoints(id)));
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WAITER')")
    @GetMapping("/claim/{id}/{quantity}")
    public ResponseEntity<?> claim(@PathVariable("id") long id, @PathVariable("quantity") int quantity) {
        if(quantity <= 0) return ResponseEntity.badRequest().body("Errore quantita");
        return ResponseEntity.ok(new DataResponse<>(cardService.claimPoints(id, quantity)));
    }

}
