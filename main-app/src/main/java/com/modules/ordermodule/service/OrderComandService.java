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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    public String addOrderFromClient(AddComandClient request) {
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
        try {
            ComandFromWaiterJpa comand = new ComandFromWaiterJpa(idAgency, table.getId(), 0L, ComandStatus.AWAIT, orders, session.getId());
            comand.setComandWaiterType(ComandWaiterType.TABLE);
            comand.setClientSessionId(request.getClientSessionId());

            Comand saved = mongoComandRepository.save(comand);
            notifyComandCreated(saved);
            return saved.getId();
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
     * @return id della comanda, oppure null per errore interno
     * @throws OrderRejectedException 400 (payload) / 409 (slot non disponibile)
     */
    public String addPublicTakeaway(long idAgency, String customerName, String customerPhone, String pickupTime, List<AddComandOrder> orders) {
        String comandId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        List<Order> orderList = orderList(idAgency, 0L, orders, comandId);
        int products = countProducts(orderList);
        TakeawaySlotService.SlotKey slotKey = takeawaySlotService.reservePublicSlot(idAgency, pickupTime, products);
        try {
            ComandFromWaiterJpa comand = new ComandFromWaiterJpa(
                    idAgency, 0L, ComandStatus.PENDING, orderList,
                    customerName, pickupTime, customerPhone
            );
            comand.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
            Comand saved = mongoComandRepository.save(comand);
            notifyComandCreated(saved);
            return saved.getId();
        } catch (Exception e) {
            takeawaySlotService.releaseSlot(slotKey, products);
            ErrorLog.logger.error("Errore aggiunta ordine asporto pubblico", e);
            return null;
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

            comandFromWaiterJpa.setStatus(comandStatus);
            mongoComandRepository.save(comandFromWaiterJpa);

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
