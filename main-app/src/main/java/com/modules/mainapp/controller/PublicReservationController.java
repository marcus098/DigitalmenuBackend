package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.mainapp.reservation.dto.CreateReservationRequest;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RequestMapping("/api/public/reservations")
@RestController
public class PublicReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/{localname}")
    public ResponseEntity<?> createReservation(
            @PathVariable String localname,
            @RequestBody CreateReservationRequest request) {

        if (request.getCustomerName() == null || request.getCustomerName().isBlank()
                || request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()
                || request.getReservationDate() == null
                || request.getReservationTime() == null
                || request.getPartySize() <= 0) {
            return ResponseEntity.badRequest().body("Dati incompleti");
        }

        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(user -> {
                    ReservationJpa saved = reservationService.createPublicReservation(user.getIdAgency(), request);
                    return ResponseEntity.ok().body(saved.getId());
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
