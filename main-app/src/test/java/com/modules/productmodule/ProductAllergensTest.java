package com.modules.productmodule;

import com.modules.common.dto.ProductDto;
import com.modules.common.mapper.ProductMapper;
import com.modules.common.utilities.Utilities;
import com.modules.productmodule.model.ProductJpa;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Allergeni e tracce dichiarati sul prodotto (Reg. UE 1169/2011, id 1..14). */
class ProductAllergensTest {

    private final Utilities utilities = new Utilities();

    @Test
    void normalizeKeepsOnlyValidIdsDedupedAndSorted() {
        List<Integer> input = Arrays.asList(7, 0, -3, 15, 1, 7, null, 14, 1);
        assertEquals(List.of(1, 7, 14), utilities.normalizeAllergens(input));
    }

    @Test
    void normalizeHandlesNullAndEmpty() {
        assertEquals(List.of(), utilities.normalizeAllergens(null));
        assertEquals(List.of(), utilities.normalizeAllergens(List.of()));
        // "0" è il valore usato dal client per dire "nessuna traccia"
        assertEquals(List.of(), utilities.normalizeAllergens(List.of(0)));
    }

    @Test
    void mapperDropsLegacyIngredientCopiesAndExposesTraces() {
        ProductMapper mapper = new ProductMapper();
        ReflectionTestUtils.setField(mapper, "utilities", utilities);

        // Formato legacy: positivi = dichiarati sul prodotto, negativi = copie dagli ingredienti
        ProductJpa product = new ProductJpa(1L, "Tiramisù", "", "", "", 2L, 3L, "", "|3|-7|-1|3|", "", true, 1);
        product.setTraces("|8|5|8|");

        ProductDto dto = mapper.toDto(product);
        assertEquals(List.of(3), dto.getAllergens());
        assertEquals(List.of(5, 8), dto.getTraces());
    }

    @Test
    void mapperHandlesProductsWithoutTracesColumnValue() {
        ProductMapper mapper = new ProductMapper();
        ReflectionTestUtils.setField(mapper, "utilities", utilities);

        // Righe esistenti: colonna traces null dopo ddl-auto=update
        ProductJpa product = new ProductJpa(1L, "Acqua", "", "", "", 2L, 3L, "", "", "", true, 1);
        ProductDto dto = mapper.toDto(product);
        assertEquals(List.of(), dto.getAllergens());
        assertEquals(List.of(), dto.getTraces());
    }
}
