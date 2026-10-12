package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Elenco pagamenti del locale: periodo (giorni inclusi), limite e retrocompatibilità senza parametri. */
class PaymentServiceListTest {

    private final PaymentTestSupport s = new PaymentTestSupport();
    private final AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s.service, "authUserProvider", auth);
        when(auth.getAgencyId()).thenReturn(7L);
    }

    @Test
    void withoutRangeReturnsLatestWithDefaultCap() {
        List<PaymentJpa> latest = List.of(PaymentTestSupport.stripePayment("COMPLETED"));
        when(s.repo.findByIdAgencyOrderByCreatedAtDesc(7L, PageRequest.of(0, PaymentService.DEFAULT_PAYMENTS_LIMIT)))
                .thenReturn(latest);
        assertSame(latest, s.service.getPaymentsForAgency(null, null, null));
    }

    @Test
    void rangeCoversWholeDaysInclusive() {
        LocalDate from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 12);
        s.service.getPaymentsForAgency(from, to, 50);
        verify(s.repo).findByIdAgencyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                7L, from.atStartOfDay(), LocalDate.of(2026, 10, 13).atStartOfDay(), PageRequest.of(0, 50));
        verify(s.repo, never()).findByIdAgencyOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void invalidParametersAreRejected() {
        LocalDate d = LocalDate.of(2026, 10, 12);
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(d, null, null));
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(null, d, null));
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(d, d.minusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(d.minusYears(1).minusDays(1), d, null));
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(d, d, 0));
        assertThrows(IllegalArgumentException.class, () -> s.service.getPaymentsForAgency(d, d, PaymentService.MAX_PAYMENTS_LIMIT + 1));
        verifyNoInteractions(s.repo);
    }
}
