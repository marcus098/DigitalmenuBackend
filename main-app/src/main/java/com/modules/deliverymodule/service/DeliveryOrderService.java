package com.modules.deliverymodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandHistoryEntry;
import com.modules.common.model.ComandDelivery;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.variant.VariantGroups;
import com.modules.deliverymodule.dto.DeliveryOrderRequest;
import com.modules.deliverymodule.dto.DeliveryZone;
import com.modules.deliverymodule.model.DeliverySettingsJpa;
import com.modules.deliverymodule.otp.OtpService;
import com.modules.deliverymodule.otp.PhoneNumbers;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandFromWaiterJpa;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.service.ComandStatusUpdater;
import com.modules.ordermodule.service.OrderComandService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService.SlotKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ordini a domicilio: creazione dal menu cliente (distanza, zona, tariffa e totali calcolati SOLO qui), snapshot per
 * gli ordini del cameriere, lista consegne del giorno e stage del rider. Vedi docs/DOMICILIO.md.
 */
@Service
public class DeliveryOrderService {

    public static final String MSG_PHONE_NOT_VERIFIED = "Telefono non verificato o verifica scaduta: richiedi un nuovo codice";

    @Autowired private DeliverySettingsService settingsService;
    @Autowired private DeliverySlotService slotService;
    @Autowired private OtpService otpService;
    @Autowired private OrderComandService orderComandService;
    @Autowired private MongoComandRepository mongoComandRepository;
    @Autowired private MongoComandReadRepository mongoComandReadRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AuthenticatedUserProvider auth;

    /** Preventivo della consegna (puro). */
    public record Quote(DeliveryZone zone, double distanceKm, long subtotalCents, long feeCents) {
        public long totalCents() { return subtotalCents + feeCents; }
    }

