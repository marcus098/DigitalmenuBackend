package com.modules.takeawaymodule;

import com.modules.takeawaymodule.service.SlotRules;
import com.modules.takeawaymodule.service.SlotRules.Availability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Decisione di disponibilità degli slot asporto: normale / su richiesta / pieno / chiuso / sospeso / passato. */
class SlotRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 18, 0);
    private static final LocalDateTime SLOT = NOW.plusHours(1);

    @Test
    void ordersWithinCapacityAreNormal() {
        // max 5: il 5° ordine è ancora normale
        assertEquals(Availability.AVAILABLE, SlotRules.capacity(5, 10, 5, 0, 2, true));
        assertEquals(Availability.AVAILABLE,
                SlotRules.evaluate(false, false, SLOT, NOW, 4, 10, 2, 5, 0, 2, true));
    }

    @Test
    void ordersInReserveAreOnRequest() {
        assertEquals(Availability.ON_REQUEST, SlotRules.capacity(6, 10, 5, 0, 2, true));
        assertEquals(Availability.ON_REQUEST, SlotRules.capacity(7, 10, 5, 0, 2, true));
        assertEquals(Availability.ON_REQUEST,
                SlotRules.evaluate(false, false, SLOT, NOW, 5, 10, 1, 5, 0, 2, true));
    }

    @Test
    void ordersBeyondReserveAreFull() {
        assertEquals(Availability.FULL, SlotRules.capacity(8, 10, 5, 0, 2, true));
        // senza riserva configurata il primo ordine oltre il massimo è "pieno"
        assertEquals(Availability.FULL, SlotRules.capacity(6, 10, 5, 0, 0, true));
        // riserva configurata ma non consentita (es. prepagamento oltre 6 giorni)
        assertEquals(Availability.FULL, SlotRules.capacity(6, 10, 5, 0, 2, false));
    }

    @Test
    void productLimitIsOptionalExtraConstraint() {
        // 0 = disattivato: nessun limite di prodotti
        assertEquals(Availability.AVAILABLE, SlotRules.capacity(1, 500, 5, 0, 0, true));
        // limite attivo e superato: l'ordine passa in riserva se disponibile, altrimenti pieno
        assertEquals(Availability.ON_REQUEST, SlotRules.capacity(2, 21, 5, 20, 1, true));
        assertEquals(Availability.FULL, SlotRules.capacity(2, 21, 5, 20, 0, true));
        assertEquals(Availability.AVAILABLE, SlotRules.capacity(2, 20, 5, 20, 0, true));
    }

    @Test
    void closedPausedAndPastWinOverCapacity() {
        assertEquals(Availability.PAUSED, SlotRules.evaluate(true, true, SLOT, NOW, 0, 0, 1, 5, 0, 2, true));
        assertEquals(Availability.CLOSED, SlotRules.evaluate(false, true, SLOT, NOW, 0, 0, 1, 5, 0, 2, true));
        assertEquals(Availability.PAST,
                SlotRules.evaluate(false, false, NOW.minusMinutes(1), NOW, 0, 0, 1, 5, 0, 2, true));
    }

    @Test
    void reserveWithPrepaymentOnlyWithinSixDays() {
        LocalDate today = NOW.toLocalDate();
        assertTrue(SlotRules.reserveAllowed(2, true, today.plusDays(6), today));
        assertFalse(SlotRules.reserveAllowed(2, true, today.plusDays(7), today));
        assertTrue(SlotRules.reserveAllowed(2, false, today.plusDays(30), today));
        assertFalse(SlotRules.reserveAllowed(0, false, today, today));
    }
}
