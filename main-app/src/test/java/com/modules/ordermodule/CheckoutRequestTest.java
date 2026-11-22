package com.modules.ordermodule;

import com.modules.common.model.ComandCheckout;
import com.modules.ordermodule.request.CheckoutRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutRequestTest {

    @Test
    void metodoAssente_valeNonSpecificato() {
        CheckoutRequest r = new CheckoutRequest(1000, 900, "PCT", 10.0, 0, null, 0, false, true);
        assertNull(r.validate());
        assertEquals("UNSPECIFIED", r.normalizedPaymentMethod());
        assertEquals("UNSPECIFIED", new ComandCheckout().getPaymentMethod());
    }

    @Test
    void metodoValido_siNormalizza_ilResto_siRifiuta() {
        assertEquals("CASH", new CheckoutRequest(1000, 900, "PCT", 10.0, 0, null, 0, false, true, "cash").normalizedPaymentMethod());
        assertNull(new CheckoutRequest(1000, 900, "PCT", 10.0, 0, null, 0, false, true, "card").validate());
        assertNotNull(new CheckoutRequest(1000, 900, "PCT", 10.0, 0, null, 0, false, true, "bitcoin").validate());
    }
}
