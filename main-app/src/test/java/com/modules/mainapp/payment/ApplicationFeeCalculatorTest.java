package com.modules.mainapp.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationFeeCalculatorTest {

    @Test
    void zeroBpsMeansNoFee() {
        assertEquals(0, ApplicationFeeCalculator.feeCents(2500, 0));
    }

    @Test
    void feeIsRoundedDownInFavourOfRestaurant() {
        // 1,5% di 12,99 € = 19,485 cent → 19
        assertEquals(19, ApplicationFeeCalculator.feeCents(1299, 150));
        assertEquals(100, ApplicationFeeCalculator.feeCents(10_000, 100));
    }

    @Test
    void feeNeverEqualsWholeAmount() {
        assertEquals(499, ApplicationFeeCalculator.feeCents(500, 10_000));
        assertEquals(0, ApplicationFeeCalculator.feeCents(0, 500));
    }

    @Test
    void effectiveBpsUsesAgencyOverrideAndClamps() {
        assertEquals(200, ApplicationFeeCalculator.effectiveBps(null, 200));
        assertEquals(50, ApplicationFeeCalculator.effectiveBps(50, 200));
        assertEquals(0, ApplicationFeeCalculator.effectiveBps(-10, 200));
        assertEquals(10_000, ApplicationFeeCalculator.effectiveBps(99_999, 200));
    }
}
