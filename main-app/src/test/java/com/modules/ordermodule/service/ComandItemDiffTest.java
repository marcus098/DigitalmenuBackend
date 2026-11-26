package com.modules.ordermodule.service;

import com.modules.common.model.*;
import com.modules.common.model.enums.ComandItemChangeType;
import com.modules.common.model.variant.SelectedVariant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Differenze tra le righe prima/dopo una modifica: confronto per contenuto (le righe non hanno un id stabile). */
class ComandItemDiffTest {

    private static ProductToOrder line(long id, String name, int qty) {
        ProductToOrder p = new ProductToOrder(id, name, 2, "Pizze", new OptionInProduct("Normale", 7.0, true), qty, null);
        p.setUnitPriceCents(700L);
        return p;
    }

    private static ProductToOrder withNote(ProductToOrder p, String note) {
        p.setNote(note);
        return p;
    }

    private static ProductToOrder withVariant(ProductToOrder p, String group, String choice) {
        List<SelectedVariant> v = new ArrayList<>(p.getSelectedVariants());
        v.add(new SelectedVariant(group.toLowerCase(), group, choice.toLowerCase(), choice, 1.0, false));
        p.setSelectedVariants(v);
        return p;
    }

    private static ProductToOrder without(ProductToOrder p, long id, String name) {
        IngredientOrder i = new IngredientOrder();
        i.setId(id);
        i.setName(name);
        List<IngredientOrder> l = new ArrayList<>(p.getIngredientsMinus());
        l.add(i);
        p.setIngredientsMinus(l);
        return p;
    }

    private static List<Order> orders(ProductToOrder... lines) {
        return List.of(new Order("o1", LocalDateTime.now(), LocalDateTime.now(), "c1", "7", List.of(lines)));
    }

    @Test
    void stesseRighe_nessunaDifferenza() {
        assertTrue(ComandItemDiff.diff(orders(line(1, "Margherita", 2), line(2, "Diavola", 1)),
                orders(line(2, "Diavola", 1), line(1, "Margherita", 2))).isEmpty());
    }

    @Test
    void righeUgualiSuPiuGiri_siSommano() {
        List<Order> before = new ArrayList<>(orders(line(1, "Margherita", 1)));
        before.add(new Order("o2", LocalDateTime.now(), LocalDateTime.now(), "c1", "7", List.of(line(1, "Margherita", 1))));
        assertTrue(ComandItemDiff.diff(before, orders(line(1, "Margherita", 2))).isEmpty());
    }

    @Test
    void aggiunta_rimozione_quantita() {
        List<ComandItemChange> d = ComandItemDiff.diff(
                orders(line(1, "Margherita", 2), line(2, "Diavola", 1)),
                orders(line(1, "Margherita", 3), line(3, "Coca", 2)));

        assertEquals(3, d.size());
        ComandItemChange qty = find(d, ComandItemChangeType.QTY_CHANGED);
        assertEquals("Margherita", qty.getProductName());
        assertEquals(2, qty.getOldQty());
        assertEquals(3, qty.getNewQty());
        assertEquals("Normale", qty.getNewDescription());
        ComandItemChange removed = find(d, ComandItemChangeType.REMOVED);
        assertEquals("Diavola", removed.getProductName());
        assertEquals(1, removed.getOldQty());
        assertNull(removed.getNewQty());
        ComandItemChange added = find(d, ComandItemChangeType.ADDED);
        assertEquals("Coca", added.getProductName());
        assertEquals(2, added.getNewQty());
        assertNull(added.getOldQty());
        assertEquals("Pizze", added.getCategoryName());
        assertEquals(2L, added.getIdCategory());
    }

    @Test
    void variantiDiverse_variantChangedConDescrizioni() {
        List<ComandItemChange> d = ComandItemDiff.diff(
                orders(line(1, "Margherita", 1)),
                orders(without(withVariant(line(1, "Margherita", 1), "Impasto", "Integrale"), 9, "Cipolla")));

        assertEquals(1, d.size());
        ComandItemChange c = d.get(0);
        assertEquals(ComandItemChangeType.VARIANT_CHANGED, c.getType());
        assertEquals("Normale", c.getOldDescription());
        assertEquals("Normale, Impasto: Integrale, senza Cipolla", c.getNewDescription());
        assertEquals(1, c.getOldQty());
        assertEquals(1, c.getNewQty());
    }

    @Test
    void soloNota_noteChanged() {
        List<ComandItemChange> d = ComandItemDiff.diff(
                orders(withNote(line(1, "Margherita", 1), "ben cotta")),
                orders(withNote(line(1, "Margherita", 2), "  poco cotta ")));

        assertEquals(1, d.size());
        ComandItemChange c = d.get(0);
        assertEquals(ComandItemChangeType.NOTE_CHANGED, c.getType());
        assertEquals("ben cotta", c.getOldNote());
        assertEquals("poco cotta", c.getNewNote());
        assertEquals(1, c.getOldQty());
        assertEquals(2, c.getNewQty());
    }

    @Test
    void notaVuotaEquivaleANessunaNota() {
        assertTrue(ComandItemDiff.diff(orders(withNote(line(1, "Margherita", 1), " ")),
                orders(line(1, "Margherita", 1))).isEmpty());
    }

    @Test
    void prodottoSostituito_rimossoEAggiunto() {
        List<ComandItemChange> d = ComandItemDiff.diff(orders(line(1, "Margherita", 1)), orders(line(2, "Diavola", 1)));
        assertEquals(2, d.size());
        assertEquals("Margherita", find(d, ComandItemChangeType.REMOVED).getProductName());
        assertEquals("Diavola", find(d, ComandItemChangeType.ADDED).getProductName());
    }

    private static ComandItemChange find(List<ComandItemChange> d, ComandItemChangeType type) {
        return d.stream().filter(c -> c.getType() == type).findFirst().orElseThrow(() -> new AssertionError("manca " + type));
    }
}
