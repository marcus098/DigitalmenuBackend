package com.modules.ordermodule.request;

import java.util.ArrayList;
import java.util.List;

/**
 * Nuove righe di una comanda esistente (PUT /api/orders/{id}/items): la lista COMPLETA, nello stesso formato di
 * POST /api/orders/insertWaiter ({@code orders: [{products: [...]}]}). Prezzi, nomi e varianti vengono ricalcolati
 * dal catalogo come alla creazione.
 */
public class EditComandItems {
    private List<AddComandOrder> orders = new ArrayList<>();
    /** Versione della comanda letta dal client (campo {@code version}); null = nessun controllo. Diversa → 409. */
    private Long expectedVersion;

    public EditComandItems() {}

    public EditComandItems(List<AddComandOrder> orders, Long expectedVersion) {
        this.orders = orders;
        this.expectedVersion = expectedVersion;
    }

    public List<AddComandOrder> getOrders() { return orders; }
    public void setOrders(List<AddComandOrder> orders) { this.orders = orders == null ? new ArrayList<>() : orders; }
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }

    /** Numero di righe prodotto (0 = ordine vuoto). */
    public int lineCount() {
        if (orders == null) return 0;
        return orders.stream().mapToInt(o -> o == null || o.getProducts() == null ? 0 : o.getProducts().size()).sum();
    }
}
