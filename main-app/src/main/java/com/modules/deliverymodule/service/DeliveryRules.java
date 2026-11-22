package com.modules.deliverymodule.service;

import com.modules.common.model.ComandDelivery;
import com.modules.deliverymodule.dto.DeliveryZone;

import java.util.*;

/**
 * Regole pure (senza infrastruttura) della consegna a domicilio: distanza, zona, tariffa, ordine minimo,
 * metodi di pagamento ammessi e transizioni di stage. Vedi docs/DOMICILIO.md.
 */
public final class DeliveryRules {

    public enum PaymentMode { ONLINE_REQUIRED, CUSTOMER_CHOICE, ON_DELIVERY_ONLY }

    public static final int MAX_ZONES = 10;
    public static final double MAX_ZONE_KM = 50;
    public static final long MAX_FEE_CENTS = 10_000;
    public static final long MAX_MIN_ORDER_CENTS = 100_000;

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private DeliveryRules() {}

    /** Distanza in linea d'aria (haversine) in km. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double round2(double km) {
        return Math.round(km * 100d) / 100d;
    }

    public static boolean validCoordinates(Double lat, Double lng) {
        return lat != null && lng != null && !lat.isNaN() && !lng.isNaN()
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** Prima zona (ordinate per maxKm) che contiene la distanza; empty = fuori zona. */
    public static Optional<DeliveryZone> zoneFor(List<DeliveryZone> zones, double distanceKm) {
        if (zones == null) return Optional.empty();
        return zones.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(DeliveryZone::getMaxKm))
                .filter(z -> distanceKm <= z.getMaxKm())
                .findFirst();
    }

    public static double maxKm(List<DeliveryZone> zones) {
        return zones == null ? 0 : zones.stream().filter(Objects::nonNull).mapToDouble(DeliveryZone::getMaxKm).max().orElse(0);
    }

    /** Tariffa della zona, 0 se il subtotale raggiunge la soglia "consegna gratis" (null/≤0 = nessuna soglia). */
    public static long fee(DeliveryZone zone, long subtotalCents, Long freeDeliveryOverCents) {
        if (freeDeliveryOverCents != null && freeDeliveryOverCents > 0 && subtotalCents >= freeDeliveryOverCents) return 0;
        return Math.max(0, zone.getFeeCents());
    }

    /**
     * Metodi che il cliente può scegliere ora. Se il locale vuole il pagamento online ma i pagamenti online non sono
     * attivi, il prepagamento decade (come per l'asporto) e restano i metodi alla consegna (CASH se nessuno).
     */
    public static List<String> paymentOptions(PaymentMode mode, Collection<String> onDeliveryMethods, boolean onlineAvailable) {
        List<String> onDelivery = normalizeMethods(onDeliveryMethods);
        if (onDelivery.isEmpty()) onDelivery = List.of(ComandDelivery.PAY_CASH);
        List<String> out = new ArrayList<>();
        switch (mode == null ? PaymentMode.ON_DELIVERY_ONLY : mode) {
            case ONLINE_REQUIRED -> {
                if (onlineAvailable) out.add(ComandDelivery.PAY_ONLINE);
                else out.addAll(onDelivery);
            }
            case CUSTOMER_CHOICE -> {
                if (onlineAvailable) out.add(ComandDelivery.PAY_ONLINE);
                out.addAll(onDelivery);
            }
            case ON_DELIVERY_ONLY -> out.addAll(onDelivery);
        }
        return out;
    }

    /** Solo CASH/CARD, senza duplicati, in ordine CASH, CARD. */
    public static List<String> normalizeMethods(Collection<String> methods) {
        if (methods == null) return List.of();
        Set<String> set = new HashSet<>();
        for (String m : methods) {
            if (m == null) continue;
            String v = m.trim().toUpperCase(Locale.ROOT);
            if (ComandDelivery.PAY_CASH.equals(v) || ComandDelivery.PAY_CARD.equals(v)) set.add(v);
        }
        List<String> out = new ArrayList<>();
        if (set.contains(ComandDelivery.PAY_CASH)) out.add(ComandDelivery.PAY_CASH);
        if (set.contains(ComandDelivery.PAY_CARD)) out.add(ComandDelivery.PAY_CARD);
        return out;
    }

    /**
     * Valida e normalizza le zone del titolare: 1..10, maxKm in (0, 50] crescenti e distinti, importi non negativi.
     * @throws IllegalArgumentException con messaggio in italiano
     */
    public static List<DeliveryZone> normalizeZones(List<DeliveryZone> zones) {
        if (zones == null || zones.isEmpty()) return List.of();
        if (zones.size() > MAX_ZONES) throw new IllegalArgumentException("Al massimo " + MAX_ZONES + " zone di consegna");
        List<DeliveryZone> out = new ArrayList<>();
        for (DeliveryZone z : zones) {
            if (z == null) throw new IllegalArgumentException("Zona non valida");
            if (!(z.getMaxKm() > 0) || z.getMaxKm() > MAX_ZONE_KM) {
                throw new IllegalArgumentException("Il raggio di ogni zona deve essere tra 0 e " + (int) MAX_ZONE_KM + " km");
            }
            if (z.getFeeCents() < 0 || z.getFeeCents() > MAX_FEE_CENTS) {
                throw new IllegalArgumentException("Tariffa di consegna non valida");
            }
            if (z.getMinOrderCents() < 0 || z.getMinOrderCents() > MAX_MIN_ORDER_CENTS) {
                throw new IllegalArgumentException("Ordine minimo non valido");
            }
            out.add(new DeliveryZone(Math.round(z.getMaxKm() * 100d) / 100d, z.getFeeCents(), z.getMinOrderCents()));
        }
        out.sort(Comparator.comparingDouble(DeliveryZone::getMaxKm));
        for (int i = 1; i < out.size(); i++) {
            if (out.get(i).getMaxKm() == out.get(i - 1).getMaxKm()) {
                throw new IllegalArgumentException("Due zone hanno lo stesso raggio (" + out.get(i).getMaxKm() + " km)");
            }
        }
        return out;
    }

    // ── Stage ────────────────────────────────────────────────────────────────

    public static boolean isStage(String s) {
        return ComandDelivery.STAGE_PREPARING.equals(s) || ComandDelivery.STAGE_OUT_FOR_DELIVERY.equals(s)
                || ComandDelivery.STAGE_DELIVERED.equals(s);
    }

    private static int stageIndex(String s) {
        if (ComandDelivery.STAGE_OUT_FOR_DELIVERY.equals(s)) return 1;
        if (ComandDelivery.STAGE_DELIVERED.equals(s)) return 2;
        return 0; // PREPARING o assente
    }

    /**
     * Solo in avanti: PREPARING → OUT_FOR_DELIVERY → DELIVERED (anche PREPARING → DELIVERED). Il rider può solo
     * impostare OUT_FOR_DELIVERY o DELIVERED; DELIVERED è definitivo.
     */
    public static boolean canMove(String from, String to) {
        if (!ComandDelivery.STAGE_OUT_FOR_DELIVERY.equals(to) && !ComandDelivery.STAGE_DELIVERED.equals(to)) return false;
        return stageIndex(to) > stageIndex(from);
    }

    /** Stage di partenza ammessi per arrivare a {@code to} (per l'update condizionale). */
    public static List<String> allowedFrom(String to) {
        List<String> out = new ArrayList<>();
        for (String s : List.of(ComandDelivery.STAGE_PREPARING, ComandDelivery.STAGE_OUT_FOR_DELIVERY)) {
            if (canMove(s, to)) out.add(s);
        }
        return out;
    }

    /** "€ 23,50" */
    public static String euro(long cents) {
        return String.format(Locale.ITALY, "€ %d,%02d", cents / 100, Math.abs(cents % 100));
    }
}
