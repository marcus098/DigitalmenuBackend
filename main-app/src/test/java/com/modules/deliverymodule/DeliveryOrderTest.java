package com.modules.deliverymodule;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.CategoryUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.*;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.variant.VariantGroups;
import com.modules.common.model.variant.VariantSelection;
import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.UserRepository;
import com.modules.deliverymodule.dto.DeliveryOrderRequest;
import com.modules.deliverymodule.dto.PublicDeliveryInfo;
import com.modules.deliverymodule.otp.OtpSettings;
import com.modules.deliverymodule.repository.DeliverySettingsRepository;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.mainapp.controller.PublicOrderController;
import com.modules.mainapp.request.PublicTakeawayRequest;
import com.modules.ordermodule.service.PrepaymentPolicy;
import org.springframework.mock.web.MockHttpServletRequest;
import com.modules.deliverymodule.model.DeliverySettingsJpa;
import com.modules.deliverymodule.otp.OtpService;
import com.modules.deliverymodule.service.DeliveryOrderService;
import com.modules.deliverymodule.service.DeliverySettingsService;
import com.modules.deliverymodule.service.DeliverySlotService;
import com.modules.mainapp.controller.DeliveryController;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.ordermodule.service.OrderComandService;
import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import com.modules.printmodule.service.TicketFactory;
import com.modules.productmodule.ProductVariantGroupsTest;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.tablemodule.repository.TableEntityRepository;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ordine a domicilio: totali con varianti e tariffa, pagamento, telefono, ticket DOMICILIO, stage del rider. */
class DeliveryOrderTest {

    private DeliveryOrderService service;
    private OrderComandService orderComandService;
    private DeliverySettingsService settingsService;
    private DeliverySlotService slotService;
    private OtpService otpService;
    private MongoComandRepository comandRepo;
    private MongoComandReadRepository readRepo;
    private MongoTemplate mongoTemplate;
    private OrderUpdateProducer producer;
    private DeliverySettingsJpa settings;

    @BeforeEach
    void setUp() {
        orderComandService = new OrderComandService();
        ProductUtils productUtils = mock(ProductUtils.class);
        CategoryUtils categoryUtils = mock(CategoryUtils.class);
        IngredientUtils ingredientUtils = mock(IngredientUtils.class);
        ReflectionTestUtils.setField(orderComandService, "productUtils", productUtils);
        ReflectionTestUtils.setField(orderComandService, "categoryUtils", categoryUtils);
        ReflectionTestUtils.setField(orderComandService, "ingredientUtils", ingredientUtils);
        ReflectionTestUtils.setField(orderComandService, "eventPublisher", mock(ApplicationEventPublisher.class));
        producer = mock(OrderUpdateProducer.class);
        ReflectionTestUtils.setField(orderComandService, "orderUpdateProducer", producer);
        comandRepo = mock(MongoComandRepository.class);
        ReflectionTestUtils.setField(orderComandService, "mongoComandRepository", comandRepo);

        ProductDto margherita = new ProductDto(44L, "Margherita", "", List.of(
                new OptionInProduct("Baby", 5.0, false),
                new OptionInProduct("Normale", 7.0, true),
                new OptionInProduct("Maxi", 11.0, false)), "", 2L, List.of(), List.of(1, 7), List.of(), true, 1);
        margherita.setVariantGroups(VariantGroups.parseRequest(ProductVariantGroupsTest.MARGHERITA_GROUPS));
        when(productUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(margherita));
        CategoryDto pizze = new CategoryDto();
        pizze.setId(2L);
        pizze.setName("Pizze");
        when(categoryUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(pizze));
        when(ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(any(), any())).thenReturn(List.of());

        settings = new DeliverySettingsJpa(3L);
        settings.setEnabled(true);
        settings.setOriginLat(DeliveryRulesTest.O_LAT);
        settings.setOriginLng(DeliveryRulesTest.O_LNG);
        settings.setZones("[{\"maxKm\":3,\"feeCents\":200,\"minOrderCents\":1500},{\"maxKm\":6,\"feeCents\":350,\"minOrderCents\":2000}]");
        settings.setFreeDeliveryOverCents(5000L);
        settings.setPaymentMode("CUSTOMER_CHOICE");
        settings.setOnDeliveryMethods("[\"CASH\",\"CARD\"]");

        settingsService = mock(DeliverySettingsService.class);
        when(settingsService.settings(3L)).thenReturn(settings);
        when(settingsService.onlineAvailable(3L)).thenReturn(true);
        slotService = mock(DeliverySlotService.class);
        when(slotService.reserve(anyLong(), any(), anyString()))
                .thenReturn(new TakeawaySlotService.SlotKey(3, LocalDate.of(2026, 11, 20), java.time.LocalTime.of(20, 15)));
        otpService = mock(OtpService.class);
        when(otpService.enabled()).thenReturn(true);
        when(otpService.isVerified(eq("tok"), anyString())).thenReturn(true);
        readRepo = mock(MongoComandReadRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getAgencyId()).thenReturn(3L);
        when(auth.getUserId()).thenReturn(99L);

        service = new DeliveryOrderService();
        ReflectionTestUtils.setField(service, "settingsService", settingsService);
        ReflectionTestUtils.setField(service, "slotService", slotService);
        ReflectionTestUtils.setField(service, "otpService", otpService);
        ReflectionTestUtils.setField(service, "orderComandService", orderComandService);
        ReflectionTestUtils.setField(service, "mongoComandRepository", comandRepo);
        ReflectionTestUtils.setField(service, "mongoComandReadRepository", readRepo);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "auth", auth);

