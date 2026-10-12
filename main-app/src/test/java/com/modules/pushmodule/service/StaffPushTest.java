package com.modules.pushmodule.service;

import com.modules.common.dto.TableDto;
import com.modules.common.dto.UserDto;
import com.modules.common.finders.TableUtils;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.mainapp.controller.PushTokenController;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.pushmodule.model.PushTokenJpa;
import com.modules.pushmodule.repository.PushTokenRepository;
import com.modules.servletconfiguration.model.CustomUserDetails;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Registrazione token, scelta dei destinatari e testi delle notifiche push allo staff. */
class StaffPushTest {

    private static final long AGENCY = 7L;
    private static final String TOKEN = "ExponentPushToken[abcDEF123_-]";

    private PushTokenRepository tokens;
    private AuthenticatedUserProvider auth;
    private PushTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokens = mock(PushTokenRepository.class);
        auth = mock(AuthenticatedUserProvider.class);
        tokenService = new PushTokenService();
        ReflectionTestUtils.setField(tokenService, "tokenRepository", tokens);
        ReflectionTestUtils.setField(tokenService, "auth", auth);
        when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void loggedAs(long userId, String role, long idAgency, Long impersonatedBy) {
        UserDto user = new UserDto(userId, "user" + userId, "u@x.it", role, null, idAgency,
                true, true, null, null, null, "Nome", "Cognome");
        when(auth.getCurrentDetails()).thenReturn(new CustomUserDetails(user, impersonatedBy, impersonatedBy == null ? null : "sa@x.it"));
        when(auth.getAuthenticatedUser()).thenReturn(user);
    }

    // ── Registrazione token ─────────────────────────────────────────────

    @Test
    void rejectsInvalidTokens() {
        loggedAs(1, "ROLE_WAITER", AGENCY, null);
        String[] invalid = {null, "", "abc", "ExponentPushToken[]", "ExponentPushToken[a b]",
                "FooToken[abc]", "ExponentPushToken[abc", "ExponentPushToken[abc]x"};
        for (String bad : invalid) {
            assertThrows(IllegalArgumentException.class, () -> tokenService.register(bad, "android"), String.valueOf(bad));
        }
        verify(tokens, never()).save(any());
        assertDoesNotThrow(() -> PushTokenService.requireValidToken("ExpoPushToken[xyz]"));
    }

    @Test
    void invalidTokenIs400FromController() {
        loggedAs(1, "ROLE_ADMIN", AGENCY, null);
        PushTokenController controller = new PushTokenController();
        ReflectionTestUtils.setField(controller, "service", tokenService);

        ResponseEntity<?> resp = controller.register(new PushTokenController.PushTokenRequest("nope", "ios"));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Token push non valido"), resp.getBody());

        assertEquals(400, controller.unregister(null).getStatusCode().value());

        loggedAs(2, "ROLE_SUPERADMIN", 0, null);
        assertEquals(403, controller.register(new PushTokenController.PushTokenRequest(TOKEN, "ios")).getStatusCode().value());
    }

    @Test
    void waiterRegistersTokenForOwnAgency() {
        loggedAs(5, "ROLE_WAITER", AGENCY, null);
        when(tokens.findByToken(TOKEN)).thenReturn(Optional.empty());

        assertTrue(tokenService.register("  " + TOKEN + " ", " Android "));

        ArgumentCaptor<PushTokenJpa> saved = ArgumentCaptor.forClass(PushTokenJpa.class);
        verify(tokens).save(saved.capture());
        assertEquals(TOKEN, saved.getValue().getToken());
        assertEquals(5L, saved.getValue().getUserId());
        assertEquals(AGENCY, saved.getValue().getIdAgency());
        assertEquals("android", saved.getValue().getPlatform());
        assertNotNull(saved.getValue().getCreatedAt());
        assertNotNull(saved.getValue().getUpdatedAt());
    }

