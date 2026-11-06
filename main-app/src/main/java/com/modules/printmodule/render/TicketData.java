package com.modules.printmodule.render;

import java.time.LocalDateTime;
import java.util.List;

/** Dati neutri (già filtrati per categoria) da cui il TicketRenderer produce le righe dello scontrino comanda. */
public record TicketData(
        String restaurantName,
        OrderKind orderKind,
        String tableName,
        String customerName,
        String phone,
        String address,
        String pickupTime,
        LocalDateTime createdAt,
        String shortId,
        String banner,          // es. "RISTAMPA" (null = nessuno)
        List<Item> items
) {
    public enum OrderKind { TABLE, TAKE_AWAY, HOME }

    /** variants: una riga ciascuna sotto il prodotto; highlight = in evidenza (es. senza glutine). */
    public record Item(String category, int quantity, String name, String option,
                       List<Variant> variants, List<String> minus, List<String> plus, String note) {
        public Item(String category, int quantity, String name, String option,
                    List<String> minus, List<String> plus, String note) {
            this(category, quantity, name, option, List.of(), minus, plus, note);
        }
    }

    public record Variant(String group, String choice, boolean highlight) {}
}