        when(comandRepo.save(any(Comand.class))).thenAnswer(inv -> {
            Comand c = inv.getArgument(0);
            c.setId("cmd1");
            return c;
        });
    }

    private static DeliveryOrderRequest request(String payment, int qty, double lat, double lng) {
        AddProductToOrder p = new AddProductToOrder();
        p.setIdProduct(44);
        p.setProductOption("Maxi");
        p.setQuantity(qty);
        p.setVariants(List.of(new VariantSelection("impasto", "senza-glutine")));
        DeliveryOrderRequest r = new DeliveryOrderRequest();
        r.setCustomerName("Giulia Bianchi");
        r.setCustomerPhone("333 123 4567");
        r.setPhoneToken("tok");
        DeliveryOrderRequest.Address a = new DeliveryOrderRequest.Address();
        a.setLabel("Viale Stazione 5, Brescia");
        a.setLat(lat);
        a.setLng(lng);
        a.setIntercom("Bianchi");
        a.setFloor("2");
        a.setNotes("cancello verde");
        r.setAddress(a);
        r.setDeliveryTime("2026-11-20T20:15");
        r.setPaymentChoice(payment);
        r.setOrders(List.of(new AddComandOrder(List.of(p))));
        return r;
    }

    // ── Creazione ────────────────────────────────────────────────────────────

    @Test
    void cashOrderTotalsIncludeVariantsAndFee() {
        DeliveryOrderService.Created c = service.create(3, request("CASH", 2, 45.5323, 10.2133));
        // Maxi 11 + senza glutine 2 = 13 € x 2 = 26 €, zona 1: 2 €
        assertEquals(2600, c.subtotalCents());
        assertEquals(200, c.deliveryFeeCents());
        assertEquals(2800, c.totalCents());
        assertEquals(ComandStatus.PENDING, c.status());
        assertFalse(c.paymentRequired());

        ArgumentCaptor<Comand> saved = ArgumentCaptor.forClass(Comand.class);
        verify(comandRepo).save(saved.capture());
        ComandJpaLike cj = ComandJpaLike.of(saved.getValue());
        assertEquals(ComandWaiterType.HOME, cj.type());
        assertEquals("+393331234567", cj.phone());
        assertEquals("2026-11-20T20:15", cj.time());
        ComandDelivery d = saved.getValue().getDelivery();
        assertEquals("PREPARING", d.getStage());
        assertEquals("CASH", d.getPaymentChoice());
        assertEquals(200, d.getFeeCents());
        assertEquals("Bianchi", d.getIntercom());
        assertTrue(d.getDistanceKm() > 0.5 && d.getDistanceKm() < 1.5);
        verify(producer).sendUpdate(eq(3L), contains("\"status\":\"PENDING\""));

        // l'importo del pagamento / cassa usa lo stesso totale (prodotti + consegna)
        when(comandRepo.findById("cmd1")).thenReturn(Optional.of(saved.getValue()));
        assertEquals(2800, orderComandService.computeTotalCents("cmd1"));
    }

    @Test
    void onlineChoiceAwaitsPaymentWithoutNotifyingKitchen() {
        DeliveryOrderService.Created c = service.create(3, request("ONLINE", 2, 45.5323, 10.2133));
        assertEquals(ComandStatus.AWAIT_PAYMENT, c.status());
        assertTrue(c.paymentRequired());
        assertEquals(2800L, c.body().get("totalCents"));
        verifyNoInteractions(producer);
    }

    @Test
    void onlineNotAllowedWhenPaymentsDisabledOrOnDeliveryOnly() {
        when(settingsService.onlineAvailable(3L)).thenReturn(false);
        OrderRejectedException e = assertThrows(OrderRejectedException.class, () -> service.create(3, request("ONLINE", 2, 45.5323, 10.2133)));
        assertEquals(400, e.getStatus());
        assertEquals("Metodo di pagamento non ammesso dal locale", e.getMessage());
        settings.setOnDeliveryMethods("[\"CASH\"]");
        assertThrows(OrderRejectedException.class, () -> service.create(3, request("CARD", 2, 45.5323, 10.2133)));
    }

    @Test
    void unverifiedPhoneIs401() {
        DeliveryOrderRequest r = request("CASH", 2, 45.5323, 10.2133);
        r.setPhoneToken("other");
        assertEquals(401, assertThrows(OrderRejectedException.class, () -> service.create(3, r)).getStatus());
        verify(comandRepo, never()).save(any());
    }

    @Test
    void withoutSmsVerificationPhoneTokenNotRequiredButPhoneValidated() {
        when(otpService.enabled()).thenReturn(false);
        DeliveryOrderRequest r = request("CASH", 2, 45.5323, 10.2133);
        r.setPhoneToken(null);
        assertEquals(ComandStatus.PENDING, service.create(3, r).status());
        ArgumentCaptor<Comand> saved = ArgumentCaptor.forClass(Comand.class);
        verify(comandRepo).save(saved.capture());
        assertEquals("+393331234567", ComandJpaLike.of(saved.getValue()).phone());
        verify(otpService, never()).isVerified(any(), any());

        DeliveryOrderRequest bad = request("CASH", 2, 45.5323, 10.2133);
        bad.setPhoneToken(null);
        bad.setCustomerPhone("123");
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.create(3, bad)).getStatus());
    }

    @Test
    void publicInfoReportsPhoneVerification() {
        DeliverySettingsService real = new DeliverySettingsService();
        DeliverySettingsRepository repo = mock(DeliverySettingsRepository.class);
        settings.setRequirePhoneOtpTakeaway(true);
        when(repo.findByIdAgency(3L)).thenReturn(Optional.of(settings));
        ReflectionTestUtils.setField(real, "repository", repo);
        ReflectionTestUtils.setField(real, "prepaymentPolicy", mock(PrepaymentPolicy.class));
        ReflectionTestUtils.setField(real, "takeawaySlotService", mock(TakeawaySlotService.class));

        // chiave Prelude presente: verifica attiva, opzione asporto del locale riportata
        ReflectionTestUtils.setField(real, "otpSettings", new OtpSettings("key", null, false, "", "", new String[0]));
        PublicDeliveryInfo on = real.publicInfo(3L);
        assertTrue(on.phoneVerification());
        assertTrue(on.requirePhoneOtpTakeaway());
        assertTrue(real.get(3L).getPhoneVerification());

        // nessuna chiave (produzione): verifica non attiva, asporto senza codice
        ReflectionTestUtils.setField(real, "otpSettings", new OtpSettings("", null, false, "https://api.example.com", "framework", new String[0]));
        PublicDeliveryInfo off = real.publicInfo(3L);
        assertFalse(off.phoneVerification());
        assertFalse(off.requirePhoneOtpTakeaway());
        assertFalse(real.get(3L).getPhoneVerification());

        // chiave presente ma app.otp.enabled=false
        ReflectionTestUtils.setField(real, "otpSettings", new OtpSettings("key", null, false, "", "", new String[0], false));
        assertFalse(real.publicInfo(3L).phoneVerification());
    }

    @Test
    void takeawayOtpRequiredOnlyWhenVerificationActive() {
        PublicOrderController controller = new PublicOrderController();
        UserRepository users = mock(UserRepository.class);
        User owner = mock(User.class);
        when(owner.getIdAgency()).thenReturn(3L);
        when(users.findByUsernameAndDeleted("pizzeria", false)).thenReturn(Optional.of(owner));
        OrderComandService orders = mock(OrderComandService.class);
        when(orders.addPublicTakeaway(anyLong(), any(), any(), any(), any()))
                .thenReturn(new OrderComandService.CreationResult("t1", ComandStatus.PENDING));
        settings.setRequirePhoneOtpTakeaway(true);
        ReflectionTestUtils.setField(controller, "orderComandService", orders);
        ReflectionTestUtils.setField(controller, "rateLimiter", new IpRateLimiter());
        ReflectionTestUtils.setField(controller, "userRepository", users);
        ReflectionTestUtils.setField(controller, "deliverySettingsService", settingsService);
        ReflectionTestUtils.setField(controller, "otpService", otpService);

        PublicTakeawayRequest r = new PublicTakeawayRequest();
        r.setCustomerName("Giulia");
        r.setCustomerPhone("3331234567");
        r.setOrders(List.of(new AddComandOrder(List.of(new AddProductToOrder()))));

        assertEquals(401, controller.insertTakeawayOrder("pizzeria", r, new MockHttpServletRequest()).getStatusCode().value());
        when(otpService.enabled()).thenReturn(false);
        assertEquals(200, controller.insertTakeawayOrder("pizzeria", r, new MockHttpServletRequest()).getStatusCode().value());
    }

    @Test
    void outOfZoneAndMinimumRejectedBeforeSlotReservation() {
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.create(3, request("CASH", 2, 45.5100, 10.3200))).getStatus());
        // zona 2 (min 20 €): 1 Maxi senza glutine = 13 €
        OrderRejectedException min = assertThrows(OrderRejectedException.class, () -> service.create(3, request("CASH", 1, 45.5700, 10.2400)));
        assertTrue(min.getMessage().startsWith("Ordine minimo"), min.getMessage());
        verify(slotService, never()).reserve(anyLong(), any(), anyString());
    }

    @Test
    void freeDeliveryOverThreshold() {
        // 4 x 13 = 52 € ≥ 50 €
        DeliveryOrderService.Created c = service.create(3, request("CARD", 4, 45.5700, 10.2400));
        assertEquals(0, c.deliveryFeeCents());
        assertEquals(5200, c.totalCents());
    }

    @Test
    void disabledDeliveryRejected() {
        settings.setEnabled(false);
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.create(3, request("CASH", 2, 45.5323, 10.2133))).getStatus());
    }

    @Test
    void slotReleasedIfSaveFails() {
        when(comandRepo.save(any(Comand.class))).thenThrow(new RuntimeException("mongo down"));
        assertThrows(OrderRejectedException.class, () -> service.create(3, request("CASH", 2, 45.5323, 10.2133)));
        verify(slotService).release(any());
    }

    @Test
    void waiterHomeOrderMapsOntoDeliveryModel() {
        AddComandWaiter w = new AddComandWaiter();
        w.setAddress("Via Milano 12, Brescia");
        w.setLat(45.5323);
        w.setLng(10.2133);
        w.setIntercom("Rossi");
        ComandDelivery d = service.forWaiter(3, w, List.of());
        assertEquals("CASH", d.getPaymentChoice());
        assertEquals("PREPARING", d.getStage());
        assertEquals(200, d.getFeeCents());
        w.setDeliveryFeeCents(0L);
        w.setPaymentChoice("CARD");
        ComandDelivery free = service.forWaiter(3, w, List.of());
        assertEquals(0, free.getFeeCents());
        assertEquals("CARD", free.getPaymentChoice());
        AddComandWaiter noCoords = new AddComandWaiter();
        noCoords.setAddress("Via Roma 1");
        assertEquals(0, service.forWaiter(3, noCoords, List.of()).getFeeCents());
    }

    // ── Ticket ───────────────────────────────────────────────────────────────

    private ComandJpa savedComand(String payment, boolean paid) {
        service.create(3, request(payment, 2, 45.5323, 10.2133));
        ArgumentCaptor<Comand> saved = ArgumentCaptor.forClass(Comand.class);
        verify(comandRepo, atLeastOnce()).save(saved.capture());
        Comand src = saved.getValue();
        ComandJpa c = new ComandJpa();
        c.setId("cmd1");
        c.setIdAgency(3L);
        c.setStatus(ComandStatus.PENDING);
        c.setComandWaiterType(ComandWaiterType.HOME);
        c.setName("Giulia Bianchi");
        c.setPhone("+393331234567");
        c.setAddress(src.getDelivery().getLabel());
        c.setTime("2026-11-20T20:15");
        c.setCreatedAt(LocalDateTime.of(2026, 11, 20, 19, 30));
        c.setOrders(src.getOrders());
        c.setDelivery(src.getDelivery());
        c.setPaid(paid);
        return c;
    }

    private static List<String> render(ComandJpa c, int width) {
        TicketFactory f = new TicketFactory(mock(AgencyRepository.class), mock(TableEntityRepository.class));
        com.modules.printmodule.model.PrinterDoc p = new com.modules.printmodule.model.PrinterDoc();
        p.setCategoryFilter(List.of());
        TicketData t = f.ticket(new TicketFactory.ComandContext(c, "Trattoria", null), p, null).orElseThrow();
        return TicketRenderer.render(t, width);
    }

    @Test
    void ticketHasDeliveryBlockToCollectCash() {
        List<String> lines = render(savedComand("CASH", false), 48);
        assertEquals("H|DOMICILIO", lines.get(2));
        assertTrue(lines.contains("H|ORE 20:15"), lines.toString());
        assertTrue(lines.contains("B|Viale Stazione 5, Brescia"), lines.toString());
        assertTrue(lines.contains("N|Citofono: Bianchi"), lines.toString());
        assertTrue(lines.contains("N|Piano: 2"), lines.toString());
        assertTrue(lines.contains("N|Note consegna: cancello verde"), lines.toString());
        assertTrue(lines.contains("N|Tel: +393331234567"), lines.toString());
        assertTrue(lines.contains("I|DA INCASSARE EUR 28,00 CONTANTI"), lines.toString());
        for (String l : render(savedComand("CASH", false), 32)) assertTrue(TicketRenderer.textOf(l).length() <= 32, l);

        byte[] esc = PrinterEncoder.escPos(lines, 1);
        assertTrue(new String(esc, StandardCharsets.US_ASCII).contains("DA INCASSARE EUR 28,00 CONTANTI"));
        assertTrue(new String(PrinterEncoder.starPrnt(lines, 1), StandardCharsets.US_ASCII).contains("Citofono: Bianchi"));
        assertTrue(new String(PrinterEncoder.plainText(lines, 48, 1), StandardCharsets.US_ASCII).contains("DOMICILIO"));
    }

    @Test
    void ticketPaidOnlineAndPos() {
        assertTrue(render(savedComand("ONLINE", true), 48).contains("I|PAGATO ONLINE"));
        assertTrue(render(savedComand("CARD", false), 48).contains("I|DA INCASSARE EUR 28,00 POS"));
    }

    // ── Rider ────────────────────────────────────────────────────────────────

    private ComandJpa riderComand(String stage, String payment, boolean paid, ComandStatus status) {
        ComandJpa c = new ComandJpa();
        c.setId("r1");
        c.setIdAgency(3L);
        c.setStatus(status);
        c.setComandWaiterType(ComandWaiterType.HOME);
        c.setOrders(List.of());
        c.setPaid(paid);
        ComandDelivery d = new ComandDelivery();
        d.setStage(stage);
        d.setPaymentChoice(payment);
        d.setFeeCents(200);
        c.setDelivery(d);
        when(readRepo.findById("r1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void stageTransitionsPublishEvent() {
        riderComand("PREPARING", "CASH", false, ComandStatus.PROGRESS);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), anyString())).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        ComandDelivery d = service.changeStage("r1", "OUT_FOR_DELIVERY", null);
        assertEquals("OUT_FOR_DELIVERY", d.getStage());
        assertNotNull(d.getOutForDeliveryAt());
        assertEquals(99L, d.getStageBy());
        verify(producer).sendUpdate(eq(3L), contains("\"deliveryStage\":\"OUT_FOR_DELIVERY\""));

        riderComand("OUT_FOR_DELIVERY", "CASH", false, ComandStatus.PROGRESS);
        // da incassare: serve cashCollected
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "DELIVERED", null)).getStatus());
        ComandDelivery done = service.changeStage("r1", "DELIVERED", true);
        assertEquals("DELIVERED", done.getStage());
        assertEquals(Boolean.TRUE, done.getCashCollected());
    }

    @Test
    void stageBackwardsOrInvalidRejected() {
        riderComand("DELIVERED", "CASH", false, ComandStatus.COMPLETED);
        assertEquals(409, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "OUT_FOR_DELIVERY", null)).getStatus());
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "PREPARING", null)).getStatus());
        assertEquals(400, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "FLYING", null)).getStatus());
        riderComand("PREPARING", "ONLINE", false, ComandStatus.AWAIT_PAYMENT);
        assertEquals(409, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "OUT_FOR_DELIVERY", null)).getStatus());
        when(readRepo.findById("missing")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(OrderRejectedException.class, () -> service.changeStage("missing", "DELIVERED", true)).getStatus());
    }

    @Test
    void paidOnlineDeliveredWithoutCashCollected() {
        riderComand("OUT_FOR_DELIVERY", "ONLINE", true, ComandStatus.COMPLETED);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), anyString())).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        ComandDelivery d = service.changeStage("r1", "DELIVERED", null);
        assertNull(d.getCashCollected());
    }

    @Test
    void concurrentStageChangeIs409() {
        riderComand("PREPARING", "CASH", false, ComandStatus.PROGRESS);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), anyString())).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertEquals(409, assertThrows(OrderRejectedException.class, () -> service.changeStage("r1", "OUT_FOR_DELIVERY", null)).getStatus());
    }

    @Test
    void riderEndpointsAllowDeliveryWaiterAndOwner() throws Exception {
        for (String m : List.of("orders", "stage", "slots")) {
            java.lang.reflect.Method method = java.util.Arrays.stream(DeliveryController.class.getMethods())
                    .filter(x -> x.getName().equals(m)).findFirst().orElseThrow();
            String expr = method.getAnnotation(PreAuthorize.class).value();
            assertTrue(expr.contains("ROLE_DELIVERY") && expr.contains("ROLE_WAITER") && expr.contains("ROLE_ADMIN"), m + ": " + expr);
        }
        for (String m : List.of("getSettings", "saveSettings")) {
            java.lang.reflect.Method method = java.util.Arrays.stream(DeliveryController.class.getMethods())
                    .filter(x -> x.getName().equals(m)).findFirst().orElseThrow();
            assertEquals("hasRole('ROLE_ADMIN')", method.getAnnotation(PreAuthorize.class).value());
        }
    }

    /** Accesso ai campi di ComandFromWaiter senza dipendere dalla sottoclasse concreta. */
    private record ComandJpaLike(ComandWaiterType type, String phone, String time) {
        static ComandJpaLike of(Comand c) {
            ComandFromWaiter w = (ComandFromWaiter) c;
            return new ComandJpaLike(w.getComandWaiterType(), w.getPhone(), w.getTime());
        }
    }
}
