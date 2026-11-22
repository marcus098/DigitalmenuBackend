package com.modules.deliverymodule;

import com.modules.common.model.ComandDelivery;
import com.modules.deliverymodule.dto.DeliveryZone;
import com.modules.deliverymodule.service.DeliveryOrderService;
import com.modules.deliverymodule.service.DeliveryRules;
import com.modules.deliverymodule.service.DeliverySlotService;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.takeawaymodule.dto.SlotConfigDto;
import com.modules.takeawaymodule.dto.SlotDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Distanza, zone, tariffa, ordine minimo, metodi di pagamento, fasce e transizioni di stage. */
class DeliveryRulesTest {

    // Piazza della Loggia, Brescia
    static final double O_LAT = 45.5398802, O_LNG = 10.2191831;
    static final List<DeliveryZone> ZONES = List.of(
            new DeliveryZone(6, 350, 2000), new DeliveryZone(3, 200, 1500)); // non ordinate di proposito

    @Test
    void haversineMatchesKnownDistances() {
        // Brescia - Milano Duomo ~ 82 km in linea d'aria
        double km = DeliveryRules.haversineKm(O_LAT, O_LNG, 45.4642, 9.1900);
        assertEquals(80.5, km, 2.0);
        assertEquals(0, DeliveryRules.haversineKm(O_LAT, O_LNG, O_LAT, O_LNG), 1e-9);
        // 0.01° di latitudine ~ 1.11 km
        assertEquals(1.112, DeliveryRules.haversineKm(45, 10, 45.01, 10), 0.01);
    }

    @Test
    void zoneIsFirstContainingDistanceSortedByMaxKm() {
        assertEquals(200, DeliveryRules.zoneFor(ZONES, 0.5).orElseThrow().getFeeCents());
        assertEquals(200, DeliveryRules.zoneFor(ZONES, 3.0).orElseThrow().getFeeCents());
        assertEquals(350, DeliveryRules.zoneFor(ZONES, 3.01).orElseThrow().getFeeCents());
        assertTrue(DeliveryRules.zoneFor(ZONES, 6.01).isEmpty());
        assertEquals(6, DeliveryRules.maxKm(ZONES));
    }

