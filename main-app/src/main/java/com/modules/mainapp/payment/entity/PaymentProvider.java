package com.modules.mainapp.payment.entity;

/** Provider di pagamento online scelto dal locale (ognuno incassa sul PROPRIO account). */
public enum PaymentProvider {
    NONE, STRIPE, SUMUP
}
