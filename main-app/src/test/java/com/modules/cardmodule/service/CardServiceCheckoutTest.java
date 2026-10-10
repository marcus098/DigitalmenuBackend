package com.modules.cardmodule.service;

import com.modules.cardmodule.models.CardClaim;
import com.modules.cardmodule.models.CardJpa;
import com.modules.cardmodule.models.LoyaltySettingsJpa;
import com.modules.cardmodule.repository.CardClaimRepository;
import com.modules.cardmodule.repository.CardRepository;
import com.modules.cardmodule.repository.LoyaltySettingsRepository;
import com.modules.ordermodule.request.CheckoutRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CardServiceCheckoutTest {

    private static final long AGENCY = 7L;
    private static final long USER = 3L;

    private CardRepository cardRepository;
    private CardClaimRepository claimRepository;
    private LoyaltySettingsRepository settingsRepository;
    private CardService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CardRepository.class);
        claimRepository = mock(CardClaimRepository.class);
        settingsRepository = mock(LoyaltySettingsRepository.class);
        service = new CardService();
        ReflectionTestUtils.setField(service, "cardRepository", cardRepository);
        ReflectionTestUtils.setField(service, "cardClaimRepository", claimRepository);
        ReflectionTestUtils.setField(service, "loyaltySettingsRepository", settingsRepository);
        when(cardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settingsRepository.findById(AGENCY)).thenReturn(Optional.empty());
    }

    private CardJpa card(boolean points, int value, int scope, double priceForPoint) {
        CardJpa c = new CardJpa("ABC", points, value, scope, priceForPoint, "url", AGENCY, OffsetDateTime.now());
        c.setId(1L);
        when(cardRepository.findByIdAndDeletedAndIdAgency(1L, false, AGENCY)).thenReturn(Optional.of(c));
        return c;
    }

    private void settings(Double eurosPerPoint, Integer stamps) {
        LoyaltySettingsJpa s = new LoyaltySettingsJpa(AGENCY);
        s.setEurosPerPoint(eurosPerPoint);
        s.setStampsForPrize(stamps);
        when(settingsRepository.findById(AGENCY)).thenReturn(Optional.of(s));
    }

    @Test
    void pointsEarnedOnPaidTotalWithAgencyRateOverridingCard() {
        card(true, 10, 0, 1.0);
        settings(2.0, null); // 1 punto ogni 2 €

        CardService.CheckoutLoyalty r = service.applyCheckout(1L, AGENCY, USER, 4800, 0, false, true);

        assertEquals(24, r.earned());
        assertEquals(34, r.card().getActualValue());
        assertEquals(2.0, r.card().getPriceForPoint());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void pointsUsedAreClaimedBeforeEarningAndClampedToBalance() {
        card(true, 50, 0, 1.0);

        CardService.CheckoutLoyalty r = service.applyCheckout(1L, AGENCY, USER, 1050, 80, false, true);

        assertEquals(50, r.claimed());
        assertEquals(10, r.earned());
        assertEquals(10, r.card().getActualValue());
        verify(claimRepository).save(argThat((CardClaim c) -> c.getPoints() == 50));
    }

    @Test
    void stampPrizeRedeemedUsesAgencyThresholdAndSkipsStampWhenNotEarning() {
        card(false, 8, 10, 0);
        settings(null, 8); // il locale ha abbassato la soglia a 8 timbri

        CardService.CheckoutLoyalty r = service.applyCheckout(1L, AGENCY, USER, 1500, 0, true, false);

        assertEquals(8, r.claimed());
        assertEquals(0, r.earned());
        assertEquals(0, r.card().getActualValue());
        assertEquals(8, r.card().getScope());
    }

    @Test
    void stampNotAddedWhenCardAlreadyFull() {
        card(false, 10, 10, 0);

        CardService.CheckoutLoyalty r = service.applyCheckout(1L, AGENCY, USER, 1500, 0, false, true);

        assertEquals(0, r.earned());
        assertEquals(10, r.card().getActualValue());
    }

    @Test
    void prizeNotRedeemedIfNotEnoughStamps() {
        card(false, 4, 10, 0);

        CardService.CheckoutLoyalty r = service.applyCheckout(1L, AGENCY, USER, 1500, 0, true, true);

        assertEquals(0, r.claimed());
        assertEquals(1, r.earned());
        assertEquals(5, r.card().getActualValue());
    }

    @Test
    void checkoutRequestValidation() {
        assertNull(new CheckoutRequest(4850, 4800, "FINAL", null, 0, null, 0, false, true).validate());
        assertNull(new CheckoutRequest(4850, 5000, "FINAL", null, 0, null, 0, false, true).validate()); // maggiorazione ammessa
        assertNotNull(new CheckoutRequest(4850, -1, "FINAL", null, 0, null, 0, false, true).validate());
        assertNotNull(new CheckoutRequest(4850, 4800, "BOH", null, 0, null, 0, false, true).validate());
        assertNotNull(new CheckoutRequest(4850, 4800, "PCT", 120.0, 0, null, 0, false, true).validate());
        assertNotNull(new CheckoutRequest(4850, 4800, "PCT", 10.0, 0, null, -5, false, true).validate());
    }
}
