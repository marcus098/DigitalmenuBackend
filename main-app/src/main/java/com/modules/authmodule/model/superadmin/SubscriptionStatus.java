package com.modules.authmodule.model.superadmin;

/** Stato commerciale dell'abbonamento di un locale, gestito dal superadmin. */
public enum SubscriptionStatus {
    TRIAL, ACTIVE, SUSPENDED, CANCELLED;

    /** SUSPENDED / CANCELLED bloccano l'accesso dei membri del locale (login 402) a prescindere da billingEndAt. */
    public boolean blocksAccess() {
        return this == SUSPENDED || this == CANCELLED;
    }
}
