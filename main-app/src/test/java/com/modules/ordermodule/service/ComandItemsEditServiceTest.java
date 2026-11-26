package com.modules.ordermodule.service;

import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.dto.UserDto;
import com.modules.common.finders.CategoryUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.*;
import com.modules.common.model.enums.ComandHistoryAction;
import com.modules.common.model.enums.ComandItemChangeType;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.event.ComandItemsEditedEvent;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.ordermodule.request.EditComandItems;
import com.modules.ordermodule.service.ComandPaymentOperations.ItemsEditGuard;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Modifica delle righe di una comanda: prezzi dal catalogo, solo stati aperti, qualsiasi collega del locale,
 * storico con le differenze, niente modifiche su pagamenti online, conflitti di versione → 409.
 */
class ComandItemsEditServiceTest {

    private static final long AGENCY = 3L;

    private ComandItemsEditService service;
    private MongoComandReadRepository reads;
    private MongoTemplate mongo;
    private ApplicationEventPublisher events;
    private OrderUpdateProducer kafka;
    private TakeawaySlotService slots;
    private ComandPaymentOperations payments;
    private UserDto mario;

    /** Stato "in DB" della comanda: l'update condizionale lo sostituisce con una copia aggiornata. */
    private ComandJpa current;
    private long modifiedCount = 1;
    private Update lastUpdate;
    private Query lastQuery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        OrderComandService orders = new OrderComandService();
        ProductUtils productUtils = mock(ProductUtils.class);
        CategoryUtils categoryUtils = mock(CategoryUtils.class);
        IngredientUtils ingredientUtils = mock(IngredientUtils.class);
        kafka = mock(OrderUpdateProducer.class);
        ReflectionTestUtils.setField(orders, "productUtils", productUtils);
        ReflectionTestUtils.setField(orders, "categoryUtils", categoryUtils);
        ReflectionTestUtils.setField(orders, "ingredientUtils", ingredientUtils);
        ReflectionTestUtils.setField(orders, "orderUpdateProducer", kafka);

        ProductDto margherita = new ProductDto(44L, "Margherita", "", List.of(
                new OptionInProduct("Normale", 7.0, true)), "", 2L, List.of(), List.of(), List.of(), true, 1);
        ProductDto diavola = new ProductDto(45L, "Diavola", "", List.of(
                new OptionInProduct("Normale", 8.5, true)), "", 2L, List.of(), List.of(), List.of(), true, 2);
        when(productUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(margherita, diavola));
        CategoryDto pizze = new CategoryDto();
        pizze.setId(2L);
        pizze.setName("Pizze");
        when(categoryUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(pizze));
        when(ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(any(), any())).thenReturn(List.of());

        reads = mock(MongoComandReadRepository.class);
        mongo = mock(MongoTemplate.class);
        events = mock(ApplicationEventPublisher.class);
        slots = mock(TakeawaySlotService.class);
        payments = mock(ComandPaymentOperations.class);
        when(payments.prepareForItemsEdit(any())).thenReturn(ItemsEditGuard.NONE);
        ObjectProvider<ComandPaymentOperations> paymentProvider = mock(ObjectProvider.class);
        when(paymentProvider.getIfAvailable()).thenReturn(payments);

        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        mario = new UserDto();
        mario.setId(7L);
        mario.setUsername("mario.sala");
        mario.setName("Mario");
        mario.setSurname("Rossi");
        mario.setIdAgency(AGENCY);
        when(auth.getAuthenticatedUser()).thenAnswer(inv -> mario);
        when(auth.getAgencyId()).thenReturn(AGENCY);

        service = new ComandItemsEditService(reads, mock(MongoComandLogRepository.class), orders, mongo, auth,
                paymentProvider, events, slots);