    /** Esito della creazione: risposta dell'endpoint pubblico. */
    public record Created(String id, ComandStatus status, long subtotalCents, long deliveryFeeCents) {
        public boolean paymentRequired() { return status == ComandStatus.AWAIT_PAYMENT; }
        public long totalCents() { return subtotalCents + deliveryFeeCents; }
        public Map<String, Object> body() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("status", status.name());
            m.put("paymentRequired", paymentRequired());
            m.put("subtotalCents", subtotalCents);
            m.put("deliveryFeeCents", deliveryFeeCents);
            m.put("totalCents", totalCents());
            return m;
        }
    }

    /**
     * Zona, tariffa e ordine minimo per un indirizzo.
     * @throws OrderRejectedException 400 fuori zona / sotto l'ordine minimo / origine non configurata
     */
    public static Quote quote(Double originLat, Double originLng, List<DeliveryZone> zones, Long freeDeliveryOverCents,
                              double lat, double lng, long subtotalCents) {
        if (!DeliveryRules.validCoordinates(originLat, originLng) || zones == null || zones.isEmpty()) {
            throw new OrderRejectedException(400, "La consegna a domicilio non è configurata per questo locale");
        }
        double km = DeliveryRules.round2(DeliveryRules.haversineKm(originLat, originLng, lat, lng));
        DeliveryZone zone = DeliveryRules.zoneFor(zones, km).orElseThrow(() -> new OrderRejectedException(400,
                String.format(Locale.ITALY, "Indirizzo fuori dalla zona di consegna (%.1f km, consegniamo entro %.1f km)",
                        km, DeliveryRules.maxKm(zones))));
        if (subtotalCents < zone.getMinOrderCents()) {
            throw new OrderRejectedException(400, "Ordine minimo per la tua zona: " + DeliveryRules.euro(zone.getMinOrderCents())
                    + " (mancano " + DeliveryRules.euro(zone.getMinOrderCents() - subtotalCents) + ")");
        }
        return new Quote(zone, km, subtotalCents, DeliveryRules.fee(zone, subtotalCents, freeDeliveryOverCents));
    }

    public static long subtotalCents(List<Order> orders) {
        long total = 0;
        if (orders == null) return 0;
        for (Order o : orders) {
            if (o == null || o.getProducts() == null) continue;
            for (ProductToOrder p : o.getProducts()) {
                if (p == null) continue;
                Long unit = p.getUnitPriceCents();
                if (unit == null) unit = VariantGroups.unitPriceCents(p.getProductOption(), p.getSelectedVariants(), p.getIngredientsPlus());
                total += unit * Math.max(0, p.getQuantity());
            }
        }
        return total;
    }

    // ── Ordine dal menu cliente ──────────────────────────────────────────────

    /**
     * @throws OrderRejectedException 400 (dati, telefono, zona, minimo, fascia, pagamento), 401 (telefono non verificato,
     *                                solo con la verifica SMS attiva)
     */
    public Created create(long idAgency, DeliveryOrderRequest req) {
        DeliverySettingsJpa s = settingsService.settings(idAgency);
        if (!s.isEnabled()) throw new OrderRejectedException(400, "La consegna a domicilio non è attiva per questo locale");
        if (req == null || blank(req.getCustomerName()) || blank(req.getCustomerPhone())
                || req.getAddress() == null || blank(req.getAddress().getLabel())
                || req.getOrders() == null || req.getOrders().isEmpty()) {
            throw new OrderRejectedException(400, "Dati incompleti");
        }
        String phone;
        try {
            phone = PhoneNumbers.normalize(req.getCustomerPhone());
        } catch (IllegalArgumentException e) {
            throw new OrderRejectedException(400, e.getMessage());
        }
        // Verifica SMS solo se attiva sul server (OtpService.enabled): altrimenti basta un numero valido
        if (otpService.enabled() && !otpService.isVerified(req.getPhoneToken(), phone)) {
            throw new OrderRejectedException(401, MSG_PHONE_NOT_VERIFIED);
        }

        String choice = req.getPaymentChoice() == null ? "" : req.getPaymentChoice().trim().toUpperCase(Locale.ROOT);
        List<String> options = DeliveryRules.paymentOptions(DeliverySettingsService.mode(s), DeliverySettingsService.methods(s),
                settingsService.onlineAvailable(idAgency));
        if (!options.contains(choice)) throw new OrderRejectedException(400, "Metodo di pagamento non ammesso dal locale");

        DeliveryOrderRequest.Address a = req.getAddress();
        if (!DeliveryRules.validCoordinates(a.getLat(), a.getLng())) {
            throw new OrderRejectedException(400, "Posizione dell'indirizzo mancante: scegli l'indirizzo sulla mappa");
        }

        String comandId = UUID.randomUUID() + "_" + System.currentTimeMillis();
        List<Order> orders = orderComandService.orderList(idAgency, 0L, req.getOrders(), comandId);
        long subtotal = subtotalCents(orders);
        Quote q = quote(s.getOriginLat(), s.getOriginLng(), DeliverySettingsService.zones(s), s.getFreeDeliveryOverCents(),
                a.getLat(), a.getLng(), subtotal);

        SlotKey slot = slotService.reserve(idAgency, s, req.getDeliveryTime());
        String time = req.getDeliveryTime().length() > 16 ? req.getDeliveryTime().substring(0, 16) : req.getDeliveryTime();
        ComandStatus status = ComandDelivery.PAY_ONLINE.equals(choice) ? ComandStatus.AWAIT_PAYMENT : ComandStatus.PENDING;
        try {
            ComandDelivery d = new ComandDelivery();
            d.setLabel(cut(a.getLabel(), 300));
            d.setLat(a.getLat());
            d.setLng(a.getLng());
            d.setIntercom(cut(a.getIntercom(), 60));
            d.setFloor(cut(a.getFloor(), 20));
            d.setNotes(cut(a.getNotes(), 300));
            d.setDistanceKm(q.distanceKm());
            d.setFeeCents(q.feeCents());
            d.setPaymentChoice(choice);
            d.setStage(ComandDelivery.STAGE_PREPARING);

            ComandFromWaiterJpa comand = new ComandFromWaiterJpa(idAgency, 0L, status, orders,
                    cut(req.getCustomerName(), 80), d.getLabel(), time, phone);
            comand.setComandWaiterType(ComandWaiterType.HOME);
            comand.setDelivery(d);
            comand.appendHistory(ComandHistoryEntry.created(ComandHistoryEntry.client(), status));
            Comand saved = mongoComandRepository.save(comand);
            if (status == ComandStatus.PENDING) {
                orderComandService.notifyComandCreated(saved);
                orderComandService.notifyStaffNewOrder(saved);
            }
            // AWAIT_PAYMENT: stampa/dashboard partono da onPaymentCompleted, come l'asporto prepagato
            return new Created(saved.getId(), saved.getStatus(), q.subtotalCents(), q.feeCents());
        } catch (Exception e) {
            slotService.release(slot);
            ErrorLog.logger.error("Errore creazione ordine a domicilio idAgency=" + idAgency, e);
            throw new OrderRejectedException(500, "Errore nella creazione dell'ordine a domicilio");
        }
    }

    // ── Ordine a domicilio del cameriere ─────────────────────────────────────

    /**
     * Snapshot per un ordine "Domicilio" inserito dallo staff: indirizzo libero (coordinate facoltative), tariffa
     * indicata dallo staff oppure dalla zona se ci sono le coordinate (nessun controllo di ordine minimo/zona: è lo
     * staff a decidere), pagamento alla consegna in contanti se non indicato.
     */
    public ComandDelivery forWaiter(long idAgency, AddComandWaiter req, List<Order> orders) {
        ComandDelivery d = new ComandDelivery();
        d.setLabel(cut(req.getAddress(), 300));
        d.setIntercom(cut(req.getIntercom(), 60));
        d.setFloor(cut(req.getFloor(), 20));
        d.setNotes(cut(req.getDeliveryNotes(), 300));
        String choice = req.getPaymentChoice() == null ? "" : req.getPaymentChoice().trim().toUpperCase(Locale.ROOT);
        d.setPaymentChoice(List.of(ComandDelivery.PAY_CASH, ComandDelivery.PAY_CARD, ComandDelivery.PAY_ONLINE).contains(choice)
                ? choice : ComandDelivery.PAY_CASH);
        d.setStage(ComandDelivery.STAGE_PREPARING);
        long fee = 0;
        if (DeliveryRules.validCoordinates(req.getLat(), req.getLng())) {
            d.setLat(req.getLat());
            d.setLng(req.getLng());
            try {
                DeliverySettingsJpa s = settingsService.settings(idAgency);
                if (DeliveryRules.validCoordinates(s.getOriginLat(), s.getOriginLng())) {
                    double km = DeliveryRules.round2(DeliveryRules.haversineKm(s.getOriginLat(), s.getOriginLng(), req.getLat(), req.getLng()));
                    d.setDistanceKm(km);
                    long subtotal = subtotalCents(orders);
                    fee = DeliveryRules.zoneFor(DeliverySettingsService.zones(s), km)
                            .map(z -> DeliveryRules.fee(z, subtotal, s.getFreeDeliveryOverCents())).orElse(0L);
                }
            } catch (Exception e) {
                ErrorLog.logger.warn("Tariffa consegna non calcolabile per l'ordine del cameriere idAgency={}", idAgency);
            }
        }
        if (req.getDeliveryFeeCents() != null && req.getDeliveryFeeCents() >= 0 && req.getDeliveryFeeCents() <= DeliveryRules.MAX_FEE_CENTS) {
            fee = req.getDeliveryFeeCents();
        }
        d.setFeeCents(fee);
        return d;
    }

    // ── Rider ────────────────────────────────────────────────────────────────

    /** Consegne del giorno dell'agency del chiamante (esclusi ordini annullati o in attesa di pagamento/approvazione). */
    public List<Map<String, Object>> deliveriesOfDay(LocalDate date) {
        long idAgency = auth.getAgencyId();
        Criteria isoOnDay = Criteria.where("time").regex("^" + date + "T");
        Criteria createdOnDay = Criteria.where("createdAt").gte(date.atStartOfDay()).lt(date.plusDays(1).atStartOfDay());
        Criteria notIso = Criteria.where("time").not().regex("^\\d{4}-\\d{2}-\\d{2}T");
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("idAgency").is(idAgency),
                Criteria.where("comandWaiterType").is(ComandWaiterType.HOME.name()),
                Criteria.where("status").nin(List.of(ComandStatus.DELETED.name(), ComandStatus.AWAIT_PAYMENT.name(),
                        ComandStatus.AWAIT_APPROVAL.name())),
                new Criteria().orOperator(isoOnDay, new Criteria().andOperator(notIso, createdOnDay)))).limit(300);
        List<ComandJpa> list = mongoTemplate.find(q, ComandJpa.class, ComandStatusUpdater.COLLECTION);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComandJpa c : list) out.add(riderView(c, date));
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("deliveryTime"))));
        return out;
    }

    static Map<String, Object> riderView(ComandJpa c, LocalDate date) {
        ComandDelivery d = c.getDelivery() != null ? c.getDelivery() : legacy(c);
        LocalDateTime when = DeliverySlotService.parse(c.getTime(), c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate() : date);
        long subtotal = subtotalCents(c.getOrders());
        long fee = d.getFeeCents();
        boolean paid = Boolean.TRUE.equals(c.getPaid());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        m.put("createdAt", c.getCreatedAt());
        m.put("deliveryTime", when != null ? when.toString() : c.getTime());
        m.put("customerName", c.getName());
        m.put("phone", c.getPhone());
        m.put("paid", paid);
        m.put("subtotalCents", subtotal);
        m.put("deliveryFeeCents", fee);
        m.put("totalCents", subtotal + fee);
        m.put("toCollectCents", paid || !d.payOnDelivery() ? 0 : subtotal + fee);
        m.put("delivery", d);
        m.put("orders", c.getOrders() == null ? List.of() : c.getOrders());
        return m;
    }

    /** Ordini a domicilio precedenti allo snapshot: indirizzo libero, contanti, in preparazione. */
    static ComandDelivery legacy(Comand c) {
        ComandDelivery d = new ComandDelivery();
        d.setLabel(c instanceof ComandJpa j ? j.getAddress() : null);
        d.setPaymentChoice(ComandDelivery.PAY_CASH);
        d.setStage(ComandDelivery.STAGE_PREPARING);
        return d;
    }

    /**
     * Cambio stage del rider: solo in avanti (OUT_FOR_DELIVERY, DELIVERED). Alla consegna di un ordine da incassare
     * serve {@code cashCollected}. Pubblica l'evento d'ordine (SSE) per la pagina stato del cliente e le dashboard.
     * @throws OrderRejectedException 400 (stage / cashCollected), 404 (comanda), 409 (stato o transizione)
     */
    public ComandDelivery changeStage(String comandId, String stage, Boolean cashCollected) {
        long idAgency = auth.getAgencyId();
        long idUser = auth.getUserId();
        String target = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        if (!ComandDelivery.STAGE_OUT_FOR_DELIVERY.equals(target) && !ComandDelivery.STAGE_DELIVERED.equals(target)) {
            throw new OrderRejectedException(400, "Stage non valido: usa OUT_FOR_DELIVERY o DELIVERED");
        }
        ComandJpa c = mongoComandReadRepository.findById(comandId)
                .filter(x -> x.getIdAgency() != null && x.getIdAgency() == idAgency
                        && x.getComandWaiterType() == ComandWaiterType.HOME)
                .orElseThrow(() -> new OrderRejectedException(404, "Consegna non trovata"));
        ComandStatus st = c.getStatus();
        if (st == ComandStatus.DELETED || st == ComandStatus.AWAIT_PAYMENT || st == ComandStatus.AWAIT_APPROVAL) {
            throw new OrderRejectedException(409, "L'ordine non è consegnabile in questo stato");
        }
        ComandDelivery d = c.getDelivery() != null ? c.getDelivery() : legacy(c);
        String from = d.getStage() == null ? ComandDelivery.STAGE_PREPARING : d.getStage();
        if (!DeliveryRules.canMove(from, target)) {
            throw new OrderRejectedException(409, "Passaggio non consentito: " + from + " → " + target);
        }
        boolean toCollect = d.payOnDelivery() && !Boolean.TRUE.equals(c.getPaid());
        if (ComandDelivery.STAGE_DELIVERED.equals(target) && toCollect && cashCollected == null) {
            throw new OrderRejectedException(400, "Indica se hai incassato il pagamento alla consegna (cashCollected)");
        }
        LocalDateTime now = LocalDateTime.now();
        d.setStage(target);
        d.setStageBy(idUser);
        if (ComandDelivery.STAGE_OUT_FOR_DELIVERY.equals(target)) d.setOutForDeliveryAt(now);
        if (ComandDelivery.STAGE_DELIVERED.equals(target)) {
            d.setDeliveredAt(now);
            if (d.getOutForDeliveryAt() == null) d.setOutForDeliveryAt(now);
            if (toCollect) d.setCashCollected(cashCollected);
        }
        Criteria stageCond = c.getDelivery() == null
                ? Criteria.where("delivery").exists(false)
                : Criteria.where("delivery.stage").in(DeliveryRules.allowedFrom(target));
        Query q = new Query(new Criteria().andOperator(Criteria.where("_id").is(comandId), Criteria.where("idAgency").is(idAgency),
                stageCond));
        Update u = new Update().set("delivery", d).set("updatedAt", now);
        if (mongoTemplate.updateFirst(q, u, ComandStatusUpdater.COLLECTION).getModifiedCount() == 0) {
            throw new OrderRejectedException(409, "La consegna è stata aggiornata da un altro dispositivo: ricarica");
        }
        c.setDelivery(d);
        orderComandService.publishOrderEvent(c, st.name(), target);
        return d;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String v = s.strip();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }
}
