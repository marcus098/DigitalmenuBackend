package com.modules.cardmodule.service;

import com.modules.cardmodule.models.CardJpa;
import com.modules.cardmodule.models.CardMovement;
import com.modules.cardmodule.models.LoyaltySettingsJpa;
import com.modules.cardmodule.repository.CardClaimRepository;
import com.modules.cardmodule.repository.CardMovementRepository;
import com.modules.cardmodule.repository.CardRepository;
import com.modules.cardmodule.repository.LoyaltySettingsRepository;
import com.modules.cardmodule.requests.LoyaltySettingsDto;
import com.modules.ordermodule.request.CheckoutRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CardServiceCheckoutTest {

    private static final long AGENCY = 7L;
    private static final long USER = 3L;

    private CardRepository cardRepository;
    private CardMovementRepository movementRepository;
    private LoyaltySettingsRepository settingsRepository;
    private CardService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CardRepository.class);
        movementRepository = mock(CardMovementRepository.class);
        settingsRepository = mock(LoyaltySettingsRepository.class);
        service = new CardService();
        ReflectionTestUtils.setField(service, "cardRepository", cardRepository);
        ReflectionTestUtils.setField(service, "cardClaimRepository", mock(CardClaimRepository.class));
        ReflectionTestUtils.setField(service, "cardMovementRepository", movementRepository);
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

    private LoyaltySettingsJpa settings(Double eurosPerPoint, Integer stamps) {
        LoyaltySettingsJpa s = new LoyaltySettingsJpa(AGENCY);
        s.setEurosPerPoint(eurosPerPoint);
        s.setStampsForPrize(stamps);
        when(settingsRepository.findById(AGENCY)).thenReturn(Optional.of(s));
        return s;
    }

    private CardService.CheckoutLoyalty checkout(long totalCents, int pointsToUse, boolean redeem, boolean earn) {
        return service.applyCheckout(1L, AGENCY, USER, totalCents, "cmd1", pointsToUse, redeem, earn);
    }

    @Test
    void pointsEarnedOnPaidTotalWithAgencyRateOverridingCard() {
        card(true, 10, 0, 1.0);
        settings(2.0, null); // 1 punto ogni 2 €

        CardService.CheckoutLoyalty r = checkout(4800, 0, false, true);

        assertEquals(24, r.earned());
        assertEquals(34, r.card().getActualValue());
        assertEquals(2.0, r.card().getPriceForPoint());
        assertEquals(1, r.card().getVisitsCount());
        assertEquals(4800, r.card().getTotalSpentCents());
        verify(movementRepository).save(argThat((CardMovement m) ->
                m.getType() == CardMovement.Type.EARN && m.getDelta() == 24 && m.getBalanceAfter() == 34
                        && m.getAmountCents() == 4800 && "cmd1".equals(m.getComandId())));
    }

    @Test
    void pointsUsedAreClaimedBeforeEarningAndClampedToBalance() {
        card(true, 50, 0, 1.0);

        CardService.CheckoutLoyalty r = checkout(1050, 80, false, true);

        assertEquals(50, r.claimed());
        assertEquals(10, r.earned());
        assertEquals(10, r.card().getActualValue());
        verify(movementRepository).save(argThat((CardMovement m) ->
                m.getType() == CardMovement.Type.CLAIM && m.getDelta() == -50 && m.getBalanceAfter() == 0));
    }

    @Test
    void stampPrizeRedeemedUsesAgencyThresholdAndSkipsStampWhenNotEarning() {
        card(false, 8, 10, 0);
        settings(null, 8); // il locale ha abbassato la soglia a 8 visite

        CardService.CheckoutLoyalty r = checkout(1500, 0, true, false);

        assertEquals(8, r.claimed());
        assertEquals(0, r.earned());
        assertEquals(0, r.card().getActualValue());
        assertEquals(8, r.card().getScope());
    }

    @Test
    void stampNotAddedWhenCardAlreadyFull() {
        card(false, 10, 10, 0);

        CardService.CheckoutLoyalty r = checkout(1500, 0, false, true);

        assertEquals(0, r.earned());
        assertEquals(10, r.card().getActualValue());
        assertNotNull(r.note());
    }

    @Test
    void prizeNotRedeemedIfNotEnoughStamps() {
        card(false, 4, 10, 0);

        CardService.CheckoutLoyalty r = checkout(1500, 0, true, true);

        assertEquals(0, r.claimed());
        assertEquals(1, r.earned());
        assertEquals(5, r.card().getActualValue());
    }

    @Test
    void visitBelowMinimumSpendIsNotCountedButStatsAre() {
        card(false, 2, 10, 0);
        settings(null, 10).setMinSpendForVisit(10.0);

        CardService.CheckoutLoyalty r = checkout(950, 0, false, true);

        assertEquals(0, r.earned());
        assertEquals(2, r.card().getActualValue());
        assertTrue(r.note().contains("spesa minima"));
        assertEquals(1, r.card().getVisitsCount());
    }

    @Test
    void secondVisitSameDayNotCountedWhenOneVisitPerDay() {
        CardJpa c = card(false, 2, 10, 0);
        c.setLastVisitAt(OffsetDateTime.now().minusMinutes(30));
        settings(null, 10).setOneVisitPerDay(true);

        CardService.CheckoutLoyalty r = checkout(2000, 0, false, true);

        assertEquals(0, r.earned());
        assertEquals("Visita già conteggiata oggi", r.note());
    }

    @Test
    void yearlyExpiryResetsBalanceAccumulatedBeforeBoundary() {
        CardJpa c = card(true, 120, 0, 1.0);
        LoyaltySettingsJpa s = settings(1.0, null);
        s.setExpiryPolicy("YEARLY");
        s.setExpiryMonth(1);
        s.setExpiryDay(1);
        c.setBalanceSince(OffsetDateTime.now().minusYears(1).minusDays(1));

        CardService.CheckoutLoyalty r = checkout(1000, 0, false, true);

        // I 120 punti dell'anno scorso scadono, restano solo i 10 di questo conto
        assertEquals(10, r.card().getActualValue());
        verify(movementRepository).save(argThat((CardMovement m) -> m.getType() == CardMovement.Type.EXPIRE && m.getDelta() == -120));
        assertNotNull(r.card().getExpiresAt());
    }

    @Test
    void inactivityExpiryOnlyAfterConfiguredMonths() {
        LoyaltySettingsJpa s = new LoyaltySettingsJpa(AGENCY);
        s.setExpiryPolicy("INACTIVITY");
        s.setInactivityMonths(6);
        OffsetDateTime now = OffsetDateTime.now();

        CardJpa recent = new CardJpa("A", true, 30, 0, 1, "u", AGENCY, now.minusYears(2));
        recent.setLastActivityAt(now.minusMonths(5));
        assertFalse(service.applyExpiry(recent, s, now));
        assertEquals(30, recent.getActualValue());

        CardJpa old = new CardJpa("B", true, 30, 0, 1, "u", AGENCY, now.minusYears(2));
        old.setLastActivityAt(now.minusMonths(7));
        assertTrue(service.applyExpiry(old, s, now));
        assertEquals(0, old.getActualValue());
    }

    @Test
    void yearlyBoundaryUsesItalianDayAndHandlesLeapDay() {
        // 1 gennaio 2027 alle 00:30 ora italiana = 31/12 alle 23:30 UTC: il nuovo anno è già iniziato
        OffsetDateTime justAfter = ZonedDateTime.of(2027, 1, 1, 0, 30, 0, 0, CardService.BUSINESS_ZONE).toOffsetDateTime();
        assertEquals(2027, CardService.lastYearlyBoundary(1, 1, justAfter).atZoneSameInstant(CardService.BUSINESS_ZONE).getYear());
        // 29 febbraio negli anni non bisestili → 28 febbraio
        OffsetDateTime mar2027 = ZonedDateTime.of(2027, 3, 1, 12, 0, 0, 0, CardService.BUSINESS_ZONE).toOffsetDateTime();
        assertEquals(28, CardService.lastYearlyBoundary(2, 29, mar2027).atZoneSameInstant(CardService.BUSINESS_ZONE).getDayOfMonth());
    }

    @Test
    void settingsValidation() {
        assertNull(new LoyaltySettingsDto(1.0, null, 10, null, "VISITS", "YEARLY", 1, 1, null, 5.0, true).validate());
        assertNotNull(new LoyaltySettingsDto(1.0, null, 10, null, "BOH", null, null, null, null, null, null).validate());
        assertNotNull(new LoyaltySettingsDto(1.0, null, 10, null, null, "YEARLY", 2, 30, null, null, null).validate());
        assertNotNull(new LoyaltySettingsDto(1.0, null, 10, null, null, "INACTIVITY", null, null, null, null, null).validate());
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