        // comanda presa da un COLLEGA (Luca), già in cucina
        current = comand(ComandStatus.PROGRESS, List.of(line(44, "Margherita", 1, 700)));
        current.setTakenBy(new ComandTakenBy(9L, "Luca Bianchi"));
        when(reads.findById("cmd1")).thenAnswer(inv -> Optional.ofNullable(current));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ComandStatusUpdater.COLLECTION))).thenAnswer(inv -> {
            lastQuery = inv.getArgument(0);
            lastUpdate = inv.getArgument(1);
            if (modifiedCount > 0) current = applied(current, lastUpdate);
            return UpdateResult.acknowledged(modifiedCount, modifiedCount, null);
        });
    }

    private static ComandJpa comand(ComandStatus status, List<ProductToOrder> lines) {
        ComandJpa c = new ComandJpa();
        c.setId("cmd1");
        c.setIdAgency(AGENCY);
        c.setStatus(status);
        c.setComandWaiterType(ComandWaiterType.TABLE);
        c.setIdTable(4L);
        c.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        c.setOrders(new ArrayList<>(List.of(new Order("o1", LocalDateTime.now().minusMinutes(10), LocalDateTime.now(),
                "cmd1", "9", new ArrayList<>(lines)))));
        return c;
    }

    private static ProductToOrder line(long id, String name, int qty, long unit) {
        ProductToOrder p = new ProductToOrder(id, name, 2, "Pizze", new OptionInProduct("Normale", unit / 100.0, true), qty, null);
        p.setUnitPriceCents(unit);
        return p;
    }

    /** Copia della comanda con l'update applicato (orders, version, history): basta per i test. */
    @SuppressWarnings("unchecked")
    private static ComandJpa applied(ComandJpa c, Update u) {
        Document set = (Document) u.getUpdateObject().get("$set");
        ComandJpa n = comand(c.getStatus(), List.of());
        n.setComandWaiterType(c.getComandWaiterType());
        n.setTime(c.getTime());
        n.setCreatedAt(c.getCreatedAt());
        n.setTakenBy(c.getTakenBy());
        n.setOrders((List<Order>) set.get("orders"));
        n.setVersion(c.getVersion() + 1);
        List<ComandHistoryEntry> h = new ArrayList<>(c.getHistory());
        h.addAll(HistoryUpdates.pushed(u));
        n.setHistory(h);
        return n;
    }

    private static EditComandItems edit(Long expectedVersion, AddProductToOrder... products) {
        return new EditComandItems(List.of(new AddComandOrder(List.of(products))), expectedVersion);
    }

    private static AddProductToOrder product(long id, int qty) {
        AddProductToOrder p = new AddProductToOrder();
        p.setIdProduct(id);
        p.setQuantity(qty);
        return p;
    }

    private OrderRejectedException rejected(EditComandItems req) {
        return assertThrows(OrderRejectedException.class, () -> service.editItems("cmd1", req));
    }

    @Test
    void collegaModifica_prezziDalCatalogo_storicoEdEventi() {
        ComandItemsEditService.EditResult r = service.editItems("cmd1", edit(0L, product(44, 2), product(45, 1)));

        assertTrue(r.changed());
        assertEquals(2 * 700 + 850, r.totalCents());
        assertEquals(1L, r.comand().getVersion());
        // prezzi ricalcolati dal catalogo, giro esistente conservato
        Order o = r.comand().getOrders().get(0);
        assertEquals("o1", o.getId());
        assertEquals("9", o.getUserId());
        assertEquals(850L, o.getProducts().stream().filter(p -> p.getIdProduct() == 45).findFirst().orElseThrow().getUnitPriceCents());

        // storico: voce EDITED di Mario (non di chi ha preso l'ordine), con le differenze
        List<ComandHistoryEntry> pushed = HistoryUpdates.pushed(lastUpdate);
        assertEquals(1, pushed.size());
        ComandHistoryEntry e = pushed.get(0);
        assertEquals(ComandHistoryAction.EDITED, e.getAction());
        assertEquals(7L, e.getBy().getId());
        assertEquals("Mario Rossi", e.getBy().getName());
        assertEquals(List.of(ComandItemChangeType.QTY_CHANGED, ComandItemChangeType.ADDED),
                e.getChanges().stream().map(ComandItemChange::getType).toList());
        assertEquals(-ComandHistoryEntry.MAX_ENTRIES, ((Number) HistoryUpdates.slice(lastUpdate)).intValue());
        assertEquals("Luca Bianchi", r.comand().getTakenBy().getName()); // display-only, invariato

        // update condizionale: agency, stati aperti, conto aperto, niente pagamenti, versione letta (0 = anche assente)
        String q = lastQuery.getQueryObject().toJson();
        assertTrue(q.contains("\"idAgency\": 3"), q);
        assertTrue(q.contains("\"version\": {\"$in\": [0, null]}"), q);
        assertTrue(q.contains("\"checkout\": {\"$exists\": false}"), q);
        assertTrue(q.contains("\"paid\": {\"$ne\": true}"), q);

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        ComandItemsEditedEvent printed = (ComandItemsEditedEvent) ev.getValue();
        assertEquals("Mario Rossi", printed.editedBy());
        assertEquals(2, printed.changes().size());
        verify(kafka).sendUpdate(eq(AGENCY), argThat((String json) -> json.contains("\"edited\":true") && json.contains("\"version\":1")));
        verify(payments).prepareForItemsEdit("cmd1");
    }

    @Test
    void versioneSuccessiva_controlloSullaVersioneLetta() {
        current.setVersion(4L);
        service.editItems("cmd1", edit(4L, product(44, 3)));
        assertTrue(lastQuery.getQueryObject().toJson().contains("\"version\": 4"));
    }

    @Test
    void expectedVersionDiversa_409SenzaScrivere() {
        current.setVersion(2L);
        OrderRejectedException e = rejected(edit(1L, product(44, 2)));
        assertEquals(409, e.getStatus());
        assertEquals(ComandItemsEditService.CODE_VERSION_CONFLICT, e.getCode());
        verifyNoInteractions(mongo, events, kafka);
    }

    @Test
    void modificaConcorrente_updateNonApplicato_409() {
        modifiedCount = 0;
        OrderRejectedException e = rejected(edit(null, product(44, 2)));
        assertEquals(409, e.getStatus());
        assertEquals(ComandItemsEditService.CODE_VERSION_CONFLICT, e.getCode());
        verifyNoInteractions(events, kafka);
    }

    @Test
    void statiChiusi_409() {
        for (ComandStatus s : List.of(ComandStatus.COMPLETED, ComandStatus.DELETED, ComandStatus.AWAIT_PAYMENT)) {
            current.setStatus(s);
            OrderRejectedException e = rejected(edit(null, product(44, 2)));
            assertEquals(409, e.getStatus(), s.name());
            assertEquals(ComandItemsEditService.CODE_NOT_EDITABLE, e.getCode());
        }
        current.setStatus(ComandStatus.PROGRESS);
        current.setCheckout(new ComandCheckout());
        assertEquals(ComandItemsEditService.CODE_NOT_EDITABLE, rejected(edit(null, product(44, 2))).getCode());
        verifyNoInteractions(mongo);
    }

    @Test
    void statiAperti_modificabili() {
        for (ComandStatus s : ComandItemsEditService.EDITABLE) {
            current = comand(s, List.of(line(44, "Margherita", 1, 700)));
            assertTrue(service.editItems("cmd1", edit(null, product(44, 2))).changed(), s.name());
        }
    }

    @Test
    void ordineVuoto_400() {
        OrderRejectedException e = rejected(new EditComandItems(List.of(new AddComandOrder(List.of())), null));
        assertEquals(400, e.getStatus());
        assertEquals(ComandItemsEditService.CODE_EMPTY, e.getCode());
    }

    @Test
    void comandaDiUnAltroLocale_404() {
        current.setIdAgency(99L);
        assertEquals(404, rejected(edit(null, product(44, 2))).getStatus());
    }

    @Test
    void nessunaDifferenza_nienteScrittura() {
        ComandItemsEditService.EditResult r = service.editItems("cmd1", edit(null, product(44, 1)));
        assertFalse(r.changed());
        assertEquals(700, r.totalCents());
        verifyNoInteractions(mongo, events, kafka);
    }

    @Test
    void pagatoOnline_409() {
        current.setPaid(true);
        OrderRejectedException e = rejected(edit(null, product(44, 2)));
        assertEquals(409, e.getStatus());
        assertEquals(ComandItemsEditService.CODE_PAID_ONLINE, e.getCode());
        assertEquals("Ordine già pagato online: per cambiarlo annullalo e rifallo, o rimborsa dalla cassa", e.getMessage());
        verifyNoInteractions(mongo);
    }

    @Test
    void autorizzatoNonIncassato_409() {
        current.setPaid(true);
        current.setPaymentAuthorized(true);
        assertEquals(ComandItemsEditService.CODE_PAYMENT_AUTHORIZED, rejected(edit(null, product(44, 2))).getCode());
        verifyNoInteractions(mongo);
    }

    @Test
    void statoDeiPagamentiPressoIlProvider() {
        when(payments.prepareForItemsEdit("cmd1")).thenReturn(ItemsEditGuard.CAPTURED);
        assertEquals(ComandItemsEditService.CODE_PAID_ONLINE, rejected(edit(null, product(44, 2))).getCode());
        when(payments.prepareForItemsEdit("cmd1")).thenReturn(ItemsEditGuard.AUTHORIZED);
        assertEquals(ComandItemsEditService.CODE_PAYMENT_AUTHORIZED, rejected(edit(null, product(44, 2))).getCode());
        when(payments.prepareForItemsEdit("cmd1")).thenReturn(ItemsEditGuard.BUSY);
        assertEquals(ComandItemsEditService.CODE_PAYMENT_IN_PROGRESS, rejected(edit(null, product(44, 2))).getCode());
        verifyNoInteractions(mongo);
    }

    @Test
    void asporto_allineaIProdottiDelloSlot() {
        current.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
        current.setTime("20:00");
        service.editItems("cmd1", edit(null, product(44, 3), product(45, 1)));
        verify(slots).adjustProducts(eq(AGENCY), eq("20:00"), any(), eq(3));
    }

    @Test
    void quantitaNonValida_400DalPercorsoDiCreazione() {
        assertEquals(400, rejected(edit(null, product(44, 0))).getStatus());
        verifyNoInteractions(mongo);
    }
}
