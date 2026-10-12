package com.modules.common.utilities;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Fuso orario del business (i locali sono in Italia).
 * <p>
 * Le date/ore delle comande, dei pagamenti, della pausa asporto ecc. sono LocalDateTime "da orologio a muro"
 * serializzate senza offset: devono essere ore italiane. Per questo entrambe le JVM (main-app e webflux)
 * girano in Europe/Rome: {@link #useAsJvmDefault()} è chiamato all'inizio dei main() e i Dockerfile
 * passano anche -Duser.timezone=Europe/Rome / TZ=Europe/Rome.
 */
public final class BusinessTime {

    public static final String ZONE_ID = "Europe/Rome";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private BusinessTime() {}

    /** Imposta Europe/Rome come fuso di default della JVM (da chiamare prima di avviare Spring). */
    public static void useAsJvmDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        System.setProperty("user.timezone", ZONE_ID);
    }
}
