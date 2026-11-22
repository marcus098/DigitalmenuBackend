package com.modules.webfluxmodule.models;

import com.modules.common.model.ComandDelivery;
import com.modules.common.model.ComandType;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.webfluxmodule.models.db.ComandReactive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista PUBBLICA (non autenticata) di una comanda: niente dati personali
 * (name, phone, address, time), niente clientSessionId/tableSessionId, niente userId/idWaiter.
 * Mantiene i campi letti dal frontend cliente (OrderStatusPage, PaymentPage, HistoryOrdersPage).
 */
public record PublicComandDto(
        String id,
        ComandType type,
        ComandStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long idTable,
        List<Order> orders,
        long totalCents,
        /** true se la comanda è stata pagata online (Stripe). */
        boolean paid,
        /** Ordine "su richiesta": serve l'approvazione del locale. */
        boolean approvalRequired,
        /** Scadenza entro cui il locale deve rispondere (AWAIT_APPROVAL). */
        LocalDateTime approvalDeadline,
        /** Motivo del rifiuto/annullamento (DELETED). */
        String rejectReason,
        /** Importo autorizzato ma non ancora addebitato. */
        boolean paymentAuthorized,
        /** Autorizzazione annullata dopo il rifiuto: nessun addebito. */
        boolean authorizationCanceled,
        /** Pagamento rimborsato integralmente. */
        boolean refunded,
        /** Totale dei prodotti (totalCents = subtotalCents + deliveryFeeCents). */
        long subtotalCents,
        /** Tariffa della consegna a domicilio (0 se non a domicilio). */
        long deliveryFeeCents,
        /** Consegna a domicilio senza indirizzo/telefono (null se non a domicilio). */
        PublicDelivery delivery
) {

    /** Vista pubblica della consegna: niente indirizzo, citofono, note, coordinate. */
    public record PublicDelivery(String stage, String paymentChoice, long feeCents, Double distanceKm,
                                 Boolean cashCollected, String deliveryTime,
                                 LocalDateTime outForDeliveryAt, LocalDateTime deliveredAt) {}


    public static PublicComandDto from(ComandReactive c) {
        List<Order> orders = c.getOrders() == null ? List.of() : c.getOrders().stream()
                // userId = id interno dell'operatore che ha inserito l'ordine: non esposto
                .map(o -> new Order(o.getId(), o.getCreatedAt(), o.getUpdatedAt(), o.getComandId(), null,
                        o.getProducts() == null ? List.of() : o.getProducts()))
                .toList();
        Long idTable = c.getIdTable() != null && c.getIdTable() > 0 ? c.getIdTable() : null;
        long subtotal = totalCents(orders);
        long fee = c.deliveryFeeCents();
        ComandDelivery d = c.getDelivery();
        PublicDelivery pd = d == null ? null : new PublicDelivery(d.getStage(), d.getPaymentChoice(), d.getFeeCents(),
                d.getDistanceKm(), d.getCashCollected(), c.getTime(), d.getOutForDeliveryAt(), d.getDeliveredAt());
        return new PublicComandDto(c.getId(), c.getType(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
                idTable, orders, subtotal + fee, Boolean.TRUE.equals(c.getPaid()),
                Boolean.TRUE.equals(c.getApprovalRequired()), c.getApprovalDeadline(), c.getRejectReason(),
                Boolean.TRUE.equals(c.getPaymentAuthorized()), Boolean.TRUE.equals(c.getAuthorizationCanceled()),
                Boolean.TRUE.equals(c.getRefunded()), subtotal, fee, pd);
    }

    /**
     * Totale dai prezzi salvati nella comanda: usa lo snapshot server-side (unitPriceCents) quando presente,
     * come il calcolo usato per il pagamento; per comande vecchie ricade su opzione + extra.
     */
    private static long totalCents(List<Order> orders) {
        long total = 0;
        for (Order o : orders) {
            for (ProductToOrder p : o.getProducts()) {
                if (p == null) continue;
                if (p.getUnitPriceCents() != null) {
                    total += p.getUnitPriceCents() * Math.max(0, p.getQuantity());
                    continue;
                }
                long unit = p.getProductOption() != null ? Math.round(p.getProductOption().getPrice() * 100d) : 0L;
                if (p.getIngredientsPlus() != null) {
                    unit += p.getIngredientsPlus().stream().mapToLong(i -> Math.round(i.getPrice() * 100d)).sum();
                }
                total += unit * p.getQuantity();
            }
        }
        return total;
    }
}