    @Test
    void existingTokenMovesToCurrentUser() {
        loggedAs(9, "ROLE_ADMIN", 8L, null);
        PushTokenJpa existing = new PushTokenJpa();
        existing.setToken(TOKEN);
        existing.setUserId(5);
        existing.setIdAgency(AGENCY);
        when(tokens.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        assertTrue(tokenService.register(TOKEN, "ios"));

        verify(tokens).save(existing);
        assertEquals(9L, existing.getUserId());
        assertEquals(8L, existing.getIdAgency());
    }

    @Test
    void impersonationIsIgnoredAndOtherRolesAreForbidden() {
        loggedAs(1, "ROLE_ADMIN", AGENCY, 99L);
        assertFalse(tokenService.register(TOKEN, "ios"));
        verify(tokens, never()).save(any());

        loggedAs(2, "ROLE_SUPERADMIN", 0, null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> tokenService.register(TOKEN, "ios"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void unregisterDeletesOnlyOwnToken() {
        loggedAs(5, "ROLE_WAITER", AGENCY, null);
        when(tokens.deleteByTokenAndUserId(TOKEN, 5L)).thenReturn(1);
        assertTrue(tokenService.unregister(TOKEN));
        verify(tokens).deleteByTokenAndUserId(TOKEN, 5L);
        assertThrows(IllegalArgumentException.class, () -> tokenService.unregister(" "));
    }

    // ── Testi ───────────────────────────────────────────────────────────

    private static ComandJpa comand(String id, long idAgency, ComandWaiterType type, ComandStatus status, int... quantities) {
        ComandJpa c = new ComandJpa();
        c.setId(id);
        c.setIdAgency(idAgency);
        c.setComandWaiterType(type);
        c.setStatus(status);
        List<ProductToOrder> products = new ArrayList<>();
        for (int q : quantities) {
            ProductToOrder p = new ProductToOrder();
            p.setQuantity(q);
            products.add(p);
        }
        c.setOrders(List.of(new Order("o1", LocalDateTime.now(), LocalDateTime.now(), id, "0", products)));
        return c;
    }

    @Test
    void tableOrderMessage() {
        ComandJpa c = comand("cmd_1", AGENCY, ComandWaiterType.TABLE, ComandStatus.AWAIT, 2, 1);
        c.setIdTable(3L);

        PushContent p = StaffPushMessages.order(c, StaffPushMessages.countArticles(c), "5");
        assertEquals("Nuovo ordine · Tavolo 5", p.title());
        assertEquals("3 articoli", p.body());
        assertEquals("orders", p.channelId());
        assertEquals(Map.of("type", "order", "comandId", "cmd_1"), p.data());

        assertEquals("Nuovo ordine · Tavolo 5", StaffPushMessages.order(c, 1, "Tavolo 5").title());
        assertEquals("1 articolo", StaffPushMessages.order(c, 1, "Tavolo 5").body());
        assertEquals("Nuovo ordine · Tavolo 3", StaffPushMessages.order(c, 1, null).title());
    }

    @Test
    void takeawayAndApprovalMessages() {
        ComandJpa c = comand("cmd_2", AGENCY, ComandWaiterType.TAKE_AWAY, ComandStatus.PENDING, 3);
        c.setName("Mario");
        c.setTime("2026-10-12T20:30");

        PushContent p = StaffPushMessages.order(c, 3, null);
        assertEquals("Nuovo ordine", p.title());
        assertEquals("Asporto · Mario alle 20:30 · 3 articoli", p.body());

        c.setStatus(ComandStatus.AWAIT_APPROVAL);
        c.setApprovalDeadline(LocalDateTime.of(2026, 10, 12, 20, 15));
        c.setPaid(true);
        PushContent a = StaffPushMessages.order(c, 3, null);
        assertEquals("Ordine da approvare", a.title());
        assertEquals("Da approvare entro le 20:15 · Asporto · Mario alle 20:30 · pagato online", a.body());

        ComandJpa home = comand("cmd_3", AGENCY, ComandWaiterType.HOME, ComandStatus.PENDING, 1);
        home.setName("Luca");
        home.setTime("21:00");
        assertEquals("Domicilio · Luca alle 21:00 · 1 articolo", StaffPushMessages.order(home, 1, null).body());
    }

    @Test
    void reservationMessage() {
        ReservationJpa r = new ReservationJpa();
        r.setId(42L);
        r.setIdAgency(AGENCY);
        r.setCustomerName("Rossi");
        r.setPartySize(4);
        r.setReservationDate(LocalDate.of(2026, 10, 10)); // sabato
        r.setReservationTime(LocalTime.of(20, 30));

        PushContent p = StaffPushMessages.reservation(r);
        assertEquals("Nuova prenotazione", p.title());
        assertEquals("Rossi · 4 persone · sab 10/10 20:30", p.body());
        assertEquals("reservations", p.channelId());
        assertEquals(Map.of("type", "reservation", "reservationId", 42L), p.data());
    }

    @Test
    void expoMessageShape() {
        Map<String, Object> m = ExpoPushClient.toExpoMessage(TOKEN,
                new PushContent("T", "B", "orders", Map.of("type", "order", "comandId", "c")));
        assertEquals(TOKEN, m.get("to"));
        assertEquals("T", m.get("title"));
        assertEquals("B", m.get("body"));
        assertEquals("high", m.get("priority"));
        assertEquals("default", m.get("sound"));
        assertEquals("orders", m.get("channelId"));
        assertEquals(Map.of("type", "order", "comandId", "c"), m.get("data"));
    }

    // ── Destinatari / invio ─────────────────────────────────────────────

    private StaffPushDispatcher dispatcher(MongoComandReadRepository reads, TableUtils tables, ExpoPushClient expo) {
        StaffPushDispatcher d = new StaffPushDispatcher();
        ReflectionTestUtils.setField(d, "tokenRepository", tokens);
        ReflectionTestUtils.setField(d, "comandReadRepository", reads);
        ReflectionTestUtils.setField(d, "tableUtils", tables);
        ReflectionTestUtils.setField(d, "expoPushClient", expo);
        return d;
    }

    @Test
    void tableSubmitSendsOneNotificationToAllStaffDevicesOfTheAgency() {
        MongoComandReadRepository reads = mock(MongoComandReadRepository.class);
        TableUtils tables = mock(TableUtils.class);
        ExpoPushClient expo = mock(ExpoPushClient.class);
        ComandJpa a = comand("a", AGENCY, ComandWaiterType.TABLE, ComandStatus.AWAIT, 2);
        a.setIdTable(3L);
        ComandJpa b = comand("b", AGENCY, ComandWaiterType.TABLE, ComandStatus.AWAIT, 1, 1);
        b.setIdTable(3L);
        when(reads.findById("a")).thenReturn(Optional.of(a));
        when(reads.findById("b")).thenReturn(Optional.of(b));
        when(reads.findById("x")).thenReturn(Optional.of(comand("x", 99L, ComandWaiterType.TABLE, ComandStatus.AWAIT, 5)));
        when(reads.findById("d")).thenReturn(Optional.of(comand("d", AGENCY, ComandWaiterType.TABLE, ComandStatus.DELETED, 5)));
        TableDto table = new TableDto();
        table.setName("5");
        when(tables.findByIdAndIdAgencyAndDeleted(3L, AGENCY)).thenReturn(Optional.of(table));
        when(tokens.findStaffTokens(AGENCY)).thenReturn(List.of("ExponentPushToken[1]", "ExponentPushToken[2]"));

        dispatcher(reads, tables, expo).newOrder(AGENCY, List.of("a", "b", "x", "d"));

        ArgumentCaptor<PushContent> content = ArgumentCaptor.forClass(PushContent.class);
        verify(expo, times(1)).send(eq(List.of("ExponentPushToken[1]", "ExponentPushToken[2]")), content.capture());
        assertEquals("Nuovo ordine · Tavolo 5", content.getValue().title());
        assertEquals("4 articoli", content.getValue().body()); // altra agency e annullata escluse
        assertEquals("a", content.getValue().data().get("comandId"));
        verify(tokens).findStaffTokens(AGENCY);
    }

    @Test
    void nothingIsSentWithoutDevicesOrForCanceledOrders() {
        MongoComandReadRepository reads = mock(MongoComandReadRepository.class);
        ExpoPushClient expo = mock(ExpoPushClient.class);
        StaffPushDispatcher d = dispatcher(reads, mock(TableUtils.class), expo);

        when(reads.findById("d")).thenReturn(Optional.of(comand("d", AGENCY, ComandWaiterType.TAKE_AWAY, ComandStatus.DELETED, 1)));
        d.newOrder(AGENCY, List.of("d"));
        verify(tokens, never()).findStaffTokens(anyLong());

        when(reads.findById("t")).thenReturn(Optional.of(comand("t", AGENCY, ComandWaiterType.TAKE_AWAY, ComandStatus.PENDING, 1)));
        when(tokens.findStaffTokens(AGENCY)).thenReturn(List.of());
        d.newOrder(AGENCY, List.of("t"));
        verify(expo, never()).send(any(), any());
    }

    @Test
    void dispatcherErrorsNeverPropagate() {
        MongoComandReadRepository reads = mock(MongoComandReadRepository.class);
        when(reads.findById(any())).thenThrow(new RuntimeException("mongo non raggiungibile"));
        StaffPushDispatcher d = dispatcher(reads, mock(TableUtils.class), mock(ExpoPushClient.class));
        assertDoesNotThrow(() -> d.newOrder(AGENCY, List.of("a")));
    }

    @Test
    void notifierSendsReservationImmediatelyWithoutTransaction() {
        StaffPushDispatcher d = mock(StaffPushDispatcher.class);
        StaffPushNotifier notifier = new StaffPushNotifier();
        ReflectionTestUtils.setField(notifier, "dispatcher", d);

        ReservationJpa r = new ReservationJpa();
        r.setId(1L);
        r.setIdAgency(AGENCY);
        r.setCustomerName("Bianchi");
        r.setPartySize(1);
        notifier.newReservation(r);
        ArgumentCaptor<PushContent> content = ArgumentCaptor.forClass(PushContent.class);
        verify(d).send(eq(AGENCY), content.capture());
        assertEquals("Bianchi · 1 persona", content.getValue().body());

        notifier.newOrder(AGENCY, (String) null);
        verify(d, never()).newOrder(anyLong(), any());
        notifier.newOrder(AGENCY, "cmd_1");
        verify(d).newOrder(AGENCY, List.of("cmd_1"));
    }
}
