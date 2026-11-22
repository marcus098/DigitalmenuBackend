package com.modules.takeawaymodule;

import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.takeawaymodule.model.TakeawaySlotConfigJpa;
import com.modules.takeawaymodule.model.TakeawaySlotCounter;
import com.modules.takeawaymodule.repository.TakeawayClosureRepository;
import com.modules.takeawaymodule.repository.TakeawaySlotConfigRepository;
import com.modules.takeawaymodule.repository.TakeawaySlotOverrideRepository;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contatore slot asporto disallineato (es. 8/5 con un solo ordine attivo): la prenotazione lo riallinea alle comande
 * attive invece di rispondere 409 "non ha più capacità sufficiente". Il contatore Mongo è simulato in memoria.
 */
class TakeawaySlotCounterResyncTest {

    private static final long AGENCY = 3L;

    private MongoTemplate mongo;
    private TakeawaySlotService service;
    private LocalDate day;
    private List<ComandJpa> liveComands;

    /** Stato del documento contatore 3|day|19:30. */
    private int counterOrders;
    private int counterProducts;
    private Date counterUpdatedAt;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        TakeawaySlotConfigRepository configRepo = mock(TakeawaySlotConfigRepository.class);
        TakeawaySlotOverrideRepository overrideRepo = mock(TakeawaySlotOverrideRepository.class);
        TakeawayClosureRepository closureRepo = mock(TakeawayClosureRepository.class);
        service = new TakeawaySlotService();
        ReflectionTestUtils.setField(service, "configRepo", configRepo);
        ReflectionTestUtils.setField(service, "overrideRepo", overrideRepo);
        ReflectionTestUtils.setField(service, "closureRepo", closureRepo);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);

        day = LocalDate.now().plusDays(1);
        TakeawaySlotConfigJpa cfg = new TakeawaySlotConfigJpa(AGENCY);
        cfg.setSlotDurationMinutes(15);
        cfg.setMaxOrdersPerSlot(5);
        cfg.setMaxProductsPerSlot(0);
        cfg.setReserveOrdersPerSlot(0);
        cfg.setWeeklyHours("{\"" + day.getDayOfWeek().name() + "\":[{\"start\":\"19:00\",\"end\":\"23:00\"}]}");
        when(configRepo.findByIdAgency(AGENCY)).thenReturn(Optional.of(cfg));
        when(overrideRepo.findByIdAgencyAndSlotDate(anyLong(), any())).thenReturn(List.of());
        when(closureRepo.findByIdAgencyAndFromDateLessThanEqualAndToDateGreaterThanEqual(anyLong(), any(), any()))
                .thenReturn(List.of());

        liveComands = new ArrayList<>(List.of(takeaway(day + "T19:30", 2)));
        when(mongo.find(any(Query.class), eq(ComandJpa.class))).thenAnswer(inv -> liveComands);
        when(mongo.exists(any(Query.class), eq(TakeawaySlotCounter.class))).thenReturn(true);

        // riallineamento: applica il $set solo se la condizione "nessuna modifica recente" è soddisfatta
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(TakeawaySlotCounter.class), eq(TakeawaySlotService.COUNTERS)))
                .thenAnswer(inv -> {
                    Query q = inv.getArgument(0);
                    Update u = inv.getArgument(1);
                    Document set = (Document) u.getUpdateObject().get("$set");
                    int o = (Integer) set.get("orders");
                    int p = (Integer) set.get("products");
                    boolean differs = o != counterOrders || p != counterProducts;
                    boolean quiet = counterUpdatedAt == null || counterUpdatedAt.before(quietSince(q));
                    if (differs && quiet) {
                        counterOrders = o;
                        counterProducts = p;
                        return UpdateResult.acknowledged(1, 1L, null);
                    }
                    return UpdateResult.acknowledged(0, 0L, null);
                });
        // prenotazione atomica: orders < limite
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(TakeawaySlotCounter.class)))
                .thenAnswer(inv -> {
                    Query q = inv.getArgument(0);
                    int lt = (Integer) ((Document) q.getQueryObject().get("orders")).get("$lt");
                    if (counterOrders >= lt) return null;
                    counterOrders++;
                    counterUpdatedAt = new Date();
                    return new TakeawaySlotCounter();
                });
    }

    /** Estrae la soglia "updatedAt < X" dalla query di riallineamento. */
    @SuppressWarnings("unchecked")
    private static Date quietSince(Query q) {
        List<Document> and = (List<Document>) q.getQueryObject().get("$and");
        for (Document d : and) {
            Object or = d.get("$or");
            if (!(or instanceof List<?> list)) continue;
            for (Object o : list) {
                Object upd = ((Document) o).get("updatedAt");
                if (upd instanceof Document ud && ud.get("$lt") instanceof Date date) return date;
            }
        }
        fail("La query di riallineamento deve escludere i contatori modificati di recente");
        return null;
    }

    private static ComandJpa takeaway(String time, int qty) {
        ComandJpa c = new ComandJpa();
        c.setIdAgency(AGENCY);
        c.setTime(time);
        c.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
        c.setStatus(ComandStatus.PENDING);
        c.setCreatedAt(LocalDateTime.now());
        ProductToOrder p = new ProductToOrder();
        p.setQuantity(qty);
        Order o = new Order();
        o.setProducts(new ArrayList<>(List.of(p)));
        c.setOrders(new ArrayList<>(List.of(o)));
        return c;
    }

    @Test
    void contatoreGonfiatoDaOrdiniAnnullati_vieneRiallineatoEOrdineAccettato() {
        counterOrders = 8;           // 7 ordini annullati mai rilasciati + 1 attivo
        counterProducts = 27;
        counterUpdatedAt = Date.from(Instant.now().minus(Duration.ofMinutes(20)));

        TakeawaySlotService.SlotReservation r = service.reservePublicSlot(AGENCY, day + "T19:30", 1, false);

        assertFalse(r.onRequest());
        assertEquals(2, counterOrders, "1 ordine attivo + quello appena prenotato");
    }

    @Test
    void contatoreLegacySenzaUpdatedAt_vieneRiallineato() {
        counterOrders = 8;
        counterProducts = 27;
        counterUpdatedAt = null;

        service.reservePublicSlot(AGENCY, day + "T19:30", 1, false);

        assertEquals(2, counterOrders);
    }

    @Test
    void contatoreModificatoDaPoco_nonVieneToccato_prenotazioneInVoloProtetta() {
        // una prenotazione appena fatta (comanda non ancora salvata) non è nel conteggio live: niente riallineamento
        counterOrders = 5;
        counterProducts = 2;
        counterUpdatedAt = new Date();

        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> service.reservePublicSlot(AGENCY, day + "T19:30", 1, false));
        assertEquals(409, e.getStatus());
        assertEquals(5, counterOrders);
    }

    @Test
    void slotDavveroPieno_restaPieno() {
        liveComands.clear();
        for (int i = 0; i < 5; i++) liveComands.add(takeaway(day + "T19:30", 1));
        counterOrders = 5;
        counterProducts = 5;
        counterUpdatedAt = null;

        assertThrows(OrderRejectedException.class, () -> service.reservePublicSlot(AGENCY, day + "T19:30", 1, false));
        assertEquals(5, counterOrders);
    }
}
