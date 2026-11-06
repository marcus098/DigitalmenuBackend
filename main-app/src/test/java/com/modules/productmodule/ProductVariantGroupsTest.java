package com.modules.productmodule;

import com.modules.common.dto.ProductDto;
import com.modules.common.mapper.ProductMapper;
import com.modules.common.model.IngredientOrderPlus;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.variant.*;
import com.modules.common.utilities.Utilities;
import com.modules.productmodule.model.ProductJpa;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Gruppi di varianti del prodotto (docs/VARIANTI_PRODOTTO.md): validazione, prezzo, scelte, allergeni. */
public class ProductVariantGroupsTest {

    public static final String MARGHERITA_GROUPS = """
            [{"id":"impasto","name":" Impasto ","choices":[
               {"id":"classico","name":"Classico","priceDelta":0,"isDefault":true},
               {"id":"senza-glutine","name":"Senza glutine","priceDelta":2.0,"isDefault":false,
                "removesAllergens":[1],"certifiedFree":false,"dietaryTags":["senza-glutine"],"highlight":true}]},
             {"id":"mozzarella","name":"Mozzarella","choices":[
               {"id":"classica","name":"Classica","priceDelta":0,"isDefault":true},
               {"id":"senza-lattosio","name":"Senza lattosio","priceDelta":1.0,"isDefault":false,
                "dietaryTags":["senza-lattosio"],"highlight":true}]}]
            """;

    static List<VariantGroup> margherita() {
        return VariantGroups.parseRequest(MARGHERITA_GROUPS);
    }

    // ── Validazione ──────────────────────────────────────────────────────────

    @Test
    void validRequestIsNormalizedAndDefaultsApplied() {
        List<VariantGroup> groups = margherita();
        assertEquals(2, groups.size());
        assertEquals("Impasto", groups.get(0).getName());
        VariantChoice gf = groups.get(0).getChoices().get(1);
        assertTrue(gf.isAvailable(), "available assente = disponibile");
        assertEquals(List.of(1), gf.getRemovesAllergens());
        assertTrue(gf.isHighlight());
        assertEquals(List.of(), groups.get(0).getChoices().get(0).getAddsAllergens());
    }

    @Test
    void emptyOrMissingJsonMeansNoGroups() {
        assertEquals(List.of(), VariantGroups.parseRequest(null));
        assertEquals(List.of(), VariantGroups.parseRequest("[]"));
        assertNull(VariantGroups.toJson(List.of()));
    }

