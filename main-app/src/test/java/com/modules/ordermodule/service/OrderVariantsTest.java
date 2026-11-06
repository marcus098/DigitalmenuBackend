package com.modules.ordermodule.service;

import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.CategoryUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.variant.VariantGroups;
import com.modules.common.model.variant.VariantSelection;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.productmodule.ProductVariantGroupsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Varianti negli ordini: risoluzione dal DB, prezzo unitario server-side, snapshot in comanda, rifiuto 400. */
class OrderVariantsTest {

    private OrderComandService service;

    @BeforeEach
    void setUp() {
        service = new OrderComandService();
        ProductUtils productUtils = mock(ProductUtils.class);
        CategoryUtils categoryUtils = mock(CategoryUtils.class);
        IngredientUtils ingredientUtils = mock(IngredientUtils.class);
        ReflectionTestUtils.setField(service, "productUtils", productUtils);
        ReflectionTestUtils.setField(service, "categoryUtils", categoryUtils);
        ReflectionTestUtils.setField(service, "ingredientUtils", ingredientUtils);

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

        IngredientDto bufala = new IngredientDto(9L, "Bufala", 2.5, true, true, false, List.of(7));
        when(ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(any(), any())).thenReturn(List.of(bufala));
    }

    private static AddProductToOrder line(String option, VariantSelection... variants) {
        AddProductToOrder p = new AddProductToOrder();
        p.setIdProduct(44);
        p.setProductOption(option);
        p.setQuantity(2);
        p.setVariants(List.of(variants));
        return p;
    }

    @Test
    void priceAndSnapshotComputedServerSide() {
        AddProductToOrder p = line("Maxi", new VariantSelection("impasto", "senza-glutine"),
                new VariantSelection("mozzarella", "senza-lattosio"));
        p.setIngredientsPlus(List.of(9L));
        List<Order> orders = service.orderList(3, 0, List.of(new AddComandOrder(List.of(p))), "c1");

        ProductToOrder pto = orders.get(0).getProducts().get(0);
        assertEquals(1650L, pto.getUnitPriceCents());
        assertEquals("Maxi", pto.getProductOption().getName());
        assertEquals(2, pto.getSelectedVariants().size());
        assertEquals("Senza glutine", pto.getSelectedVariants().get(0).getChoiceName());
        assertEquals(2.0, pto.getSelectedVariants().get(0).getPriceDelta());
        assertTrue(pto.getSelectedVariants().get(0).isHighlight());
        assertEquals("Mozzarella", pto.getSelectedVariants().get(1).getGroupName());
    }

    @Test
    void omittedVariantsUseDefaults() {
        List<Order> orders = service.orderList(3, 0, List.of(new AddComandOrder(List.of(line(null)))), "c1");
        ProductToOrder pto = orders.get(0).getProducts().get(0);
        assertEquals(700L, pto.getUnitPriceCents());
        assertEquals(List.of("classico", "classica"), pto.getSelectedVariants().stream().map(v -> v.getChoiceId()).toList());
    }

    @Test
    void unknownOrUnavailableChoiceIsRejectedWith400() {
        OrderRejectedException unknown = assertThrows(OrderRejectedException.class, () -> service.orderList(3, 0,
                List.of(new AddComandOrder(List.of(line("Normale", new VariantSelection("impasto", "integrale"))))), "c1"));
        assertEquals(400, unknown.getStatus());

        ProductDto margherita = ((ProductUtils) ReflectionTestUtils.getField(service, "productUtils"))
                .findAllByIdInAndIdAgencyAndDeleted(null, 3).get(0);
        VariantGroups.setChoiceAvailable(margherita.getVariantGroups(), "impasto", "senza-glutine", false);
        OrderRejectedException soldOut = assertThrows(OrderRejectedException.class, () -> service.orderList(3, 0,
                List.of(new AddComandOrder(List.of(line("Normale", new VariantSelection("impasto", "senza-glutine"))))), "c1"));
        assertEquals(400, soldOut.getStatus());
        assertTrue(soldOut.getMessage().contains("Senza glutine"), soldOut.getMessage());
    }
}
