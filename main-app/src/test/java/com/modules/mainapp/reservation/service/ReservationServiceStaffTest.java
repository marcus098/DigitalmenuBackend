package com.modules.mainapp.reservation.service;

import com.modules.mainapp.reservation.dto.CreateReservationRequest;
import com.modules.mainapp.reservation.dto.StaffReservationRequest;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.repository.ReservationRepository;
import com.modules.mainapp.service.TwilioService;
import com.modules.pushmodule.service.StaffPushNotifier;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReservationServiceStaffTest {

    private static final long AGENCY = 5L;
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    // 12 ottobre 2026, 20:00 a Roma
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private ReservationRepository repo;
    private TwilioService twilio;
    private StaffPushNotifier push;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repo = mock(ReservationRepository.class);
        twilio = mock(TwilioService.class);
        push = mock(StaffPushNotifier.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getAgencyId()).thenReturn(AGENCY);
        service = new ReservationService();
        ReflectionTestUtils.setField(service, "reservationRepository", repo);
        ReflectionTestUtils.setField(service, "authUserProvider", auth);
        ReflectionTestUtils.setField(service, "twilioService", twilio);
        ReflectionTestUtils.setField(service, "staffPushNotifier", push);
        service.clock = Clock.fixed(TODAY.atTime(20, 0).atZone(ROME).toInstant(), ROME);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private StaffReservationRequest req(String name, String phone, Integer people, LocalDate date, LocalTime time) {
        StaffReservationRequest r = new StaffReservationRequest();
        r.setCustomerName(name);
        r.setCustomerPhone(phone);
        r.setPartySize(people);
        r.setReservationDate(date);
        r.setReservationTime(time);
        return r;
    }

    private ReservationJpa existing(String status) {
        ReservationJpa r = new ReservationJpa();
        r.setId(9L);
        r.setIdAgency(AGENCY);
        r.setCustomerName("Rossi");
        r.setCustomerPhone("+39333");
        r.setPartySize(2);
        r.setReservationDate(TODAY.plusDays(1));
        r.setReservationTime(LocalTime.of(20, 30));
        r.setStatus(status);
        r.setReminder24hSent(true);
        r.setReminder1hSent(true);
        when(repo.findById(9L)).thenReturn(Optional.of(r));
        return r;
    }

    // ── Creazione staff ──────────────────────────────────────────────────

    @Test
    void staffCreate_defaultsConfirmedStaffSource_noSmsNoPush() {
        ReservationJpa saved = service.createStaffReservation(req("  Bianchi ", "+39333", 4, TODAY.plusDays(2), LocalTime.of(21, 0)));
        assertEquals("CONFIRMED", saved.getStatus());
        assertEquals(ReservationService.SOURCE_STAFF, saved.getSource());
        assertEquals(AGENCY, saved.getIdAgency());
        assertEquals("Bianchi", saved.getCustomerName());
        verifyNoInteractions(twilio);
        verifyNoInteractions(push);
    }

    @Test
    void staffCreate_notifyCustomerSendsSmsWhenPhonePresent() {
        StaffReservationRequest r = req("Bianchi", "+39333", 2, TODAY.plusDays(1), LocalTime.of(20, 0));
        r.setNotifyCustomer(true);
        service.createStaffReservation(r);
        verify(twilio).sendSms(eq("+39333"), anyString());
    }

    @Test
    void staffCreate_notifyWithoutPhone_noSms() {
        StaffReservationRequest r = req("Walk-in", null, 2, TODAY, LocalTime.of(19, 0));
        r.setNotifyCustomer(true);
        ReservationJpa saved = service.createStaffReservation(r);
        assertEquals("", saved.getCustomerPhone());
        verifyNoInteractions(twilio);
    }

    @Test
    void staffCreate_walkInTodayPastTimeAsArrived() {
        StaffReservationRequest r = req("Walk-in", "", 3, TODAY, LocalTime.of(13, 0));
        r.setStatus("arrived");
        ReservationJpa saved = service.createStaffReservation(r);
        assertEquals("ARRIVED", saved.getStatus());
        verifyNoInteractions(twilio);
    }

    @Test
    void staffCreate_validation() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createStaffReservation(req(" ", "1", 2, TODAY, LocalTime.NOON)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createStaffReservation(req("A", "1", 0, TODAY, LocalTime.NOON)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createStaffReservation(req("A", "1", null, TODAY, LocalTime.NOON)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createStaffReservation(req("A", "1", 2, null, LocalTime.NOON)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createStaffReservation(req("A", "1", 2, TODAY.minusDays(1), LocalTime.NOON)));
        StaffReservationRequest cancelled = req("A", "1", 2, TODAY, LocalTime.NOON);
        cancelled.setStatus("CANCELLED");
        assertThrows(IllegalArgumentException.class, () -> service.createStaffReservation(cancelled));
        verify(repo, never()).save(any());
    }

    @Test
    void publicCreate_marksPublicSourceAndPushes() {
        CreateReservationRequest r = new CreateReservationRequest();
        r.setCustomerName("C");
        r.setCustomerPhone("1");
        r.setPartySize(2);
        r.setReservationDate(TODAY.plusDays(1));
        r.setReservationTime(LocalTime.NOON);
        ReservationJpa saved = service.createPublicReservation(AGENCY, r);
        assertEquals("PENDING", saved.getStatus());
        assertEquals(ReservationService.SOURCE_PUBLIC, saved.getSource());
        verify(push).newReservation(saved);
    }

    // ── Modifica staff ───────────────────────────────────────────────────

    @Test
    void staffUpdate_changesFieldsAndResetsReminders() {
        ReservationJpa r = existing("CONFIRMED");
        StaffReservationRequest body = new StaffReservationRequest();
        body.setPartySize(6);
        body.setReservationTime(LocalTime.of(21, 15));
        body.setSpecialRequests("Seggiolone");
        ReservationJpa saved = service.updateStaffReservation(9L, body).orElseThrow();
        assertSame(r, saved);
        assertEquals(6, saved.getPartySize());
        assertEquals(LocalTime.of(21, 15), saved.getReservationTime());
        assertEquals("Seggiolone", saved.getSpecialRequests());
        assertEquals("Rossi", saved.getCustomerName());
        assertFalse(saved.isReminder24hSent());
        assertFalse(saved.isReminder1hSent());
        verifyNoInteractions(twilio);
    }

    @Test
    void staffUpdate_onlyNotesKeepsReminders() {
        existing("CONFIRMED");
        StaffReservationRequest body = new StaffReservationRequest();
        body.setSpecialRequests("Tavolo fuori");
        ReservationJpa saved = service.updateStaffReservation(9L, body).orElseThrow();
        assertTrue(saved.isReminder24hSent());
    }

    @Test
    void staffUpdate_otherAgencyNotFound() {
        ReservationJpa r = existing("CONFIRMED");
        r.setIdAgency(99L);
        assertTrue(service.updateStaffReservation(9L, new StaffReservationRequest()).isEmpty());
        verify(repo, never()).save(any());
    }

    @Test
    void staffUpdate_rejectsBlankNameAndPastDate() {
        existing("CONFIRMED");
        StaffReservationRequest blank = new StaffReservationRequest();
        blank.setCustomerName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.updateStaffReservation(9L, blank));
        StaffReservationRequest past = new StaffReservationRequest();
        past.setReservationDate(TODAY.minusDays(3));
        assertThrows(IllegalArgumentException.class, () -> service.updateStaffReservation(9L, past));
    }

    // ── Stato ARRIVED ────────────────────────────────────────────────────

    @Test
    void status_arrivedAcceptedWithoutSms() {
        existing("CONFIRMED");
        ReservationJpa saved = service.updateStatus(9L, "ARRIVED").orElseThrow();
        assertEquals("ARRIVED", saved.getStatus());
        verifyNoInteractions(twilio);
    }

    @Test
    void status_backFromArrivedToConfirmed_noSecondSms() {
        existing("ARRIVED");
        service.updateStatus(9L, "CONFIRMED");
        verifyNoInteractions(twilio);
    }

    @Test
    void status_pendingToConfirmed_sendsSms() {
        existing("PENDING");
        service.updateStatus(9L, "CONFIRMED");
        verify(twilio).sendSms(eq("+39333"), anyString());
    }

    @Test
    void status_unknownRejected() {
        existing("PENDING");
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(9L, "SEATED_MAYBE"));
        verify(repo, never()).save(any());
    }
}