    @Test
    void rejectsInvalidGroups() {
        assertInvalid("non json", "JSON");
        assertInvalid("[{\"id\":\"a\",\"name\":\"\",\"choices\":[" + choice("x", true) + "," + choice("y", false) + "]}]", "nome");
        assertInvalid("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("x", true) + "]}]", "almeno 2");
        assertInvalid("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("x", false) + "," + choice("y", false) + "]}]", "esattamente una");
        assertInvalid("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("x", true) + "," + choice("y", true) + "]}]", "esattamente una");
        assertInvalid("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("x", true) + "," + choice("x", false) + "]}]", "ripetuto");
        assertInvalid("[" + group("a") + "," + group("a") + "]", "ripetuto");
        assertInvalid("[{\"id\":\"\",\"name\":\"A\",\"choices\":[" + choice("x", true) + "," + choice("y", false) + "]}]", "id");
        assertInvalid("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("x", true)
                + ",{\"id\":\"y\",\"name\":\"Y\",\"removesAllergens\":[15]}]}]", "allergeni");
        StringBuilder seven = new StringBuilder("[");
        for (int i = 0; i < 7; i++) seven.append(i == 0 ? "" : ",").append(group("g" + i));
        assertInvalid(seven.append("]").toString(), "6 gruppi");
        StringBuilder many = new StringBuilder("[{\"id\":\"a\",\"name\":\"A\",\"choices\":[" + choice("c0", true));
        for (int i = 1; i < 13; i++) many.append(",").append(choice("c" + i, false));
        assertInvalid(many.append("]}]").toString(), "12 scelte");
    }

    private static String choice(String id, boolean def) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id.toUpperCase() + "\",\"isDefault\":" + def + "}";
    }

    private static String group(String id) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id.toUpperCase() + "\",\"choices\":[" + choice("x", true) + "," + choice("y", false) + "]}";
    }

    private static void assertInvalid(String json, String messagePart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> VariantGroups.parseRequest(json));
        assertTrue(e.getMessage().contains(messagePart), "messaggio inatteso: " + e.getMessage());
    }

    @Test
    void jsonRoundTripKeepsIsDefaultNameAndMapperNeverReturnsNull() {
        String json = VariantGroups.toJson(margherita());
        assertTrue(json.contains("\"isDefault\":true"), json);
        assertFalse(json.contains("\"default\""), json);

        ProductMapper mapper = new ProductMapper();
        ReflectionTestUtils.setField(mapper, "utilities", new Utilities());
        ProductJpa product = new ProductJpa(44L, "Margherita", "", "|Normale|7.0|1|;", "", 2L, 3L, "", "", "", true, 1);
        assertEquals(List.of(), mapper.toDto(product).getVariantGroups());
        product.setVariantGroups(json);
        ProductDto dto = mapper.toDto(product);
        assertEquals(2, dto.getVariantGroups().size());
        assertEquals("senza-lattosio", dto.getVariantGroups().get(1).getChoices().get(1).getId());
        assertTrue(dto.getVariantGroups().get(0).getChoices().get(0).isDefault());
    }

    @Test
    void setChoiceAvailable() {
        List<VariantGroup> groups = margherita();
        assertTrue(VariantGroups.setChoiceAvailable(groups, "impasto", "senza-glutine", false));
        assertFalse(groups.get(0).getChoices().get(1).isAvailable());
        assertFalse(VariantGroups.setChoiceAvailable(groups, "impasto", "integrale", false));
        assertFalse(VariantGroups.setChoiceAvailable(groups, "salsa", "senza-glutine", false));
    }

    // ── Ordini ───────────────────────────────────────────────────────────────

    @Test
    void omittedGroupsTakeDefaultAndPriceIncludesDeltasAndExtras() {
        List<SelectedVariant> sel = VariantGroups.resolve("Margherita", margherita(),
                List.of(new VariantSelection("impasto", "senza-glutine")));
        assertEquals(2, sel.size());
        assertEquals("Senza glutine", sel.get(0).getChoiceName());
        assertEquals("Impasto", sel.get(0).getGroupName());
        assertTrue(sel.get(0).isHighlight());
        assertEquals("classica", sel.get(1).getChoiceId());

        List<SelectedVariant> all = VariantGroups.resolve("Margherita", margherita(),
                List.of(new VariantSelection("impasto", "senza-glutine"), new VariantSelection("mozzarella", "senza-lattosio")));
        // Maxi 11,00 + senza glutine 2,00 + senza lattosio 1,00 + bufala 2,50 = 16,50
        long cents = VariantGroups.unitPriceCents(new OptionInProduct("Maxi", 11.0, false), all,
                List.of(new IngredientOrderPlus(9, "Bufala", 2.5)));
        assertEquals(1650, cents);
        assertEquals(700, VariantGroups.unitPriceCents(new OptionInProduct("Normale", 7.0, true), List.of(), null));
    }

    @Test
    void negativeDeltaReducesPriceButNeverBelowZero() {
        SelectedVariant cheaper = new SelectedVariant("g", "G", "c", "C", -1.5, false);
        assertEquals(550, VariantGroups.unitPriceCents(new OptionInProduct("N", 7.0, true), List.of(cheaper), null));
        SelectedVariant huge = new SelectedVariant("g", "G", "c", "C", -20, false);
        assertEquals(0, VariantGroups.unitPriceCents(new OptionInProduct("N", 7.0, true), List.of(huge), null));
    }

    @Test
    void rejectsUnknownOrUnavailableChoice() {
        List<VariantGroup> groups = margherita();
        IllegalArgumentException unknownChoice = assertThrows(IllegalArgumentException.class, () ->
                VariantGroups.resolve("Margherita", groups, List.of(new VariantSelection("impasto", "integrale"))));
        assertTrue(unknownChoice.getMessage().contains("Margherita"));
        assertThrows(IllegalArgumentException.class, () ->
                VariantGroups.resolve("Margherita", groups, List.of(new VariantSelection("salsa", "rossa"))));
        assertThrows(IllegalArgumentException.class, () ->
                VariantGroups.resolve("Margherita", groups, List.of(new VariantSelection("impasto", "classico"),
                        new VariantSelection("impasto", "senza-glutine"))));

        VariantGroups.setChoiceAvailable(groups, "impasto", "senza-glutine", false);
        IllegalArgumentException soldOut = assertThrows(IllegalArgumentException.class, () ->
                VariantGroups.resolve("Margherita", groups, List.of(new VariantSelection("impasto", "senza-glutine"))));
        assertTrue(soldOut.getMessage().contains("esaurito"), soldOut.getMessage());
        // le altre scelte restano ordinabili
        assertEquals("classico", VariantGroups.resolve("Margherita", groups, null).get(0).getChoiceId());
    }

    @Test
    void productWithoutGroupsIgnoresNothingAndReturnsEmpty() {
        assertEquals(List.of(), VariantGroups.resolve("Acqua", List.of(), null));
        assertThrows(IllegalArgumentException.class, () ->
                VariantGroups.resolve("Acqua", List.of(), List.of(new VariantSelection("impasto", "classico"))));
    }

    // ── Allergeni ────────────────────────────────────────────────────────────

    @Test
    void removedAllergenGoesToTracesUnlessCertifiedFree() {
        List<VariantGroup> groups = margherita();
        VariantChoice glutenFree = groups.get(0).getChoices().get(1);
        VariantChoice lactoseFree = groups.get(1).getChoices().get(1);

        // Margherita: glutine (1) e latte (7)
        VariantGroups.EffectiveAllergens e = VariantGroups.effectiveAllergens(List.of(1, 7), List.of(), List.of(glutenFree, lactoseFree));
        assertEquals(List.of(7), e.allergens(), "senza lattosio non toglie il latte");
        assertEquals(List.of(1), e.traces(), "il glutine tolto resta come traccia");

        glutenFree.setCertifiedFree(true);
        e = VariantGroups.effectiveAllergens(List.of(1, 7), List.of(5), List.of(glutenFree));
        assertEquals(List.of(7), e.allergens());
        assertEquals(List.of(5), e.traces());

        VariantChoice withNuts = new VariantChoice("pesto", "Pesto", 1, false);
        withNuts.setAddsAllergens(new ArrayList<>(List.of(8)));
        e = VariantGroups.effectiveAllergens(List.of(1), List.of(8), List.of(withNuts));
        assertEquals(List.of(1, 8), e.allergens());
        assertEquals(List.of(), e.traces(), "un allergene vero non è anche traccia");
    }
}
