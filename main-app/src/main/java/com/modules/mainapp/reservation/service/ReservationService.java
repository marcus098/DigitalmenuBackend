package com.modules.mainapp.reservation.service;

import com.modules.mainapp.reservation.dto.CreateReservationRequest;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.repository.ReservationRepository;
import com.modules.mainapp.service.TwilioService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @Autowired
    private TwilioService twilioService;

    @Value("${spring.application.name:RestaurantFlow}")
    private String appName;

    /** Called by the public (no-auth) endpoint: creates a pending reservation for the venue */
    public ReservationJpa createPublicReservation(long idAgency, CreateReservationRequest request) {
        ReservationJpa r = new ReservationJpa();
        r.setIdAgency(idAgency);
        r.setCustomerName(request.getCustomerName());
        r.setCustomerPhone(request.getCustomerPhone());
        r.setCustomerEmail(request.getCustomerEmail());
        r.setPartySize(request.getPartySize());
        r.setReservationDate(request.getReservationDate());
        r.setReservationTime(request.getReservationTime());
        r.setSpecialRequests(request.getSpecialRequests());
        r.setStatus("PENDING");
        return reservationRepository.save(r);
    }

    /** Owner: list all reservations for own agency */
    public List<ReservationJpa> getAllForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findByIdAgencyOrderByReservationDateDescReservationTimeDesc(idAgency);
    }

    /** Owner: list reservations in a date range */
    public List<ReservationJpa> getForAgencyBetween(LocalDate from, LocalDate to) {
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findByIdAgencyAndReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(idAgency, from, to);
    }

    /** Owner: update reservation status */
    public Optional<ReservationJpa> updateStatus(long id, String status) {
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findById(id)
                .filter(r -> r.getIdAgency() == idAgency)
                .map(r -> {
                    r.setStatus(status);
                    ReservationJpa saved = reservationRepository.save(r);
                    if ("CONFIRMED".equals(status)) {
                        sendConfirmationSms(saved);
                    }
                    return saved;
                });
    }

    private void sendConfirmationSms(ReservationJpa r) {
        String msg = String.format(
                "Prenotazione confermata per %s il %s alle %s (%d %s). A presto!",
                r.getCustomerName(),
                r.getReservationDate().toString(),
                r.getReservationTime().toString().substring(0, 5),
                r.getPartySize(),
                r.getPartySize() == 1 ? "persona" : "persone"
        );
        twilioService.sendSms(r.getCustomerPhone(), msg);
    }

    /** Owner: delete reservation */
    public boolean delete(long id) {
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findById(id)
                .filter(r -> r.getIdAgency() == idAgency)
                .map(r -> { reservationRepository.delete(r); return true; })
                .orElse(false);
    }
}
