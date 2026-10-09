package com.modules.authmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.superadmin.AgencyAdminInfoJpa;
import com.modules.authmodule.model.superadmin.SubscriptionStatus;

import java.time.OffsetDateTime;

/** Regole di accesso legate all'abbonamento del locale: unico punto usato da login, checkToken e pannello superadmin. */
public final class SubscriptionRules {

    private SubscriptionRules() {
    }

    /**
     * true se i membri del locale non possono accedere (login 402):
     * stato SUSPENDED/CANCELLED, oppure (regola storica) non in trial e billingEndAt passato.
     * billingEndAt null = nessuna scadenza.
     */
    public static boolean isBlocked(AgencyJpa agency, AgencyAdminInfoJpa info, OffsetDateTime now) {
        if (info != null && info.getSubscriptionStatus() != null && info.getSubscriptionStatus().blocksAccess()) {
            return true;
        }
        return !agency.isTrial() && agency.getBillingEndAt() != null && agency.getBillingEndAt().isBefore(now);
    }

    /** Stato esplicito impostato dal superadmin, altrimenti derivato dal flag trial del locale. */
    public static SubscriptionStatus effectiveStatus(AgencyJpa agency, AgencyAdminInfoJpa info) {
        if (info != null && info.getSubscriptionStatus() != null) {
            return info.getSubscriptionStatus();
        }
        return agency.isTrial() ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE;
    }
}
