package com.modules.ordermodule.service;

import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.dto.UserDto;
import com.modules.common.finders.CategoryUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandTakenBy;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Comande dello staff: "preso da" impostato dall'utente autenticato; le comande dei clienti non lo hanno. */
class OrderTakenByTest {

    private OrderComandService service;
    private MongoComandRepository comandRepo;
    private UserDto waiter;

    @BeforeEach
    void setUp() {
        service = new OrderComandService();
        ProductUtils productUtils = mock(ProductUtils.class);
        CategoryUtils categoryUtils = mock(CategoryUtils.class);
        IngredientUtils ingredientUtils = mock(IngredientUtils.class);
        comandRepo = mock(MongoComandRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        ReflectionTestUtils.setField(service, "productUtils", productUtils);
        ReflectionTestUtils.setField(service, "categoryUtils", categoryUtils);
        ReflectionTestUtils.setField(service, "ingredientUtils", ingredientUtils);
        ReflectionTestUtils.setField(service, "mongoComandRepository", comandRepo);
        ReflectionTestUtils.setField(service, "mongoComandLogRepository", mock(MongoComandLogRepository.class));
        ReflectionTestUtils.setField(service, "authUserProvider", auth);
        ReflectionTestUtils.setField(service, "takeawaySlotService", mock(TakeawaySlotService.class));
        ReflectionTestUtils.setField(service, "eventPublisher", mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "orderUpdateProducer", mock(OrderUpdateProducer.class));

        ProductDto margherita = new ProductDto(44L, "Margherita", "", List.of(
                new OptionInProduct("Normale", 7.0, true)), "", 2L, List.of(), List.of(), List.of(), true, 1);
        when(productUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(margherita));
        CategoryDto pizze = new CategoryDto();
        pizze.setId(2L);
        pizze.setName("Pizze");
        when(categoryUtils.findAllByIdInAndIdAgencyAndDeleted(any(), anyLong())).thenReturn(List.of(pizze));
        when(ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(any(), any())).thenReturn(List.of());

        waiter = new UserDto();
        waiter.setId(7L);
        waiter.setUsername("mario.sala");
        waiter.setName("Mario");
        waiter.setSurname("Rossi");
        waiter.setIdAgency(3L);
        when(auth.getAuthenticatedUser()).thenAnswer(inv -> waiter);
        when(auth.getAgencyId()).thenReturn(3L);
        when(comandRepo.save(any(Comand.class))).thenAnswer(inv -> {
            Comand c = inv.getArgument(0);
            c.setId("cmd1");
            return c;
        });
    }

    private static AddComandWaiter takeaway() {
        AddProductToOrder p = new AddProductToOrder();
        p.setIdProduct(44);
        p.setQuantity(1);
        AddComandWaiter r = new AddComandWaiter();
        r.setOrders(List.of(new AddComandOrder(List.of(p))));
        r.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
        r.setName("Fatima");
        r.setPhone("3331234567");
        r.setTime("20:00");
        return r;
    }

    private ComandTakenBy savedTakenBy() {
        ArgumentCaptor<Comand> saved = ArgumentCaptor.forClass(Comand.class);
        verify(comandRepo).save(saved.capture());
        return saved.getValue().getTakenBy();
    }

    @Test
    void waiterOrderStoresAuthenticatedStaffName() {
        assertEquals("cmd1", service.addOrderWaiter(takeaway()));
        ComandTakenBy by = savedTakenBy();
        assertNotNull(by);
        assertEquals(7L, by.getId());
        assertEquals("Mario Rossi", by.getName());
    }

    @Test
    void creationIsTheFirstHistoryEntry() {
        service.addOrderWaiter(takeaway());
        ArgumentCaptor<Comand> saved = ArgumentCaptor.forClass(Comand.class);
        verify(comandRepo).save(saved.capture());
        assertEquals(1, saved.getValue().getHistory().size());
        var entry = saved.getValue().getHistory().get(0);
        assertEquals(com.modules.common.model.enums.ComandHistoryAction.CREATED, entry.getAction());
        assertEquals(7L, entry.getBy().getId());
        assertEquals("Mario Rossi", entry.getBy().getName());
        assertEquals(saved.getValue().getStatus(), entry.getTo());
    }

    @Test
    void fallsBackToUsernameWithoutName() {
        waiter.setName(null);
        waiter.setSurname(" ");
        service.addOrderWaiter(takeaway());
        assertEquals("mario.sala", savedTakenBy().getName());
    }
}
