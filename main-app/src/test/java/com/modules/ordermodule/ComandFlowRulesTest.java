package com.modules.ordermodule;

import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.service.ComandFlowRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ComandFlowRulesTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 9, 19, 0);

    @Test
    void approvalDeadlineIsTenMinutesWhenSlotIsFarAway() {
        assertEquals(T0.plusMinutes(10), ComandFlowRules.approvalDeadline(T0, T0.plusHours(2)));
        assertEquals(T0.plusMinutes(10), ComandFlowRules.approvalDeadline(T0, null));
    }

    @Test
    void approvalDeadlineIsSlotStartWhenSooner() {
        assertEquals(T0.plusMinutes(4), ComandFlowRules.approvalDeadline(T0, T0.plusMinutes(4)));
        // slot già iniziato: scadenza immediata (il job rifiuta al primo giro)
        assertEquals(T0.minusMinutes(1), ComandFlowRules.approvalDeadline(T0, T0.minusMinutes(1)));
    }

    @Test
    void statusAfterPaymentDependsOnChannelAndReserve() {
        assertEquals(ComandStatus.PENDING, ComandFlowRules.statusAfterPayment(ComandWaiterType.TAKE_AWAY, null));
        assertEquals(ComandStatus.AWAIT, ComandFlowRules.statusAfterPayment(ComandWaiterType.TABLE, false));
        assertEquals(ComandStatus.AWAIT_APPROVAL, ComandFlowRules.statusAfterPayment(ComandWaiterType.TAKE_AWAY, true));
    }

    @Test
    void paymentWindowIsFifteenMinutes() {
        assertFalse(ComandFlowRules.isPaymentExpired(T0, T0.plusMinutes(14)));
        assertTrue(ComandFlowRules.isPaymentExpired(T0, T0.plusMinutes(15)));
        assertFalse(ComandFlowRules.isPaymentExpired(null, T0));
    }
}