    @Test
    void quoteInZoneOneWithFee() {
        DeliveryOrderService.Quote q = DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5323, 10.2133, 2600);
        assertEquals(200, q.feeCents());
        assertEquals(2800, q.totalCents());
        assertTrue(q.distanceKm() > 0.5 && q.distanceKm() < 1.5, "km=" + q.distanceKm());
    }

    @Test
    void quoteZoneTwoMinimumOrder() {
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5700, 10.2400, 1600));
        assertEquals(400, e.getStatus());
        assertTrue(e.getMessage().contains("20,00"), e.getMessage());
        assertTrue(e.getMessage().contains("4,00"), e.getMessage());
        assertEquals(350, DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5700, 10.2400, 2100).feeCents());
    }

    @Test
    void outOfZoneRejected() {
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5100, 10.3200, 9000));
        assertEquals(400, e.getStatus());
        assertTrue(e.getMessage().startsWith("Indirizzo fuori dalla zona di consegna"), e.getMessage());
    }

    @Test
    void freeDeliveryOverThreshold() {
        assertEquals(0, DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5700, 10.2400, 5000).feeCents());
        assertEquals(350, DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, 5000L, 45.5700, 10.2400, 4999).feeCents());
        assertEquals(350, DeliveryOrderService.quote(O_LAT, O_LNG, ZONES, null, 45.5700, 10.2400, 99999).feeCents());
    }

    @Test
    void notConfiguredOriginRejected() {
        assertThrows(OrderRejectedException.class, () -> DeliveryOrderService.quote(null, null, ZONES, null, 45, 10, 100));
        assertThrows(OrderRejectedException.class, () -> DeliveryOrderService.quote(O_LAT, O_LNG, List.of(), null, 45, 10, 100));
    }

    @Test
    void zonesValidatedAndSorted() {
        List<DeliveryZone> z = DeliveryRules.normalizeZones(ZONES);
        assertEquals(3, z.get(0).getMaxKm());
        assertThrows(IllegalArgumentException.class, () -> DeliveryRules.normalizeZones(List.of(new DeliveryZone(0, 100, 0))));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRules.normalizeZones(List.of(new DeliveryZone(3, -1, 0))));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRules.normalizeZones(List.of(new DeliveryZone(3, 1, 0), new DeliveryZone(3, 2, 0))));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRules.normalizeZones(List.of(new DeliveryZone(80, 1, 0))));
    }

    @Test
    void paymentOptionsPerMode() {
        List<String> both = List.of("CARD", "CASH", "bitcoin");
        assertEquals(List.of("ONLINE", "CASH", "CARD"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.CUSTOMER_CHOICE, both, true));
        assertEquals(List.of("CASH", "CARD"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.CUSTOMER_CHOICE, both, false));
        assertEquals(List.of("ONLINE"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.ONLINE_REQUIRED, both, true));
        // online obbligatorio ma pagamenti disattivati: il prepagamento decade
        assertEquals(List.of("CASH", "CARD"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.ONLINE_REQUIRED, both, false));
        assertEquals(List.of("CARD"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.ON_DELIVERY_ONLY, List.of("CARD"), true));
        assertEquals(List.of("CASH"), DeliveryRules.paymentOptions(DeliveryRules.PaymentMode.ON_DELIVERY_ONLY, List.of(), true));
    }

    @Test
    void stageOnlyForward() {
        assertTrue(DeliveryRules.canMove("PREPARING", "OUT_FOR_DELIVERY"));
        assertTrue(DeliveryRules.canMove("PREPARING", "DELIVERED"));
        assertTrue(DeliveryRules.canMove("OUT_FOR_DELIVERY", "DELIVERED"));
        assertTrue(DeliveryRules.canMove(null, "OUT_FOR_DELIVERY"));
        assertFalse(DeliveryRules.canMove("DELIVERED", "OUT_FOR_DELIVERY"));
        assertFalse(DeliveryRules.canMove("OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY"));
        assertFalse(DeliveryRules.canMove("OUT_FOR_DELIVERY", "PREPARING"));
        assertFalse(DeliveryRules.canMove("DELIVERED", "DELIVERED"));
        assertEquals(List.of("PREPARING", "OUT_FOR_DELIVERY"), DeliveryRules.allowedFrom(ComandDelivery.STAGE_DELIVERED));
        assertEquals(List.of("PREPARING"), DeliveryRules.allowedFrom(ComandDelivery.STAGE_OUT_FOR_DELIVERY));
    }

    @Test
    void slotStatusWithLeadAndCapacity() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 19, 0);
        assertEquals(SlotDto.Status.PAST, DeliverySlotService.status(false, now.plusMinutes(15), now, 30, 0, 4));
        assertEquals(SlotDto.Status.AVAILABLE, DeliverySlotService.status(false, now.plusMinutes(30), now, 30, 3, 4));
        assertEquals(SlotDto.Status.FULL, DeliverySlotService.status(false, now.plusMinutes(45), now, 30, 4, 4));
        assertEquals(SlotDto.Status.CLOSED, DeliverySlotService.status(true, now.plusMinutes(45), now, 30, 0, 4));
        List<LocalTime> starts = DeliverySlotService.slotStarts(List.of(new SlotConfigDto.TimeRange("19:00", "20:00")), 15);
        assertEquals(List.of(LocalTime.of(19, 0), LocalTime.of(19, 15), LocalTime.of(19, 30), LocalTime.of(19, 45)), starts);
    }

    @Test
    void euroFormat() {
        assertEquals("€ 23,50", DeliveryRules.euro(2350));
        assertEquals("€ 0,05", DeliveryRules.euro(5));
    }
}
