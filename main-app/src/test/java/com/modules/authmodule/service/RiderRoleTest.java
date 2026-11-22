package com.modules.authmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.repository.superadmin.AgencyAdminInfoRepository;
import com.modules.authmodule.request.SignupWaiter;
import com.modules.common.dto.WaiterDto;
import com.modules.common.model.enums.Role;
import com.modules.common.responses.AuthResponse;
import com.modules.common.utilities.Constants;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.servletconfiguration.security.JwtService;
import com.modules.servletconfiguration.security.SuperadminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ruolo rider (ROLE_DELIVERY): invito, login/check, approvazione, eliminazione e permessi degli endpoint. */
class RiderRoleTest {

    private UserRepository userRepository;
    private AgencyRepository agencyRepository;
    private AuthenticatedUserProvider auth;
    private AuthService service;
    private AgencyJpa agency;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(any(), any())).thenReturn(true);
        when(encoder.encode(any())).thenReturn("hash");
        agencyRepository = mock(AgencyRepository.class);
        AgencyAdminInfoRepository info = mock(AgencyAdminInfoRepository.class);
        auth = mock(AuthenticatedUserProvider.class);
        when(auth.getAgencyId()).thenReturn(7L);
        when(auth.getUserId()).thenReturn(42L);
        Utilities utilities = mock(Utilities.class);
        when(utilities.generateOtpEmailCode()).thenReturn("123456");
        JwtService jwt = SuperadminTestSupport.jwtService();

        service = new AuthService(userRepository, encoder);
        ReflectionTestUtils.setField(service, "agencyRepository", agencyRepository);
        ReflectionTestUtils.setField(service, "agencyAdminInfoRepository", info);
        ReflectionTestUtils.setField(service, "authUserProvider", auth);
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "utilities", utilities);

        agency = new AgencyJpa("Pizzeria", "pizzeria", "invite-code", "admin-code");
        agency.setId(7L);
        agency.setTrial(false);
        agency.setBillingEndAt(OffsetDateTime.now().plusDays(30));
        when(agencyRepository.findByIdAndDeleted(7L, false)).thenReturn(Optional.of(agency));
        when(info.findById(7L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(100L);
            return u;
        });
    }

    private SignupWaiter signup(String role) {
        SignupWaiter s = new SignupWaiter();
        s.setIdAgency(7L);
        s.setCode("invite-code");
        s.setFirstName("Luca");
        s.setLastName("Rider");
        s.setEmail("rider@test.it");
        s.setPassword("Password#1");
        s.setRole(role);
        return s;
    }

    private static User user(Role role, boolean emailConfirmed, boolean approved) {
        User u = new User("pizzeria1", "Luca", "Rider", "rider@test.it", "hash", role, 7L, null, null, null, false);
        u.setId(100L);
        u.setEmailConfirmed(emailConfirmed);
        u.setGeneralConfirmed(approved);
        return u;
    }

    // ── Invito ───────────────────────────────────────────────────────────────

    @Test
    void signupRiderCreatesDeliveryUser() {
        assertTrue(service.signupWaiter(signup("ROLE_DELIVERY")).getData());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Constants.ROLE_DELIVERY, saved.getValue().getRole());
        assertFalse(saved.getValue().isGeneralConfirmed());
    }

    @Test
    void signupDefaultsToWaiterAndRejectsOtherRoles() {
        assertTrue(service.signupWaiter(signup(null)).getData());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Constants.ROLE_WAITER, saved.getValue().getRole());

        clearInvocations(userRepository);
        for (String bad : List.of("ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_USER", "admin")) {
            assertFalse(service.signupWaiter(signup(bad)).getData(), bad);
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void secondInvitedStaffGetsDistinctUsername() {
        when(userRepository.findByUsernameAndDeleted("pizzeria1", false)).thenReturn(Optional.of(user(Role.ROLE_WAITER, true, true)));
        assertTrue(service.signupWaiter(signup("ROLE_DELIVERY")).getData());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("pizzeria2", saved.getValue().getUsername());
    }

    @Test
    void signupWithWrongCodeRejected() {
        SignupWaiter s = signup("ROLE_DELIVERY");
        s.setCode("wrong");
        assertFalse(service.signupWaiter(s).getData());
        verify(userRepository, never()).save(any());
    }

    // ── Login / check ────────────────────────────────────────────────────────

    @Test
    void riderLoginNeedsApprovalThenControlsVariableHasIsDelivery() {
        User rider = user(Role.ROLE_DELIVERY, true, false);
        when(userRepository.findByUsernameAndDeleted("rider@test.it", false)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeleted("rider@test.it", false)).thenReturn(Optional.of(rider));
        assertEquals(406, service.login("rider@test.it", "pw").getData().getStatus());

        rider.setGeneralConfirmed(true);
        AuthResponse r = service.login("rider@test.it", "pw").getData();
        assertEquals(200, r.getStatus());
        assertEquals(Constants.IS_EMAIL_CONFIRMED * Constants.IS_DELIVERY, r.getControlsVariable());
        assertTrue(r.check(Constants.IS_DELIVERY));
        assertFalse(r.check(Constants.IS_WAITER));
        assertFalse(r.check(Constants.IS_ADMIN));
    }

    @Test
    void checkTokenReturnsIsDeliveryForRider() {
        User rider = user(Role.ROLE_DELIVERY, true, true);
        UserService userService = mock(UserService.class);
        when(userService.loadUserById(100L)).thenReturn(rider);
        ReflectionTestUtils.setField(service, "userService", userService);
        when(auth.getUserId()).thenReturn(100L);
        when(userRepository.findByEmailAndDeleted("rider@test.it", false)).thenReturn(Optional.of(rider));
        when(userRepository.findByUsernameAndDeleted(any(), eq(false))).thenReturn(Optional.empty());
        String token = service.login("rider@test.it", "pw").getData().getAccessToken();
        AuthResponse c = service.checkToken(token);
        assertNotNull(c);
        assertEquals(Constants.IS_EMAIL_CONFIRMED * Constants.IS_DELIVERY, c.getControlsVariable());
    }

    @Test
    void waiterControlsVariableUnchanged() {
        User waiter = user(Role.ROLE_WAITER, true, true);
        when(userRepository.findByUsernameAndDeleted("w", false)).thenReturn(Optional.of(waiter));
        assertEquals(Constants.IS_EMAIL_CONFIRMED * Constants.IS_WAITER, service.login("w", "pw").getData().getControlsVariable());
    }

    // ── Approvazione / elenco / eliminazione ─────────────────────────────────

    @Test
    void ownerConfirmsRiderButNotOtherRolesOrAgencies() {
        User rider = user(Role.ROLE_DELIVERY, true, false);
        when(userRepository.findByIdAndIdAgencyAndDeleted(100L, 7L, false)).thenReturn(Optional.of(rider));
        assertTrue(service.confirmWaiterAdminRegistration(100L));
        assertTrue(rider.isGeneralConfirmed());

        User otherAdmin = user(Role.ROLE_ADMIN, true, false);
        otherAdmin.setId(101L);
        when(userRepository.findByIdAndIdAgencyAndDeleted(101L, 7L, false)).thenReturn(Optional.of(otherAdmin));
        assertThrows(NoSuchElementException.class, () -> service.confirmWaiterAdminRegistration(101L));
        // utente di un altro locale: la query con idAgency non lo trova
        when(userRepository.findByIdAndIdAgencyAndDeleted(200L, 7L, false)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.confirmWaiterAdminRegistration(200L));
    }

    @Test
    void waitersListIncludesRidersWithRoleAndDeleteWorksOnlyOnStaff() {
        UserService users = new UserService(mock(PasswordEncoder.class));
        ReflectionTestUtils.setField(users, "userRepository", userRepository);
        ReflectionTestUtils.setField(users, "authUserProvider", auth);
        User rider = user(Role.ROLE_DELIVERY, true, true);
        User waiter = user(Role.ROLE_WAITER, true, false);
        waiter.setId(102L);
        when(userRepository.findAllByIdAgencyAndDeletedAndRoleIn(eq(7L), eq(false), argThat(c -> c.containsAll(List.of("ROLE_WAITER", "ROLE_DELIVERY")))))
                .thenReturn(List.of(rider, waiter));
        List<WaiterDto> list = users.getAllWaiters();
        assertEquals(List.of("ROLE_DELIVERY", "ROLE_WAITER"), list.stream().map(WaiterDto::getRole).toList());

        when(userRepository.findByIdAndIdAgencyAndDeleted(100L, 7L, false)).thenReturn(Optional.of(rider));
        assertTrue(users.deleteWaiter(100L));
        assertTrue(rider.isDeleted());
        User owner = user(Role.ROLE_ADMIN, true, true);
        owner.setId(42L);
        when(userRepository.findByIdAndIdAgencyAndDeleted(42L, 7L, false)).thenReturn(Optional.of(owner));
        assertThrows(NoSuchElementException.class, () -> users.deleteWaiter(42L));
        assertFalse(owner.isDeleted());
    }

    // ── Permessi ─────────────────────────────────────────────────────────────

    /** Metodi in cui il rider è ammesso: consegne e operazioni sul proprio account. */
    private static final Set<String> RIDER_ALLOWED = Set.of(
            "/api/delivery/slots/day/{date}", "/api/delivery/route-info", "/api/delivery/orders", "/api/delivery/orders/{id}/stage",
            "/api/users/updateData", "/api/users/changePassword", "/api/users/confirmGeneral/{code}");

    @Test
    void riderAllowedOnlyOnDeliveryAndOwnAccountEndpoints() throws Exception {
        List<Class<?>> controllers = controllerClasses();
        assertTrue(controllers.size() > 20, "controller trovati: " + controllers.size());
        Set<String> riderMethods = new TreeSet<>();
        for (Class<?> c : controllers) {
            String base = c.isAnnotationPresent(RequestMapping.class) && c.getAnnotation(RequestMapping.class).value().length > 0
                    ? c.getAnnotation(RequestMapping.class).value()[0] : "";
            PreAuthorize classRule = c.getAnnotation(PreAuthorize.class);
            for (Method m : c.getDeclaredMethods()) {
                String path = mappingPath(m);
                if (path == null) continue;
                String full = base + path;
                PreAuthorize rule = m.isAnnotationPresent(PreAuthorize.class) ? m.getAnnotation(PreAuthorize.class) : classRule;
                if (rule == null) continue; // pubblici, login, webhook, stampanti (token nel path), push (controllo nel service)
                assertFalse(rule.value().contains("isAuthenticated") || rule.value().contains("permitAll"), full);
                if (rule.value().contains("ROLE_DELIVERY")) riderMethods.add(full);
            }
        }
        assertEquals(new TreeSet<>(RIDER_ALLOWED), riderMethods);
    }

    private static String mappingPath(Method m) {
        if (m.isAnnotationPresent(GetMapping.class)) return first(m.getAnnotation(GetMapping.class).value());
        if (m.isAnnotationPresent(PostMapping.class)) return first(m.getAnnotation(PostMapping.class).value());
        if (m.isAnnotationPresent(PutMapping.class)) return first(m.getAnnotation(PutMapping.class).value());
        if (m.isAnnotationPresent(DeleteMapping.class)) return first(m.getAnnotation(DeleteMapping.class).value());
        if (m.isAnnotationPresent(PatchMapping.class)) return first(m.getAnnotation(PatchMapping.class).value());
        return null;
    }

    private static String first(String[] v) {
        return v.length == 0 ? "" : v[0];
    }

    /** Tutte le classe @RestController del main-app (dalle classi compilate). */
    private static List<Class<?>> controllerClasses() throws Exception {
        URL url = RiderRoleTest.class.getClassLoader().getResource("com/modules/mainapp/MainAppApplication.class");
        File modules = new File(url.toURI()).getParentFile().getParentFile(); // .../com/modules
        List<Class<?>> out = new ArrayList<>();
        collect(modules, "com.modules", out);
        return out;
    }

    private static void collect(File dir, String pkg, List<Class<?>> out) throws ClassNotFoundException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, pkg + "." + f.getName(), out);
            else if (f.getName().endsWith("Controller.class") && !f.getName().contains("$")) {
                Class<?> c = Class.forName(pkg + "." + f.getName().replace(".class", ""), false, RiderRoleTest.class.getClassLoader());
                if (c.isAnnotationPresent(RestController.class)) out.add(c);
            }
        }
    }
}
