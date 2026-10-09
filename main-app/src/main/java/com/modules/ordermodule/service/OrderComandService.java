package com.modules.ordermodule.service;

import com.modules.authmodule.repository.UserRepository;
import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.dto.TableDto;
import com.modules.common.finders.CategoryUtils;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.*;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.enums.SessionStatus;
import com.modules.ordermodule.event.ComandCreatedEvent;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandFromWaiterJpa;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.model.TableSessionJpa;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.repository.TableSessionRepository;
import com.modules.ordermodule.request.AddComandClient;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * NB: niente @Transactional qui. MongoDB gira standalone (non replica set) e non c'è un MongoTransactionManager:
 * le annotazioni precedenti aprivano solo una transazione JPA che non copriva le scritture Mongo
 * (falso senso di atomicità). Le scritture JPA (tavoli) restano transazionali a livello di repository.
 */
@Service
public class OrderComandService {
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 50;

    @Autowired
    private MongoComandRepository mongoComandRepository;
    @Autowired
    private MongoComandLogRepository mongoComandLogRepository;
    @Autowired
    private MongoComandReadRepository mongoComandReadRepository;
    @Autowired
    private TableUtils tableUtils;
    @Autowired
    private CategoryUtils categoryUtils;
    @Autowired
    private ProductUtils productUtils;
    @Autowired
    private IngredientUtils ingredientUtils;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private OrderUpdateProducer orderUpdateProducer;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TakeawaySlotService takeawaySlotService;
    @Autowired
    private TableSessionRepository tableSessionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ComandStatusUpdater statusUpdater;
    @Autowired
    private PrepaymentPolicy prepaymentPolicy;
    @Autowired
    private MongoTemplate mongoTemplate;
    /** Lazy: PaymentService dipende (tramite il calcolo del totale) da questo service. */
    @Autowired
    private ObjectProvider<ComandPaymentOperations> paymentOperations;

    /** Esito della creazione di una comanda pubblica: AWAIT_PAYMENT = il cliente deve pagare subito. */
    public record CreationResult(String id, ComandStatus status) {
        public boolean paymentRequired() { return status == ComandStatus.AWAIT_PAYMENT; }
    }

    public boolean requiresPrepayment(long idAgency, PrepaymentPolicy.Channel channel) {
        try {
            return prepaymentPolicy.requiresPrepayment(idAgency, channel);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore lettura impostazione prepagamento idAgency=" + idAgency, e);
            return false;
        }
    }

