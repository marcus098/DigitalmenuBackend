package com.modules.authmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.model.superadmin.AgencyAdminInfoJpa;
import com.modules.authmodule.model.superadmin.SubscriptionStatus;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.repository.superadmin.AgencyAdminInfoRepository;
import com.modules.common.model.enums.Role;
import com.modules.common.responses.AuthResponse;
import com.modules.common.responses.DataResponse;
import com.modules.common.utilities.Constants;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.servletconfiguration.security.JwtService;
import com.modules.servletconfiguration.security.SuperadminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceSuperadminTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AgencyRepository agencyRepository;
    private AgencyAdminInfoRepository infoRepository;
    private UserService userService;
    private AuthenticatedUserProvider authUserProvider;
    private JwtService jwt;
    private AuthService service;

    private User admin;
    private AgencyJpa agency;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        agencyRepository = mock(AgencyRepository.class);
        infoRepository = mock(AgencyAdminInfoRepository.class);
        userService = mock(UserService.class);
        authUserProvider = mock(AuthenticatedUserProvider.class);
        jwt = SuperadminTestSupport.jwtService();
        service = new AuthService(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "agencyRepository", agencyRepository);
        ReflectionTestUtils.setField(service, "agencyAdminInfoRepository", infoRepository);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "authUserProvider", authUserProvider);
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        agency = new AgencyJpa("Pizzeria", "pizzeria");
        agency.setId(7L);
        agency.setTrial(false);
        agency.setBillingEndAt(OffsetDateTime.now().plusDays(30));
        when(agencyRepository.findByIdAndDeleted(7L, false)).thenReturn(Optional.of(agency));
        when(infoRepository.findById(7L)).thenReturn(Optional.empty());

        admin = new User("pizzeria", "Mario", "Rossi", "owner@pizzeria.it", "hash", Role.ROLE_ADMIN, 7L, null, null, null, true);
        admin.setId(42L);
        admin.setEmailConfirmed(true);
    }

    private User superadmin() {
        User u = new User("boss@platform.it", "Superadmin", "", "boss@platform.it", "hash", Role.ROLE_SUPERADMIN, null, null, null, null, true);
        u.setId(1L);
        u.setEmailConfirmed(true);
        return u;
    }

    @Test
    void superadminLoginSkipsAgency() {
        User sa = superadmin();
        when(userRepository.findByUsernameAndDeleted("boss@platform.it", false)).thenReturn(Optional.of(sa));

        DataResponse<AuthResponse> r = service.login("boss@platform.it", "pw");

        AuthResponse a = r.getData();
        assertEquals(200, a.getStatus());
        assertEquals("Superadmin", a.getLocalname());
        assertEquals(-1, a.getIdAgency());
        assertEquals(Constants.IS_EMAIL_CONFIRMED * Constants.IS_SUPERADMIN, a.getControlsVariable());
        assertTrue(a.check(Constants.IS_SUPERADMIN));
        assertFalse(a.check(Constants.IS_ADMIN));
        assertEquals("boss@platform.it", jwt.extractEmail(a.getAccessToken()));
        assertNull(a.getImpersonatedBy());
        verifyNoInteractions(agencyRepository);
    }

    @Test
    void suspendedAgencyLoginReturns402EvenWithValidBilling() {
        when(userRepository.findByUsernameAndDeleted("pizzeria", false)).thenReturn(Optional.of(admin));
        AgencyAdminInfoJpa info = new AgencyAdminInfoJpa(7L);
        info.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
        when(infoRepository.findById(7L)).thenReturn(Optional.of(info));

        assertEquals(402, service.login("pizzeria", "pw").getData().getStatus());

        info.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        assertEquals(402, service.login("pizzeria", "pw").getData().getStatus());

        info.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        assertEquals(200, service.login("pizzeria", "pw").getData().getStatus());
    }

    @Test
    void expiredBillingStillReturns402() {
        when(userRepository.findByUsernameAndDeleted("pizzeria", false)).thenReturn(Optional.of(admin));
        agency.setBillingEndAt(OffsetDateTime.now().minusDays(1));
        assertEquals(402, service.login("pizzeria", "pw").getData().getStatus());
    }

    @Test
    void checkTokenNeverRefreshesImpersonationTokenAndExposesSuperadmin() {
        // 60 minuti < jwt_expiration_check: un token normale verrebbe rinnovato
        String token = jwt.generateImpersonationToken(AuthService.toDto(admin), 1L, "boss@platform.it");
        when(authUserProvider.getUserId()).thenReturn(42L);
        when(userService.loadUserById(42L)).thenReturn(admin);

        AuthResponse r = service.checkToken(token);

        assertNotNull(r);
        assertEquals(200, r.getStatus());
        assertEquals("", r.getAccessToken());
        assertFalse(r.isNew());
        assertEquals("boss@platform.it", r.getImpersonatedBy());
    }

    @Test
    void checkTokenRefreshesNormalTokenExpiringSoon() {
        String token = jwt.builToken(AuthService.toDto(admin),
                new java.util.HashMap<>(java.util.Map.of("email", admin.getEmail(), "id", admin.getId())), 60_000);
        when(authUserProvider.getUserId()).thenReturn(42L);
        when(userService.loadUserById(42L)).thenReturn(admin);

        AuthResponse r = service.checkToken(token);

        assertNotNull(r);
        assertFalse(r.getAccessToken().isEmpty());
        assertNull(r.getImpersonatedBy());
    }

    @Test
    void checkTokenImpersonationWorksOnBlockedAgencyButNormalDoesNot() {
        AgencyAdminInfoJpa info = new AgencyAdminInfoJpa(7L);
        info.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
        when(infoRepository.findById(7L)).thenReturn(Optional.of(info));
        when(authUserProvider.getUserId()).thenReturn(42L);
        when(userService.loadUserById(42L)).thenReturn(admin);

        assertNotNull(service.checkToken(jwt.generateImpersonationToken(AuthService.toDto(admin), 1L, "boss@platform.it")));
        assertNull(service.checkToken(jwt.generateToken(AuthService.toDto(admin))));
    }

    @Test
    void checkTokenForSuperadmin() {
        User sa = superadmin();
        when(authUserProvider.getUserId()).thenReturn(1L);
        when(userService.loadUserById(1L)).thenReturn(sa);

        AuthResponse r = service.checkToken(jwt.generateToken(AuthService.toDto(sa)));

        assertNotNull(r);
        assertEquals("Superadmin", r.getLocalname());
        assertEquals(-1, r.getIdAgency());
        verifyNoInteractions(agencyRepository);
    }

    @Test
    void reservedLocalname() {
        assertTrue(AuthService.isReservedLocalname("superadmin"));
        assertTrue(AuthService.isReservedLocalname(" SuperAdmin "));
        assertFalse(AuthService.isReservedLocalname("pizzeria"));
        assertFalse(AuthService.isReservedLocalname(null));
    }
}
