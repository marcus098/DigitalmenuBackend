package com.modules.mainapp.payment.dto;

/** Body di POST /api/payments/{id}/refund. amountCents null = rimborso totale del residuo. */
public class RefundRequest {
    private Long amountCents;

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
}
