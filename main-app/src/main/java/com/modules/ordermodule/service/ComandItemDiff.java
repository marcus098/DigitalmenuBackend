package com.modules.ordermodule.service;

import com.modules.common.model.ComandItemChange;
import com.modules.common.model.IngredientOrder;
import com.modules.common.model.IngredientOrderPlus;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandItemChangeType;
import com.modules.common.model.variant.SelectedVariant;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Differenze tra le righe di una comanda prima e dopo una modifica (regole pure, senza infrastruttura).
 * <p>
 * Le righe non hanno un id stabile (il client rimanda l'intera lista), quindi si confrontano per contenuto:
 * <ol>
 *   <li>righe identiche (prodotto + formato + varianti + ingredienti + nota) → stessa riga, le quantità si sommano:
 *       se cambiano → QTY_CHANGED;</li>
 *   <li>tra le righe rimaste, stesso prodotto e stesse scelte ma nota diversa → NOTE_CHANGED;</li>
 *   <li>poi stesso prodotto con scelte diverse → VARIANT_CHANGED (in ordine di apparizione);</li>
 *   <li>il resto → REMOVED / ADDED.</li>
 * </ol>
 */
public final class ComandItemDiff {

    private ComandItemDiff() {}

    /** Riga aggregata: un esemplare della riga (nomi, descrizione) + quantità totale. */
    private static final class Line {
        final ProductToOrder sample;
        final String choiceKey;
        final String note;
        int qty;

        Line(ProductToOrder p) {
            this.sample = p;
            this.choiceKey = choiceKey(p);
            this.note = normalizeNote(p.getNote());
            this.qty = Math.max(0, p.getQuantity());
        }

        String fullKey() { return choiceKey + "#" + (note == null ? "" : note); }
    }

    public static List<ComandItemChange> diff(List<Order> before, List<Order> after) {
        Map<String, Line> oldLines = aggregate(before);
        Map<String, Line> newLines = aggregate(after);
        List<ComandItemChange> changes = new ArrayList<>();

        // 1. stessa riga: cambia (forse) solo la quantità
        for (Iterator<Map.Entry<String, Line>> it = oldLines.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Line> e = it.next();
            Line n = newLines.remove(e.getKey());
            if (n == null) continue;
            it.remove();
            if (n.qty != e.getValue().qty) changes.add(pair(ComandItemChangeType.QTY_CHANGED, e.getValue(), n));
        }

        List<Line> oldRest = new ArrayList<>(oldLines.values());
        List<Line> newRest = new ArrayList<>(newLines.values());

        // 2. stesse scelte, nota diversa
        for (Iterator<Line> it = oldRest.iterator(); it.hasNext(); ) {
            Line o = it.next();
            Line n = newRest.stream().filter(x -> x.choiceKey.equals(o.choiceKey)).findFirst().orElse(null);
            if (n == null) continue;
            it.remove();
            newRest.remove(n);
            changes.add(pair(ComandItemChangeType.NOTE_CHANGED, o, n));
        }

        // 3. stesso prodotto, scelte diverse
        for (Iterator<Line> it = oldRest.iterator(); it.hasNext(); ) {
            Line o = it.next();
            Line n = newRest.stream().filter(x -> x.sample.getIdProduct() == o.sample.getIdProduct()).findFirst().orElse(null);
            if (n == null) continue;
            it.remove();
            newRest.remove(n);
            changes.add(pair(ComandItemChangeType.VARIANT_CHANGED, o, n));
        }

        // 4. il resto
        for (Line o : oldRest) {
            ComandItemChange c = base(ComandItemChangeType.REMOVED, o.sample);
            c.setOldQty(o.qty);
            c.setOldDescription(description(o.sample));
            c.setOldNote(o.note);
            changes.add(c);
        }
        for (Line n : newRest) {
            ComandItemChange c = base(ComandItemChangeType.ADDED, n.sample);
            c.setNewQty(n.qty);
            c.setNewDescription(description(n.sample));
            c.setNewNote(n.note);
            changes.add(c);
        }
        return changes;
    }

    /**
     * Descrizione leggibile delle scelte di una riga (senza quantità né nota):
     * "Maxi, Impasto: Integrale, senza Cipolla, + Bufala". null se il prodotto non ha scelte.
     */
    public static String description(ProductToOrder p) {
        List<String> parts = new ArrayList<>();
        if (p.getProductOption() != null && notBlank(p.getProductOption().getName())) {
            parts.add(p.getProductOption().getName().strip());
        }
        if (p.getSelectedVariants() != null) for (SelectedVariant v : p.getSelectedVariants()) {
            if (v == null || !notBlank(v.getChoiceName())) continue;
            parts.add(notBlank(v.getGroupName()) ? v.getGroupName().strip() + ": " + v.getChoiceName().strip() : v.getChoiceName().strip());
        }
        if (p.getIngredientsMinus() != null) for (IngredientOrder i : p.getIngredientsMinus()) {
            if (i != null && notBlank(i.getName())) parts.add("senza " + i.getName().strip());
        }
        if (p.getIngredientsPlus() != null) for (IngredientOrderPlus i : p.getIngredientsPlus()) {
            if (i != null && notBlank(i.getName())) parts.add("+ " + i.getName().strip());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Line> aggregate(List<Order> orders) {
        Map<String, Line> map = new LinkedHashMap<>();
        if (orders == null) return map;
        for (Order o : orders) {
            if (o == null || o.getProducts() == null) continue;
            for (ProductToOrder p : o.getProducts()) {
                if (p == null) continue;
                Line l = new Line(p);
                Line existing = map.get(l.fullKey());
                if (existing != null) existing.qty += l.qty;
                else map.put(l.fullKey(), l);
            }
        }
        return map;
    }

    private static ComandItemChange pair(ComandItemChangeType type, Line o, Line n) {
        ComandItemChange c = base(type, n.sample);
        c.setOldQty(o.qty);
        c.setNewQty(n.qty);
        c.setOldDescription(description(o.sample));
        c.setNewDescription(description(n.sample));
        c.setOldNote(o.note);
        c.setNewNote(n.note);
        return c;
    }

    private static ComandItemChange base(ComandItemChangeType type, ProductToOrder p) {
        return new ComandItemChange(type, p.getIdProduct(), p.getProductName(), p.getIdCategory(), p.getCategoryName());
    }

    /** Identità delle scelte: prodotto, formato, varianti (per gruppo), ingredienti tolti e aggiunti (ordinati). */
    static String choiceKey(ProductToOrder p) {
        String option = p.getProductOption() != null && p.getProductOption().getName() != null
                ? p.getProductOption().getName() : "";
        String variants = p.getSelectedVariants() == null ? "" : p.getSelectedVariants().stream()
                .filter(Objects::nonNull)
                .map(v -> v.getGroupId() + "=" + v.getChoiceId())
                .sorted().collect(Collectors.joining(","));
        String minus = p.getIngredientsMinus() == null ? "" : p.getIngredientsMinus().stream()
                .filter(Objects::nonNull).map(i -> String.valueOf(i.getId())).sorted().collect(Collectors.joining(","));
        String plus = p.getIngredientsPlus() == null ? "" : p.getIngredientsPlus().stream()
                .filter(Objects::nonNull).map(i -> String.valueOf(i.getId())).sorted().collect(Collectors.joining(","));
        return p.getIdProduct() + "|" + option + "|" + variants + "|-" + minus + "|+" + plus;
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
