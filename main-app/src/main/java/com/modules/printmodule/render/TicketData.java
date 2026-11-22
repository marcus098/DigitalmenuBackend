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
        List<Item> items,
        Delivery delivery       // consegna a domicilio (null = blocco DOMICILIO semplice, solo indirizzo)
) {
    public enum OrderKind { TABLE, TAKE_AWAY, HOME }

    public TicketData(String restaurantName, OrderKind orderKind, String tableName, String customerName, String phone,
                      String address, String pickupTime, LocalDateTime createdAt, String shortId, String banner,
                      List<Item> items) {
        this(restaurantName, orderKind, tableName, customerName, phone, address, pickupTime, createdAt, shortId,
                banner, items, null);
    }

    /** variants: una riga ciascuna sotto il prodotto; highlight = in evidenza (es. senza glutine). */
    public record Item(String category, int quantity, String name, String option,
                       List<Variant> variants, List<String> minus, List<String> plus, String note) {
        public Item(String category, int quantity, String name, String option,
                    List<String> minus, List<String> plus, String note) {
            this(category, quantity, name, option, List.of(), minus, plus, note);
        }
    }

    public record Variant(String group, String choice, boolean highlight) {}

    /**
     * Blocco DOMICILIO: citofono, piano, note, distanza e pagamento (l'indirizzo è in {@link #address}).
     * @param paidOnline     pagato online: "PAGATO ONLINE"
     * @param toCollectCents importo da incassare alla consegna (prodotti + consegna)
     * @param method         CASH / CARD per il pagamento alla consegna
     */
    public record Delivery(String intercom, String floor, String notes, Double distanceKm,
                           long feeCents, boolean paidOnline, long toCollectCents, String method) {}
}