    public String addOrderWaiter(AddComandWaiter addComandWaiter) {
        TakeawaySlotService.SlotKey slotKey = null;
        int slotProducts = 0;
        boolean saved = false;
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            String comandId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();

            List<Order> orders = orderList(idAgency, idUser, addComandWaiter.getOrders(), comandId);

            ComandFromWaiterJpa comand;

            switch (addComandWaiter.getComandWaiterType()) {
                case TABLE -> {
                    TableDto tableEntityJpa = tableUtils.findByIdAndIdAgencyAndDeleted(addComandWaiter.getIdTable(), idAgency).orElseThrow(() -> new EntityNotFoundException("Tavolo non trovato o eliminato"));
                    if(!tableEntityJpa.isBusy()){
                        tableEntityJpa.setBusy(true);
                        tableEntityJpa.setSeats(addComandWaiter.getSeats() > 0 ? addComandWaiter.getSeats() : 1);
                        tableEntityJpa.setSessionId(UUID.randomUUID().toString().concat(Long.toString(System.currentTimeMillis())));
                        tableEntityJpa = tableUtils.save(tableEntityJpa, idAgency);
                    }
                    String tableSessionId = tableEntityJpa.getSessionId();
                    comand = new ComandFromWaiterJpa(idAgency, addComandWaiter.getIdTable(), idUser, ComandStatus.PROGRESS, orders, tableSessionId);
                }
                case HOME -> comand = new ComandFromWaiterJpa(idAgency, idUser, ComandStatus.PROGRESS, orders,
                        addComandWaiter.getName(), addComandWaiter.getAddress(),
                        addComandWaiter.getTime(), addComandWaiter.getPhone());

                case TAKE_AWAY -> {
                    comand = new ComandFromWaiterJpa(idAgency, idUser, ComandStatus.PROGRESS, orders,
                            addComandWaiter.getName(), addComandWaiter.getTime(),
                            addComandWaiter.getPhone());
                    // Il cameriere può forzare lo slot: registriamo l'occupazione senza controllo capacità.
                    slotProducts = countProducts(orders);
                    slotKey = takeawaySlotService.consumeSlot(idAgency, addComandWaiter.getTime(), slotProducts);
                }

                default ->
                        throw new IllegalArgumentException("Tipo di comando non valido: " + addComandWaiter.getComandWaiterType());
            }

            //comand.setId(comandId);
            comand.setComandWaiterType(addComandWaiter.getComandWaiterType());
            Comand comand1 = (ComandFromWaiterJpa) mongoComandRepository.save(comand);
            saved = true;

            EntityLog<Comand> comandLog = new EntityLog<>(
                    LogOperation.ADD, null, comand,
                    "function addOrderWaiter",
                    idUser, idAgency);
            mongoComandLogRepository.save(comandLog);

            notifyComandCreated(comand1);
            return comand1.getId();
        } catch (Exception e) {
            if (!saved) takeawaySlotService.releaseSlot(slotKey, slotProducts);
            ErrorLog.logger.error("Errore aggiunta ordine da waiter", e);
            return null;
        }
    }

    /**
     * Costruisce gli ordini a partire dal catalogo (nomi, categorie, opzioni e prezzi sono SEMPRE presi
     * dal DB, mai dal client). Valida quantità (1..50) ed esistenza di prodotti/ingredienti dell'agency.
     *
     * @throws OrderRejectedException (400) se il payload non è valido
     */
    public List<Order> orderList (long idAgency, long idUser, List<AddComandOrder> orders, String comandId) {
        if (orders == null || orders.isEmpty()) {
            throw new OrderRejectedException(400, "Ordine vuoto");
        }

        Set<Long> productIds = new HashSet<>();
        Set<Long> ingredientIds = new HashSet<>();

        for (AddComandOrder order : orders) {
            if (order == null || order.getProducts() == null) {
                throw new OrderRejectedException(400, "Ordine non valido");
            }
            for (AddProductToOrder p : order.getProducts()) {
                if (p == null) throw new OrderRejectedException(400, "Prodotto non valido");
                if (p.getQuantity() < MIN_QUANTITY || p.getQuantity() > MAX_QUANTITY) {
                    throw new OrderRejectedException(400,
                            "Quantità non valida: deve essere tra " + MIN_QUANTITY + " e " + MAX_QUANTITY);
                }
                if (p.getIngredientsMinus() == null) p.setIngredientsMinus(new ArrayList<>());
                if (p.getIngredientsPlus() == null) p.setIngredientsPlus(new ArrayList<>());
                productIds.add(p.getIdProduct());
                ingredientIds.addAll(p.getIngredientsMinus());
                ingredientIds.addAll(p.getIngredientsPlus());
            }
        }

        Map<Long, ProductDto> productMap = productUtils.findAllByIdInAndIdAgencyAndDeleted(productIds, idAgency)
                .stream()
                .collect(Collectors.toMap(ProductDto::getId, Function.identity()));

        Set<Long> categoryIds = productMap.values().stream()
                .map(ProductDto::getIdCategory)
                .collect(Collectors.toSet());

        Map<Long, CategoryDto> categoryMap = categoryUtils.findAllByIdInAndIdAgencyAndDeleted(categoryIds, idAgency)
                .stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));

        Map<Long, IngredientDto> ingredientMap = ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(ingredientIds, idAgency)
                .stream()
                .collect(Collectors.toMap(IngredientDto::getId, Function.identity()));

        return orders.stream().map(addComandOrder -> {
            List<ProductToOrder> productToOrders = addComandOrder.getProducts().stream().map(prod -> {
                ProductDto product = productMap.get(prod.getIdProduct());
                if (product == null) {
                    throw new OrderRejectedException(400, "Prodotto non trovato: " + prod.getIdProduct());
                }
                CategoryDto category = categoryMap.get(product.getIdCategory());
                if (category == null) {
                    throw new OrderRejectedException(400, "Categoria non trovata per il prodotto: " + prod.getIdProduct());
                }

                List<IngredientOrder> ingredientsMinus = prod.getIngredientsMinus().stream()
                        .map(id -> {
                            IngredientDto i = requireIngredient(ingredientMap, id);
                            IngredientOrder io = new IngredientOrder();
                            io.setId(i.getId());
                            io.setName(i.getName());
                            return io;
                        }).collect(Collectors.toList());

                List<IngredientOrderPlus> ingredientsPlus = prod.getIngredientsPlus().stream()
                        .map(id -> {
                            IngredientDto i = requireIngredient(ingredientMap, id);
                            IngredientOrderPlus io = new IngredientOrderPlus();
                            io.setId(i.getId());
                            io.setName(i.getName());
                            io.setPrice(i.getPrice() == null ? 0 : i.getPrice());
                            return io;
                        }).collect(Collectors.toList());

                ProductToOrder pto = new ProductToOrder();
                pto.setIdProduct(product.getId());
                pto.setProductName(product.getName());
                pto.setIdCategory(category.getId());
                pto.setCategoryName(category.getName());
                pto.setQuantity(prod.getQuantity());
                pto.setNote(prod.getNote());
                pto.setIngredientsMinus(ingredientsMinus);
                pto.setIngredientsPlus(ingredientsPlus);
                // Risolve l'opzione: client → nome match, altrimenti default, altrimenti la prima.
                // Prodotti senza opzioni ricevono null — niente NPE su getOptions().get(0).
                String requested = prod.getProductOption();
                var opts = product.getOptions();
                com.modules.common.model.OptionInProduct chosenOpt = null;
                if (opts != null && !opts.isEmpty()) {
                    if (requested != null && !requested.isBlank()) {
                        chosenOpt = opts.stream()
                                .filter(o -> o != null && requested.equals(o.getName()))
                                .findFirst()
                                .orElse(null);
                    }
                    if (chosenOpt == null) {
                        chosenOpt = opts.stream()
                                .filter(o -> o != null && o.isIsDefault())
                                .findFirst()
                                .orElse(opts.get(0));
                    }
                }
                pto.setProductOption(chosenOpt);
                // Snapshot del prezzo unitario (opzione + extra) dal catalogo.
                pto.setUnitPriceCents(computeUnitPriceCents(chosenOpt, ingredientsPlus));

                return pto;
            }).collect(Collectors.toList());

            return new Order(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    comandId,
                    Long.toString(idUser),
                    productToOrders
            );
        }).collect(Collectors.toList());

    }

    /**
     * Ordine diretto da cliente al tavolo (POST /api/public/orders/insert).
     * Richiede un clientSessionId che abbia fatto join alla sessione OPEN del tavolo e un localname
     * coerente con l'agency del tavolo (non eliminato).
     *
     * @return id della comanda, oppure null per errore interno
     * @throws OrderRejectedException con lo status HTTP da restituire per richieste non valide
     */
    public CreationResult addOrderFromClient(AddComandClient request) {
        if (request.getClientSessionId() == null || request.getClientSessionId().isBlank()) {
            throw new OrderRejectedException(403, "Sessione tavolo mancante: inserisci il codice del tavolo");
        }
        Long agencyId = resolveAgencyByLocalname(request.getLocalname());
        if (agencyId == null) throw new OrderRejectedException(404, "Tavolo non trovato");
        TableDto table = tableUtils.findByIdAndIdAgencyAndDeleted(request.getTableId(), agencyId)
                .orElseThrow(() -> new OrderRejectedException(404, "Tavolo non trovato"));
        TableSessionJpa session = tableSessionRepository.findByTableIdAndStatus(table.getId(), SessionStatus.OPEN)
                .orElseThrow(() -> new OrderRejectedException(409, "Il tavolo non è aperto"));
        if (session.getIdAgency() != agencyId) throw new OrderRejectedException(404, "Tavolo non trovato");
        boolean joined = session.getClients() != null && session.getClients().stream()
                .anyMatch(c -> request.getClientSessionId().equals(c.getClientSessionId()));
        if (!joined) {
            throw new OrderRejectedException(403, "Non sei unito alla sessione di questo tavolo");
        }

        long idAgency = agencyId;
        String comandId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        List<Order> orders = orderList(idAgency, 0L, request.getOrders(), comandId);
        boolean prepay = requiresPrepayment(idAgency, PrepaymentPolicy.Channel.TABLE);
        try {
            ComandFromWaiterJpa comand = new ComandFromWaiterJpa(idAgency, table.getId(), 0L,
                    prepay ? ComandStatus.AWAIT_PAYMENT : ComandStatus.AWAIT, orders, session.getId());
            comand.setComandWaiterType(ComandWaiterType.TABLE);
            comand.setClientSessionId(request.getClientSessionId());

            Comand saved = mongoComandRepository.save(comand);
            // Prepagamento: niente stampa/dashboard finché Stripe non conferma (vedi onPaymentCompleted)
            if (!prepay) notifyComandCreated(saved);
            return new CreationResult(saved.getId(), saved.getStatus());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore aggiunta ordine da client", e);
            return null;
        }
    }

    /**
     * Da chiamare dopo il salvataggio di una NUOVA comanda (qualsiasi canale): pubblica il
     * {@link ComandCreatedEvent} (es. stampanti) e l'evento Kafka order-updated.
     */
    public void notifyComandCreated(Comand saved) {
        try {
            eventPublisher.publishEvent(new ComandCreatedEvent(saved.getId(), String.valueOf(saved.getIdAgency())));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore pubblicazione ComandCreatedEvent comandId=" + saved.getId(), e);
        }
        sendOrderKafkaEvent(saved.getId(), saved.getStatus().toString(), saved.getIdAgency());
    }

    private void sendOrderKafkaEvent(String id, String status, long idAgency) {
        try {
            String json = String.format("{\"id\":\"%s\",\"status\":\"%s\",\"idAgency\":%d}", id, status, idAgency);
            orderUpdateProducer.sendUpdate(idAgency, json);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio kafka order event", e);
        }
    }

    /**
     * Ordine asporto pubblico. Valida i prodotti e prenota atomicamente lo slot di ritiro.
     *
     * Stato iniziale:
     * <ul>
     *   <li>prepagamento richiesto → AWAIT_PAYMENT (approvalRequired se lo slot è nella riserva);</li>
     *   <li>altrimenti riserva → AWAIT_APPROVAL (visibile in "Da approvare", non stampato);</li>
     *   <li>altrimenti PENDING + pipeline "creata" (stampa + dashboard).</li>
     * </ul>
     *
     * @return id e stato della comanda, oppure null per errore interno
     * @throws OrderRejectedException 400 (payload) / 409 (slot non disponibile, chiuso, asporto sospeso)
     */
    public CreationResult addPublicTakeaway(long idAgency, String customerName, String customerPhone, String pickupTime, List<AddComandOrder> orders) {
        String comandId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        List<Order> orderList = orderList(idAgency, 0L, orders, comandId);
        int products = countProducts(orderList);
        boolean prepay = requiresPrepayment(idAgency, PrepaymentPolicy.Channel.TAKEAWAY);
        TakeawaySlotService.SlotReservation reservation =
                takeawaySlotService.reservePublicSlot(idAgency, pickupTime, products, prepay);
        ComandStatus status = prepay ? ComandStatus.AWAIT_PAYMENT
                : reservation.onRequest() ? ComandStatus.AWAIT_APPROVAL : ComandStatus.PENDING;
        try {
            ComandFromWaiterJpa comand = new ComandFromWaiterJpa(
                    idAgency, 0L, status, orderList,
                    customerName, pickupTime, customerPhone
            );
            comand.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
            if (reservation.onRequest()) comand.setApprovalRequired(true);
            if (status == ComandStatus.AWAIT_APPROVAL) {
                comand.setApprovalDeadline(ComandFlowRules.approvalDeadline(LocalDateTime.now(), reservation.slotStart()));
            }
            Comand saved = mongoComandRepository.save(comand);
            switch (status) {
                case PENDING -> notifyComandCreated(saved);
                case AWAIT_APPROVAL -> notifyAwaitingApproval(saved);
                default -> { /* AWAIT_PAYMENT: tutto parte da onPaymentCompleted / onPaymentAuthorized */ }
            }
            return new CreationResult(saved.getId(), saved.getStatus());
        } catch (Exception e) {
            takeawaySlotService.releaseSlot(reservation.key(), products);
            ErrorLog.logger.error("Errore aggiunta ordine asporto pubblico", e);
            return null;
        }
    }

    /** Ordine "su richiesta" visibile al locale: evento Kafka (dashboard + SSE) ma niente ComandCreatedEvent/stampa. */
    private void notifyAwaitingApproval(Comand c) {
        sendOrderKafkaEvent(c.getId(), ComandStatus.AWAIT_APPROVAL.name(), c.getIdAgency());
    }

    // ── Prepagamento / approvazione ──────────────────────────────────────────

    /**
     * Pagamento confermato (capture automatica, dopo che la comanda è stata marcata paid): AWAIT_PAYMENT → stato
     * iniziale del canale (o AWAIT_APPROVAL per gli ordini nella riserva) con update condizionale, poi la pipeline
     * "creata" (stampa + dashboard). È l'UNICO punto in cui parte la pipeline per gli ordini prepagati.
     * Idempotente: una seconda chiamata (retry webhook) non trova più AWAIT_PAYMENT e non fa nulla.
     *
     * @return true se la transizione è avvenuta ora
     */
    public boolean onPaymentCompleted(String comandId) {
        ComandJpa c = mongoComandReadRepository.findById(comandId).orElse(null);
        if (c == null || c.getStatus() != ComandStatus.AWAIT_PAYMENT) return false;
        ComandStatus target = ComandFlowRules.statusAfterPayment(c.getComandWaiterType(), c.getApprovalRequired());
        LocalDateTime deadline = target == ComandStatus.AWAIT_APPROVAL ? approvalDeadlineFor(c) : null;
        boolean moved = statusUpdater.compareAndSet(comandId, null, List.of(ComandStatus.AWAIT_PAYMENT), target,
                deadline == null ? null : u -> u.set("approvalDeadline", deadline));
        if (!moved) return false;
        c.setStatus(target);
        if (target == ComandStatus.AWAIT_APPROVAL) notifyAwaitingApproval(c);
        else notifyComandCreated(c);
        return true;
    }

    /**
     * Importo autorizzato (capture manuale, ordine nella riserva): AWAIT_PAYMENT → AWAIT_APPROVAL. Il locale lo vede
     * in "Da approvare" (Kafka/SSE) ma NON viene stampato: la pipeline "creata" parte solo all'approvazione.
     */
    public boolean onPaymentAuthorized(String comandId, String paymentIntentId) {
        ComandJpa c = mongoComandReadRepository.findById(comandId).orElse(null);
        if (c == null || c.getStatus() != ComandStatus.AWAIT_PAYMENT) return false;
        LocalDateTime deadline = approvalDeadlineFor(c);
        boolean moved = statusUpdater.compareAndSet(comandId, null, List.of(ComandStatus.AWAIT_PAYMENT),
                ComandStatus.AWAIT_APPROVAL, u -> {
                    u.set("paymentAuthorized", true).set("approvalDeadline", deadline);
                    if (paymentIntentId != null) u.set("paymentIntentId", paymentIntentId);
                });
        if (!moved) return false;
        notifyAwaitingApproval(c);
        return true;
    }

    /** Scadenza approvazione da adesso: min(ora + 10 min, inizio slot di ritiro). */
    private LocalDateTime approvalDeadlineFor(ComandJpa c) {
        LocalDateTime slotStart = null;
        if (c.getComandWaiterType() == ComandWaiterType.TAKE_AWAY && c.getIdAgency() != null) {
            slotStart = takeawaySlotService.slotStartFor(c.getIdAgency(), c.getTime(), c.getCreatedAt());
        }
        return ComandFlowRules.approvalDeadline(LocalDateTime.now(), slotStart);
    }

    /**
     * Il locale accetta un ordine "su richiesta": AWAIT_APPROVAL → PENDING (update condizionale, vince su un rifiuto
     * concorrente), incasso dell'importo autorizzato se presente, poi pipeline "creata" (stampa + dashboard).
     * Se l'incasso fallisce l'ordine viene annullato (l'autorizzazione non è più valida).
     *
     * @throws OrderRejectedException 404 (non trovata / altra agency), 409 (non più in attesa / incasso fallito)
     */
    public void approve(String comandId) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ComandJpa c = requireOwnComand(comandId, idAgency);
        if (c.getStatus() != ComandStatus.AWAIT_APPROVAL
                || !statusUpdater.compareAndSet(comandId, idAgency, List.of(ComandStatus.AWAIT_APPROVAL),
                ComandStatus.PENDING, null)) {
            throw new OrderRejectedException(409, "L'ordine non è più in attesa di approvazione");
        }
        if (Boolean.TRUE.equals(c.getPaymentAuthorized())) {
            boolean captured;
            try {
                captured = paymentOperations.getObject().captureAuthorized(comandId);
            } catch (Exception e) {
                ErrorLog.logger.error("Errore incasso pagamento autorizzato comanda " + comandId, e);
                captured = false;
            }
            if (!captured) {
                if (statusUpdater.compareAndSet(comandId, idAgency, List.of(ComandStatus.PENDING), ComandStatus.DELETED,
                        u -> u.set("rejectReason", ComandFlowRules.REASON_CAPTURE_FAILED))) {
                    afterDeleted(c, ComandStatus.PENDING, true);
                }
                throw new OrderRejectedException(409,
                        "Impossibile incassare il pagamento autorizzato: l'ordine è stato annullato");
            }
        }
        saveLog(ComandStatus.AWAIT_APPROVAL, ComandStatus.PENDING, "approve comand " + comandId, idUser, idAgency);
        c.setStatus(ComandStatus.PENDING);
        notifyComandCreated(c);
    }

    /**
     * Il locale rifiuta un ordine "su richiesta": AWAIT_APPROVAL → DELETED con motivo (visibile al cliente),
     * libera lo slot e annulla l'autorizzazione di pagamento (nessun addebito).
     */
    public void reject(String comandId, String reason) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        ComandJpa c = requireOwnComand(comandId, idAgency);
        String r = reason == null || reason.isBlank() ? "Ordine rifiutato dal locale"
                : reason.strip().substring(0, Math.min(reason.strip().length(), 300));
        if (c.getStatus() != ComandStatus.AWAIT_APPROVAL || !rejectInternal(c, idAgency, r)) {
            throw new OrderRejectedException(409, "L'ordine non è più in attesa di approvazione");
        }
        saveLog(ComandStatus.AWAIT_APPROVAL, ComandStatus.DELETED, "reject comand " + comandId + ": " + r, idUser, idAgency);
    }

    private boolean rejectInternal(ComandJpa c, Long idAgency, String reason) {
        boolean authorized = Boolean.TRUE.equals(c.getPaymentAuthorized());
        boolean moved = statusUpdater.compareAndSet(c.getId(), idAgency, List.of(ComandStatus.AWAIT_APPROVAL),
                ComandStatus.DELETED, u -> {
                    u.set("rejectReason", reason);
                    if (authorized) u.set("authorizationCanceled", true);
                });
        if (moved) afterDeleted(c, ComandStatus.AWAIT_APPROVAL, true);
        return moved;
    }

    /** Dopo un passaggio a DELETED: libera lo slot, evento di dominio (annulla gli intent Stripe), Kafka opzionale. */
    private void afterDeleted(ComandJpa c, ComandStatus oldStatus, boolean notifyDashboards) {
        long idAgency = c.getIdAgency();
        if (c.getComandWaiterType() == ComandWaiterType.TAKE_AWAY) {
            takeawaySlotService.onTakeawayStatusChanged(idAgency, c.getTime(), c.getCreatedAt(),
                    countProducts(c.getOrders()), oldStatus, ComandStatus.DELETED);
        }
        try {
            eventPublisher.publishEvent(new ComandStatusChangedEvent(c.getId(), String.valueOf(idAgency), oldStatus, ComandStatus.DELETED));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore pubblicazione ComandStatusChangedEvent comandId=" + c.getId(), e);
        }
        if (notifyDashboards) sendOrderKafkaEvent(c.getId(), ComandStatus.DELETED.name(), idAgency);
    }

    /**
     * Job: comande in AWAIT_PAYMENT da più di 15 minuti → DELETED (slot liberato, intent annullato).
     * Se un intent non è annullabile (pagamento appena riuscito, webhook in arrivo) la comanda viene lasciata stare.
     * @return numero di comande scadute
     */
    public int expireUnpaid(LocalDateTime now) {
        Query q = new Query(Criteria.where("status").is(ComandStatus.AWAIT_PAYMENT.name())
                .and("createdAt").lte(now.minus(ComandFlowRules.PAYMENT_WINDOW))).limit(200);
        int count = 0;
        for (ComandJpa c : mongoTemplate.find(q, ComandJpa.class, ComandStatusUpdater.COLLECTION)) {
            try {
                if (!paymentOperations.getObject().cancelOpenIntents(c.getId())) continue;
                if (statusUpdater.compareAndSet(c.getId(), null, List.of(ComandStatus.AWAIT_PAYMENT), ComandStatus.DELETED,
                        u -> u.set("rejectReason", ComandFlowRules.REASON_PAYMENT_EXPIRED))) {
                    afterDeleted(c, ComandStatus.AWAIT_PAYMENT, false);
                    count++;
                }
            } catch (Exception e) {
                ErrorLog.logger.error("Errore scadenza comanda non pagata " + c.getId(), e);
            }
        }
        return count;
    }

    /** Job: ordini "su richiesta" oltre la scadenza → rifiuto automatico "Nessuna risposta dal locale". */
    public int autoRejectExpiredApprovals(LocalDateTime now) {
        Query q = new Query(Criteria.where("status").is(ComandStatus.AWAIT_APPROVAL.name())
                .and("approvalDeadline").lte(now)).limit(200);
        int count = 0;
        for (ComandJpa c : mongoTemplate.find(q, ComandJpa.class, ComandStatusUpdater.COLLECTION)) {
            try {
                if (rejectInternal(c, null, ComandFlowRules.REASON_NO_ANSWER)) count++;
            } catch (Exception e) {
                ErrorLog.logger.error("Errore rifiuto automatico comanda " + c.getId(), e);
            }
        }
        return count;
    }

    private ComandJpa requireOwnComand(String comandId, long idAgency) {
        return mongoComandReadRepository.findById(comandId)
                .filter(c -> c.getIdAgency() != null && c.getIdAgency() == idAgency)
                .orElseThrow(() -> new OrderRejectedException(404, "Comanda non trovata"));
    }

    private void saveLog(ComandStatus from, ComandStatus to, String what, long idUser, long idAgency) {
        try {
            mongoComandLogRepository.save(new EntityLog<>(LogOperation.OTHER, from, to, what, idUser, idAgency));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore salvataggio log comanda", e);
        }
    }

    /**
     * Cambio stato di una comanda dell'agency del chiamante. Eseguito in modo sincrono
     * (la firma resta CompletableFuture per compatibilità con il controller).
     * Ritorna 404 anche se la comanda esiste ma appartiene a un'altra agency (non riveliamo l'esistenza).
     */
    public CompletableFuture<Integer> changeStatusToComand(String idComand, ComandStatus comandStatus) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<ComandJpa> comand = mongoComandReadRepository.findById(idComand);
            if (comand.isEmpty() || comand.get().getIdAgency() == null || comand.get().getIdAgency() != idAgency) {
                return CompletableFuture.completedFuture(404);
            }
            ComandJpa comandFromWaiterJpa = comand.get();
            ComandStatus oldStatus = comandFromWaiterJpa.getStatus();

            // Gli stati di attesa si impostano solo dai flussi dedicati (pagamento / approvazione)
            if (ComandFlowRules.isWaitingStatus(comandStatus)) {
                return CompletableFuture.completedFuture(400);
            }
            if (oldStatus == ComandStatus.AWAIT_APPROVAL) {
                // dalla dashboard un "elimina" su un ordine da approvare equivale a un rifiuto
                if (comandStatus != ComandStatus.DELETED) return CompletableFuture.completedFuture(409);
                boolean rejected = rejectInternal(comandFromWaiterJpa, idAgency, "Ordine rifiutato dal locale");
                return CompletableFuture.completedFuture(rejected ? 200 : 409);
            }
            if (oldStatus == ComandStatus.AWAIT_PAYMENT && comandStatus != ComandStatus.DELETED) {
                return CompletableFuture.completedFuture(409);
            }

            // Update condizionale (solo status + updatedAt): non sovrascrive paid/paymentIntentId scritti dal webhook
            if (!statusUpdater.compareAndSet(idComand, idAgency, List.of(oldStatus), comandStatus, null)) {
                return CompletableFuture.completedFuture(409);
            }

            EntityLog<?> comandLog = new EntityLog<>(
                    LogOperation.OTHER, oldStatus, comandStatus,
                    "function changeStatus for command " + comandFromWaiterJpa.getId(),
                    idUser, idAgency);
            mongoComandLogRepository.save(comandLog);

            if (comandFromWaiterJpa.getComandWaiterType() == ComandWaiterType.TAKE_AWAY) {
                takeawaySlotService.onTakeawayStatusChanged(idAgency, comandFromWaiterJpa.getTime(),
                        comandFromWaiterJpa.getCreatedAt(), countProducts(comandFromWaiterJpa.getOrders()),
                        oldStatus, comandStatus);
            }

            try {
                eventPublisher.publishEvent(new ComandStatusChangedEvent(idComand, String.valueOf(idAgency), oldStatus, comandStatus));
            } catch (Exception e) {
                ErrorLog.logger.error("Errore pubblicazione ComandStatusChangedEvent comandId=" + idComand, e);
            }
            sendOrderKafkaEvent(idComand, comandStatus.toString(), idAgency);

        } catch (Exception e) {
            ErrorLog.logger.error("Errore cambio stato comanda con id " + idComand, e);
            return CompletableFuture.completedFuture(400);
        }

        return CompletableFuture.completedFuture(200);
    }

    /**
     * Totale della comanda in centesimi: somma di unitPriceCents * quantity su tutti i prodotti.
     * Per comande precedenti allo snapshot (unitPriceCents null) usa prezzo opzione + extra salvati sulla comanda.
     * NB: non verifica l'agency — il chiamante deve controllare che la comanda gli appartenga.
     *
     * @throws EntityNotFoundException se la comanda non esiste
     */
    public long computeTotalCents(String comandId) {
        Comand comand = mongoComandRepository.findById(comandId)
                .orElseThrow(() -> new EntityNotFoundException("Comanda non trovata: " + comandId));
        long total = 0;
        if (comand.getOrders() == null) return 0;
        for (Order order : comand.getOrders()) {
            if (order == null || order.getProducts() == null) continue;
            for (ProductToOrder p : order.getProducts()) {
                if (p == null) continue;
                Long unit = p.getUnitPriceCents();
                if (unit == null) unit = computeUnitPriceCents(p.getProductOption(), p.getIngredientsPlus());
                total += unit * Math.max(0, p.getQuantity());
            }
        }
        return total;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static long computeUnitPriceCents(OptionInProduct option, List<IngredientOrderPlus> extras) {
        long cents = option != null ? toCents(option.getPrice()) : 0;
        if (extras != null) {
            for (IngredientOrderPlus e : extras) {
                if (e != null) cents += toCents(e.getPrice());
            }
        }
        return cents;
    }

    private static long toCents(double price) {
        return Math.max(0, Math.round(price * 100));
    }

    private static int countProducts(List<Order> orders) {
        if (orders == null) return 0;
        return orders.stream()
                .mapToInt(o -> o == null || o.getProducts() == null ? 0
                        : o.getProducts().stream().mapToInt(ProductToOrder::getQuantity).sum())
                .sum();
    }

    private static IngredientDto requireIngredient(Map<Long, IngredientDto> ingredientMap, Long id) {
        IngredientDto i = ingredientMap.get(id);
        if (i == null) throw new OrderRejectedException(400, "Ingrediente non trovato: " + id);
        return i;
    }

    private Long resolveAgencyByLocalname(String localname) {
        if (localname == null || localname.isBlank()) return null;
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(u -> u.getIdAgency()).orElse(null);
    }
}
