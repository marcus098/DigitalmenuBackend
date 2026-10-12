package com.modules.mainapp;

import com.modules.common.utilities.BusinessTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessTimeTest {

    private TimeZone original;
    private String originalProp;

    @BeforeEach
    void saveDefault() {
        original = TimeZone.getDefault();
        originalProp = System.getProperty("user.timezone");
        // Simula il container in UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(original);
        if (originalProp == null) System.clearProperty("user.timezone");
        else System.setProperty("user.timezone", originalProp);
    }

    @Test
    void useAsJvmDefaultSwitchesToEuropeRome() {
        BusinessTime.useAsJvmDefault();
        assertEquals(ZoneId.of("Europe/Rome"), ZoneId.systemDefault());
        assertEquals("Europe/Rome", System.getProperty("user.timezone"));
    }

    @Test
    void localDateTimeIsItalianWallClockAfterSwitch() {
        BusinessTime.useAsJvmDefault();
        // 22:30 UTC del 12 ottobre (ora legale, +02:00) = 00:30 del 13 ottobre in Italia:
        // è il caso dei totali "di oggi" sbagliati tra mezzanotte e le 2.
        Instant i = Instant.parse("2026-10-12T22:30:00Z");
        LocalDateTime local = LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 30), local);
        // Ora solare (+01:00)
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 30),
                LocalDateTime.ofInstant(Instant.parse("2026-11-30T23:30:00Z"), ZoneId.systemDefault()));
    }
}
