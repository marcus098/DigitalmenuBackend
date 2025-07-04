package com.modules.ordermodule.service;

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
import com.modules.common.model.enums.LogOperation;
import com.modules.ordermodule.model.ComandFromWaiterJpa;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.request.AddComandOrder;
import com.modules.ordermodule.request.AddComandWaiter;
import com.modules.ordermodule.request.AddProductToOrder;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderComandService {
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

    @Transactional
    public String addOrderWaiter(AddComandWaiter addComandWaiter) {
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
                        tableEntityJpa.setSeats(1);
                        //todo meglio fare un controllo nel frontend per occupare il tavolo
                        // todo attualmente il default e' un posto, permettere di modificare i posti
                        tableEntityJpa.setSessionId(UUID.randomUUID().toString().concat(Long.toString(System.currentTimeMillis())));
                        tableEntityJpa = tableUtils.save(tableEntityJpa, idAgency);
                    }
                    String tableSessionId = tableEntityJpa.getSessionId();
                    comand = new ComandFromWaiterJpa(idAgency, addComandWaiter.getIdTable(), idUser, ComandStatus.PROGRESS, orders, tableSessionId);
                }
                case HOME -> comand = new ComandFromWaiterJpa(idAgency, idUser, ComandStatus.PROGRESS, orders,
                        addComandWaiter.getName(), addComandWaiter.getAddress(),
                        addComandWaiter.getTime(), addComandWaiter.getPhone());

                case TAKE_AWAY -> comand = new ComandFromWaiterJpa(idAgency, idUser, ComandStatus.PROGRESS, orders,
                        addComandWaiter.getName(), addComandWaiter.getTime(),
                        addComandWaiter.getPhone());

                default ->
                        throw new IllegalArgumentException("Tipo di comando non valido: " + addComandWaiter.getComandWaiterType());
            }

            //comand.setId(comandId);
            comand.setComandWaiterType(addComandWaiter.getComandWaiterType());
            Comand comand1 = (ComandFromWaiterJpa) mongoComandRepository.save(comand);

            EntityLog<Comand> comandLog = new EntityLog<>(
                    LogOperation.ADD, null, comand,
                    "function addOrderWaiter",
                    idUser, idAgency);
            mongoComandLogRepository.save(comandLog);

            return comand1.getId();
        } catch (Exception e) {
            ErrorLog.logger.error("Errore aggiunta ordine da waiter", e);
            return null;
        }
    }

    @Transactional
    public List<Order> orderList (long idAgency, long idUser, List<AddComandOrder> orders, String comandId) {

        Set<Long> productIds = new HashSet<>();
        Set<Long> ingredientIds = new HashSet<>();

        for (AddComandOrder order : orders) {
            for (AddProductToOrder p : order.getProducts()) {
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
                CategoryDto category = categoryMap.get(product.getIdCategory());

                List<IngredientOrder> ingredientsMinus = prod.getIngredientsMinus().stream()
                        .map(id -> {
                            IngredientDto i = ingredientMap.get(id);
                            IngredientOrder io = new IngredientOrder();
                            io.setId(i.getId());
                            io.setName(i.getName());
                            return io;
                        }).collect(Collectors.toList());

                List<IngredientOrderPlus> ingredientsPlus = prod.getIngredientsPlus().stream()
                        .map(id -> {
                            IngredientDto i = ingredientMap.get(id);
                            IngredientOrderPlus io = new IngredientOrderPlus();
                            io.setId(i.getId());
                            io.setName(i.getName());
                            io.setPrice(i.getPrice());
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
                pto.setProductOption(product.getOptions().get(0));

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

    @Transactional
    public boolean addOrderFromHome() {
        return false;
    }

    @Transactional
    public boolean addOrderTakeAway() {
        return false;
    }

    @Transactional
    public boolean addOrderFromTable() {
        return false;
    }

    @Transactional
    public CompletableFuture<Integer> changeStatusToComand(String idComand, ComandStatus comandStatus) {
        Executor delegateExecutor = Executors.newCachedThreadPool();
        Executor executor = new DelegatingSecurityContextExecutor(delegateExecutor);
        return CompletableFuture.supplyAsync(() -> {
            try {
                long idUser = authUserProvider.getUserId();
                long idAgency = authUserProvider.getAgencyId();

                Optional<ComandJpa> comand = mongoComandReadRepository.findById(idComand);
                if (comand.isEmpty()) {
                    return 404;
                }
                //ComandFromWaiterJpa comandFromWaiterJpa = (ComandFromWaiterJpa) (Comand) comand.get();
                ComandJpa comandFromWaiterJpa = comand.get();
                ComandStatus oldStatus = comandFromWaiterJpa.getStatus();

                comandFromWaiterJpa.setStatus(comandStatus);

                CompletableFuture<Void> saveCommand = CompletableFuture.runAsync(() -> {
                    mongoComandRepository.save(comandFromWaiterJpa);
                });

                CompletableFuture<Void> saveLog = CompletableFuture.runAsync(() -> {
                    EntityLog<?> comandLog = new EntityLog<>(
                            LogOperation.OTHER, oldStatus, comandStatus,
                            "function changeStatus for command " + comandFromWaiterJpa.getId(),
                            idUser, idAgency);
                    mongoComandLogRepository.save(comandLog);
                });

                // Attendo che entrambe le operazioni siano completate
                CompletableFuture<Void> allOf = CompletableFuture.allOf(saveCommand, saveLog);
                allOf.join();  // Questo blocca fino al completamento di tutte le operazioni asincrone

            } catch (Exception e) {
                ErrorLog.logger.error("Errore cambio stato comanda con id " + idComand, e);
                return 400;
            }

            return 200;
        }, executor);
    }


}
