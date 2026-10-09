package com.modules.mainapp.superadmin;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.model.superadmin.*;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.repository.superadmin.AgencyAdminInfoRepository;
import com.modules.authmodule.repository.superadmin.AgencyNoteRepository;
import com.modules.authmodule.repository.superadmin.SuperadminAuditRepository;
import com.modules.authmodule.service.AuthService;
import com.modules.common.model.enums.Role;
import com.modules.mainapp.payment.repository.AgencyPaymentAccountRepository;
import com.modules.mainapp.superadmin.SuperadminDtos.*;
import com.modules.servletconfiguration.security.JwtService;
import com.modules.servletconfiguration.security.SuperadminTestSupport;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuperadminServiceTest {

    private AgencyRepository agencies;
    private UserRepository users;
    private AgencyAdminInfoRepository infos;
    private AgencyNoteRepository notes;
    private SuperadminAuditRepository auditRepo;
    private SuperadminAuditService audit;
    private AgencyPaymentAccountRepository payments;
    private OrderStatsProvider orders;
    private JwtService jwt;
    private SuperadminService service;
    private AgencyJpa agency;
    private User admin;
    private AgencyAdminInfoJpa storedInfo;
    private final SuperadminService.Actor actor = new SuperadminService.Actor(1L, "boss@platform.it", "10.0.0.1");

    @BeforeEach
    void setUp() {
        agencies = mock(AgencyRepository.class);
        users = mock(UserRepository.class);
        infos = mock(AgencyAdminInfoRepository.class);
        notes = mock(AgencyNoteRepository.class);
        auditRepo = mock(SuperadminAuditRepository.class);
        audit = mock(SuperadminAuditService.class);
        payments = mock(AgencyPaymentAccountRepository.class);
        orders = mock(OrderStatsProvider.class);
        jwt = SuperadminTestSupport.jwtService();
        AuthService authService = mock(AuthService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(authService, "jwtService", jwt);
        service = new SuperadminService(agencies, users, infos, notes, auditRepo, audit, payments, orders, jwt, authService);

        agency = new AgencyJpa("Pizzeria", "pizzeria");
        agency.setId(7L);
        agency.setTrial(true);
        when(agencies.findById(7L)).thenReturn(Optional.of(agency));
        when(agencies.save(any())).thenAnswer(i -> i.getArgument(0));
        when(infos.findById(7L)).thenAnswer(i -> Optional.ofNullable(storedInfo));
        when(infos.save(any())).thenAnswer(i -> storedInfo = i.getArgument(0));
        when(infos.findAll()).thenAnswer(i -> storedInfo == null ? List.of() : List.of(storedInfo));

        admin = new User("pizzeria", "Mario", "Rossi", "owner@pizzeria.it", "hash", Role.ROLE_ADMIN, 7L, "333", null, null, true);
        admin.setId(42L);
        admin.setEmailConfirmed(true);
        when(users.findAllByRoleAndDeletedOrderByCreatedAtAscIdAsc("ROLE_ADMIN", false)).thenReturn(List.of(admin));
        when(users.findFirstByIdAgencyAndRoleAndDeletedOrderByCreatedAtAscIdAsc(7L, "ROLE_ADMIN", false)).thenReturn(Optional.of(admin));
        when(orders.statsByAgency(any())).thenReturn(Map.of());
        when(infos.countTablesByAgency()).thenReturn(List.<Object[]>of(new Object[]{7L, 5L}));
        when(infos.countUsersByAgencyAndRole("ROLE_WAITER")).thenReturn(List.<Object[]>of(new Object[]{7L, 2L}));
    }

    private static SubscriptionUpdate update(String status, Boolean trial) {
        SubscriptionUpdate u = new SubscriptionUpdate();
        u.setSubscriptionStatus(status);
        u.setTrial(trial);
        return u;
    }

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void subscriptionUpdateValidation() {
        assertEquals(400, status(() -> service.updateSubscription(7L, null, actor)));
        assertEquals(400, status(() -> service.updateSubscription(7L, update(null, true), actor)));
        assertEquals(400, status(() -> service.updateSubscription(7L, update("PAUSED", true), actor)));
        assertEquals(400, status(() -> service.updateSubscription(7L, update("ACTIVE", null), actor)));
        SubscriptionUpdate badDate = update("ACTIVE", false);
        badDate.setBillingEndAt("31/12/2026");
        assertEquals(400, status(() -> service.updateSubscription(7L, badDate, actor)));
        SubscriptionUpdate badFrom = update("ACTIVE", false);
        badFrom.setActiveFrom("2026-13-01");
        assertEquals(400, status(() -> service.updateSubscription(7L, badFrom, actor)));
        SubscriptionUpdate longPlan = update("ACTIVE", false);
        longPlan.setPlan("x".repeat(101));
        assertEquals(400, status(() -> service.updateSubscription(7L, longPlan, actor)));
        assertEquals(404, status(() -> service.updateSubscription(99L, update("ACTIVE", false), actor)));
        verify(agencies, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void subscriptionUpdateSavesAgencyInfoAndAudit() {
        SubscriptionUpdate u = update("active", false);
        u.setActiveFrom("2026-10-01");
        u.setSubscriptionNumber("  SUB-001 ");
        u.setPlan("Pro");
        u.setBillingEndAt("2027-09-30");
        u.setTrialEndAt(null);

        AgencySummary s = service.updateSubscription(7L, u, actor);

        assertFalse(agency.isTrial());
        assertNull(agency.getTrialEndAt());
        OffsetDateTime expected = LocalDate.of(2027, 9, 30).atTime(23, 59, 59).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime();
        assertTrue(expected.isEqual(agency.getBillingEndAt()));
        assertEquals(SubscriptionStatus.ACTIVE, storedInfo.getSubscriptionStatus());
        assertEquals(LocalDate.of(2026, 10, 1), storedInfo.getActiveFrom());
        assertEquals("SUB-001", storedInfo.getSubscriptionNumber());
        assertEquals(1L, storedInfo.getUpdatedBy());

        assertEquals("ACTIVE", s.subscriptionStatus());
        assertEquals("Pro", s.plan());
        assertEquals("pizzeria", s.localname());
        assertEquals("owner@pizzeria.it", s.adminEmail());
        assertEquals(5, s.tablesCount());
        assertEquals(2, s.waitersCount());
        assertEquals("NONE", s.paymentProvider());
        assertFalse(s.blocked());

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(1L), eq("boss@platform.it"), eq(7L), eq(SuperadminAction.SUBSCRIPTION_UPDATE), detail.capture(), eq("10.0.0.1"));
        assertTrue(detail.getValue().contains("status: TRIAL -> ACTIVE"));
        assertTrue(detail.getValue().contains("plan: null -> Pro"));
    }

    @Test
    void suspendedSummaryIsBlocked() {
        SubscriptionUpdate u = update("SUSPENDED", false);
        u.setBillingEndAt("2030-01-01T00:00:00Z");
        AgencySummary s = service.updateSubscription(7L, u, actor);
        assertTrue(s.blocked());
        assertEquals("SUSPENDED", s.subscriptionStatus());
    }

    @Test
    void parseDateTimeFormats() {
        assertEquals(OffsetDateTime.parse("2026-10-10T10:00:00Z"), SuperadminService.parseDateTime("2026-10-10T10:00:00Z", "f"));
        assertEquals(ZoneId.of("Europe/Rome").getRules().getOffset(LocalDateTime.of(2026, 7, 1, 23, 59, 59)),
                SuperadminService.parseDateTime("2026-07-01", "f").getOffset());
        assertEquals(LocalTime.of(23, 59, 59), SuperadminService.parseDateTime("2026-07-01", "f").toLocalTime());
        assertNull(SuperadminService.parseDateTime(null, "f"));
        assertNull(SuperadminService.parseDateTime(" ", "f"));
    }

    @Test
    void impersonateIssuesTokenForOldestAdminAndAudits() {
        ImpersonationResponse r = service.impersonate(7L, new ImpersonateRequest("Supporto menu"), actor);

        Claims c = jwt.extractAll(r.token());
        assertEquals("pizzeria", c.getSubject());
        assertEquals(42L, jwt.extractUserID(r.token()));
        assertTrue(JwtService.isImpersonation(c));
        assertEquals(1L, JwtService.impersonatedBy(c));
        assertEquals(r.token(), r.auth().getAccessToken());
        assertEquals("boss@platform.it", r.auth().getImpersonatedBy());
        assertEquals(7L, r.auth().getIdAgency());
        assertEquals(200, r.auth().getStatus());
        assertEquals(new AgencyRef(7L, "pizzeria", "pizzeria"), r.agency());
        Instant exp = Instant.parse(r.expiresAt());
        assertTrue(exp.isBefore(Instant.now().plus(Duration.ofMinutes(61))));
        assertTrue(exp.isAfter(Instant.now().plus(Duration.ofMinutes(58))));
        verify(audit).record(eq(1L), eq("boss@platform.it"), eq(7L), eq(SuperadminAction.IMPERSONATE_START),
                contains("Supporto menu"), eq("10.0.0.1"));
    }

    @Test
    void impersonateWorksOnBlockedAgency() {
        agency.setTrial(false);
        agency.setBillingEndAt(OffsetDateTime.now().minusDays(10));
        assertNotNull(service.impersonate(7L, new ImpersonateRequest("Pagamento scaduto"), actor).token());
    }

    @Test
    void impersonateValidation() {
        assertEquals(400, status(() -> service.impersonate(7L, new ImpersonateRequest("  a "), actor)));
        assertEquals(400, status(() -> service.impersonate(7L, new ImpersonateRequest("x".repeat(501)), actor)));
        assertEquals(400, status(() -> service.impersonate(7L, null, actor)));
        assertEquals(404, status(() -> service.impersonate(99L, new ImpersonateRequest("motivo"), actor)));
        when(users.findFirstByIdAgencyAndRoleAndDeletedOrderByCreatedAtAscIdAsc(7L, "ROLE_ADMIN", false)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.impersonate(7L, new ImpersonateRequest("motivo"), actor)));
        verifyNoInteractions(audit);
    }

    @Test
    void noteValidationAndCreate() {
        assertEquals(400, status(() -> service.addNote(7L, new NoteCreate("   ", null), actor)));
        assertEquals(400, status(() -> service.addNote(7L, new NoteCreate("x".repeat(4001), null), actor)));
        when(notes.save(any())).thenAnswer(i -> {
            AgencyNoteJpa n = i.getArgument(0);
            n.setId(5L);
            return n;
        });
        Note n = service.addNote(7L, new NoteCreate(" Chiamato il titolare ", true), actor);
        assertEquals(5L, n.id());
        assertEquals("Chiamato il titolare", n.text());
        assertTrue(n.pinned());
        assertEquals("boss@platform.it", n.authorEmail());
        verify(audit).record(eq(1L), any(), eq(7L), eq(SuperadminAction.NOTE_ADD), any(), any());
    }
}
