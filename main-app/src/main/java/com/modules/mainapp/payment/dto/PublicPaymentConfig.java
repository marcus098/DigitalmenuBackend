package com.modules.mainapp.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** GET /api/public/payments/config/{localname}. Il client mostra "Paga online" solo se enabled = true. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicPaymentConfig(boolean enabled, String provider, String stripePublishableKey,
                                  boolean prepaymentTakeaway, boolean prepaymentTable) {
}
