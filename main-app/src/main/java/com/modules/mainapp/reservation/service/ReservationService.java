package com.modules.mainapp.reservation.service;

import com.modules.mainapp.reservation.dto.CreateReservationRequest;
import com.modules.mainapp.reservation.dto.StaffReservationRequest;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.repository.ReservationRepository;
import com.modules.mainapp.service.TwilioService;
import com.modules.pushmodule.service.StaffPushNotifier;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @Autowired
    private TwilioService twilioService;

    /** Push ai dispositivi dello staff (app "Sala"). */
    @Autowired(required = false)
    private StaffPushNotifier staffPushNotifier;

    @Value("${spring.application.name:RestaurantFlow}")
    private String appName;

    /** Stati ammessi. ARRIVED = cliente arrivato/seduto (nessun SMS, niente promemoria). */
    public static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "ARRIVED", "CANCELLED", "NO_SHOW");
    /** Stati con cui lo staff può creare una prenotazione (walk-in = ARRIVED). */
    static final Set<String> STAFF_CREATE_STATUSES = Set.of("PENDING", "CONFIRMED", "ARRIVED");

    public static final String SOURCE_PUBLIC = "PUBLIC";
    public static final String SOURCE_STAFF = "STAFF";

    /** Fuso del locale per "oggi" (le prenotazioni passate non si possono inserire). */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");
    Clock clock = Clock.system(BUSINESS_ZONE);

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
        r.setSource(SOURCE_PUBLIC);
        ReservationJpa saved = reservationRepository.save(r);
        if (staffPushNotifier != null) staffPushNotifier.newReservation(saved);
        return saved;
    }

    /**
     * Staff (ADMIN/WAITER): prenotazione da telefonata o walk-in. Default CONFIRMED, origine STAFF,
     * nessuna push allo staff e nessun SMS salvo {@code notifyCustomer=true} con telefono presente.
     * Ammessa la data di oggi anche con orario già passato (walk-in), non i giorni precedenti.
     */
    public ReservationJpa createStaffReservation(StaffReservationRequest request) {
        if (request == null) throw new IllegalArgumentException("Dati incompleti");
        long idAgency = authUserProvider.getAgencyId();
        String name = trimToNull(request.getCustomerName());
        if (name == null) throw new IllegalArgumentException("Nome cliente obbligatorio");
        validatePartySize(request.getPartySize(), true);
        if (request.getReservationDate() == null || request.getReservationTime() == null)
            throw new IllegalArgumentException("Data e ora obbligatorie");
        validateNotPast(request.getReservationDate());
        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? "CONFIRMED" : request.getStatus().trim().toUpperCase();
        if (!STAFF_CREATE_STATUSES.contains(status))
            throw new IllegalArgumentException("Stato non valido: " + status);

        ReservationJpa r = new ReservationJpa();
        r.setIdAgency(idAgency);
        r.setCustomerName(name);
        // Il walk-in può non lasciare il telefono: la colonna è NOT NULL, si salva vuoto
        String phone = trimToNull(request.getCustomerPhone());
        r.setCustomerPhone(phone == null ? "" : phone);
        r.setCustomerEmail(trimToNull(request.getCustomerEmail()));
        r.setPartySize(request.getPartySize());
        r.setReservationDate(request.getReservationDate());
        r.setReservationTime(request.getReservationTime());
        r.setSpecialRequests(trimToNull(request.getSpecialRequests()));
        r.setStatus(status);
        r.setSource(SOURCE_STAFF);
        ReservationJpa saved = reservationRepository.save(r);
        if (Boolean.TRUE.equals(request.getNotifyCustomer()) && "CONFIRMED".equals(status)) {
            sendConfirmationSms(saved);
        }
        return saved;
    }

    /**
     * Staff: modifica nome, telefono, email, persone, data/ora, note. I campi null restano invariati.
     * Se cambiano data o ora i promemoria SMS ripartono da zero.
     */
    public Optional<ReservationJpa> updateStaffReservation(long id, StaffReservationRequest request) {
        if (request == null) throw new IllegalArgumentException("Dati incompleti");
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findById(id)
                .filter(r -> r.getIdAgency() == idAgency)
                .map(r -> {
                    if (request.getCustomerName() != null) {
                        String name = trimToNull(request.getCustomerName());
                        if (name == null) throw new IllegalArgumentException("Nome cliente obbligatorio");
                        r.setCustomerName(name);
                    }
                    if (request.getCustomerPhone() != null) {
                        String phone = trimToNull(request.getCustomerPhone());
                        r.setCustomerPhone(phone == null ? "" : phone);
                    }
                    if (request.getCustomerEmail() != null) r.setCustomerEmail(trimToNull(request.getCustomerEmail()));
                    if (request.getPartySize() != null) {
                        validatePartySize(request.getPartySize(), true);
                        r.setPartySize(request.getPartySize());
                    }
                    if (request.getSpecialRequests() != null) r.setSpecialRequests(trimToNull(request.getSpecialRequests()));
                    boolean dateChanged = request.getReservationDate() != null
                            && !request.getReservationDate().equals(r.getReservationDate());
                    boolean timeChanged = request.getReservationTime() != null
                            && !request.getReservationTime().equals(r.getReservationTime());
                    if (dateChanged) {
                        validateNotPast(request.getReservationDate());
                        r.setReservationDate(request.getReservationDate());
                    }
                    if (timeChanged) r.setReservationTime(request.getReservationTime());
                    if (dateChanged || timeChanged) {
                        r.setReminder24hSent(false);
                        r.setReminder1hSent(false);
                    }
                    ReservationJpa saved = reservationRepository.save(r);
                    if (Boolean.TRUE.equals(request.getNotifyCustomer()) && "CONFIRMED".equals(saved.getStatus())) {
                        sendConfirmationSms(saved);
                    }
                    return saved;
                });
    }

    private void validatePartySize(Integer partySize, boolean required) {
        if (partySize == null) {
            if (required) throw new IllegalArgumentException("Numero di persone obbligatorio");
            return;
        }
        if (partySize <= 0 || partySize > 500) throw new IllegalArgumentException("Numero di persone non valido");
    }

    private void validateNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.now(clock))) throw new IllegalArgumentException("Data già passata");
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
    public Optional<ReservationJpa> updateStatus(long id, String rawStatus) {
        String status = rawStatus == null ? null : rawStatus.trim().toUpperCase();
        if (status == null || !STATUSES.contains(status))
            throw new IllegalArgumentException("Stato non valido: " + rawStatus);
        long idAgency = authUserProvider.getAgencyId();
        return reservationRepository.findById(id)
                .filter(r -> r.getIdAgency() == idAgency)
                .map(r -> {
                    String previous = r.getStatus();
                    r.setStatus(status);
                    ReservationJpa saved = reservationRepository.save(r);
                    // SMS solo alla conferma vera: non rimandarlo se si torna indietro da "Arrivato"
                    if ("CONFIRMED".equals(status) && !"CONFIRMED".equals(previous) && !"ARRIVED".equals(previous)) {
                        sendConfirmationSms(saved);
                    }
                    return saved;
                });
    }

    private void sendConfirmationSms(ReservationJpa r) {
        if (r.getCustomerPhone() == null || r.getCustomerPhone().isBlank()) return;
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
