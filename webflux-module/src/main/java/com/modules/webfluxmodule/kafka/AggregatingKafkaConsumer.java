package com.modules.webfluxmodule.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.dto.CategoryDto;
import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.dto.TableDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.webfluxmodule.services.NotificationSinkManager;
import jakarta.annotation.PreDestroy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AggregatingKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationSinkManager sinkManager;
    private final ScheduledExecutorService scheduler;

    // Cache per aggregare gli aggiornamenti per ID agenzia
    private final ConcurrentHashMap<Long, Map<String, List<?>>> updatesCache = new ConcurrentHashMap<>();

    // Mappa per tenere traccia dei task schedulati per ogni agenzia (per il debounce)
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // Mappa per l'ultima sessione che ha originato l'aggiornamento
    private final ConcurrentHashMap<Long, String> lastOriginatingSession = new ConcurrentHashMap<>();

    public AggregatingKafkaConsumer(ObjectMapper objectMapper, NotificationSinkManager sinkManager, ScheduledExecutorService scheduler) {
        this.objectMapper = objectMapper;
        this.sinkManager = sinkManager;
        this.scheduler = scheduler;
    }

    @KafkaListener(topics = "categories-updated", groupId = "aggregator-group", containerFactory = "kafkaListenerContainerFactory")
    public void listenCategoriesBatch(List<String> categoryJsonList) {
        Map<Long, List<CategoryDto>> categoriesByAgency = categoryJsonList.stream()
                .map(this::deserializeCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(c -> Long.parseLong(c.getSessionUpdating().split("_")[0])));

        categoriesByAgency.forEach((agencyId, categories) -> {
            // 1. Aggiungi i dati alla cache
            addDataToCache(agencyId, "categories", categories);
            lastOriginatingSession.put(agencyId, categories.get(0).getSessionUpdating());
            // 2. Schedula (o rischedula) l'invio
            scheduleBroadcast(agencyId);
        });
    }

    @KafkaListener(topics = "product-updated", groupId = "aggregator-group", containerFactory = "kafkaListenerContainerFactory")
    public void listenProductsBatch(List<String> productJsonList) {
        Map<Long, List<ProductDto>> productsByAgency = productJsonList.stream()
                .map(this::deserializeProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(p -> Long.parseLong(p.getSessionUpdating().split("_")[0])));

        productsByAgency.forEach((agencyId, products) -> {
            // 1. Aggiungi i dati alla cache
            addDataToCache(agencyId, "products", products);
            lastOriginatingSession.put(agencyId, products.get(0).getSessionUpdating());
            // 2. Schedula (o rischedula) l'invio
            scheduleBroadcast(agencyId);
        });
    }

    /**
     * Aggiunge dati alla cache in modo thread-safe.
     */
    private void addDataToCache(Long agencyId, String key, List<?> data) {
        updatesCache.compute(agencyId, (k, v) -> {
            Map<String, List<?>> agencyUpdates = (v == null) ? new HashMap<>() : v;
            List existingData = agencyUpdates.getOrDefault(key, new ArrayList<>());
            existingData.addAll(data);
            agencyUpdates.put(key, existingData);
            return agencyUpdates;
        });
    }

    /**
     * Schedula un invio aggregato per una specifica agenzia, cancellando eventuali invii precedenti
     */
    private void scheduleBroadcast(Long agencyId) {
        // Cancella il task precedente, se esiste. Questo resetta il timer.
        ScheduledFuture<?> existingTask = scheduledTasks.get(agencyId);
        if (existingTask != null) {
            existingTask.cancel(false); // false = non interrompere se già in esecuzione
        }

        // Schedula il nuovo task che verrà eseguito tra 1.5 secondi
        Runnable broadcastTask = () -> {
            // Rimuovi i dati dalla cache in modo atomico
            Map<String, List<?>> payloadData = updatesCache.remove(agencyId);
            String originatingSessionId = lastOriginatingSession.remove(agencyId);

            if (payloadData != null && !payloadData.isEmpty()) {
                Map<String, Object> finalPayload = new HashMap<>();
                finalPayload.put("type", "aggregated_update");
                finalPayload.put("data", payloadData);

                sinkManager.broadcast(agencyId, originatingSessionId, finalPayload);
                System.out.println("Invio aggiornamento aggregato per agenzia (debounce): " + agencyId);
            }

            // Rimuovi il task completato dalla mappa
            scheduledTasks.remove(agencyId);
        };

        ScheduledFuture<?> newScheduledTask = scheduler.schedule(broadcastTask, 1500, TimeUnit.MILLISECONDS);

        // Memorizza il riferimento al nuovo task
        scheduledTasks.put(agencyId, newScheduledTask);
    }

    private CategoryDto deserializeCategory(String json) {
        try { return objectMapper.readValue(json, CategoryDto.class); } catch (JsonProcessingException e) { ErrorLog.logger.error("Error: " + json, e); return null; }
    }
    private ProductDto deserializeProduct(String json) {
        try { return objectMapper.readValue(json, ProductDto.class); } catch (JsonProcessingException e) { ErrorLog.logger.error("Error: " + json, e); return null; }
    }
    private TableDto deserializeTable(String json) {
        try { return objectMapper.readValue(json, TableDto.class); } catch (JsonProcessingException e) { ErrorLog.logger.error("Error: " + json, e); return null; }
    }
    private IngredientDto deserializeIngredient(String json) {
        try { return objectMapper.readValue(json, IngredientDto.class); } catch (JsonProcessingException e) { ErrorLog.logger.error("Error: " + json, e); return null; }
    }
}