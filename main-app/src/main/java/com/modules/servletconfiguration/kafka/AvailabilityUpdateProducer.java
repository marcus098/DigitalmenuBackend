package com.modules.servletconfiguration.kafka;

import com.modules.common.logs.errorlog.ErrorLog;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Esaurito" in tempo reale: quando cambia la disponibilità di un piatto, una categoria o un ingrediente
 * l'evento arriva a webflux, che lo inoltra a dashboard, app staff e menu dei clienti collegati.
 * Messaggio: {"idAgency":1,"kind":"PRODUCT","id":12,"available":false}
 */
@Service
public class AvailabilityUpdateProducer {

    public static final String TOPIC = "availability-updated";

    public enum Kind { PRODUCT, CATEGORY, INGREDIENT }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AvailabilityUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Inviato dopo il commit: chi riceve l'evento non deve poter leggere il valore vecchio. */
    public void send(long idAgency, Kind kind, long id, boolean available) {
        publish(idAgency, kind, id, "{\"idAgency\":" + idAgency + ",\"kind\":\"" + kind.name() + "\",\"id\":" + id + ",\"available\":" + available + "}");
    }

    /**
     * Scelta di un gruppo di varianti esaurita/ripristinata: stesso evento PRODUCT (available = disponibilità del
     * prodotto, invariata) con in più groupId, choiceId e choiceAvailable.
     */
    public void sendVariant(long idAgency, long idProduct, boolean productAvailable, String groupId, String choiceId, boolean choiceAvailable) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("idAgency", idAgency);
        event.put("kind", Kind.PRODUCT.name());
        event.put("id", idProduct);
        event.put("available", productAvailable);
        event.put("groupId", groupId);
        event.put("choiceId", choiceId);
        event.put("choiceAvailable", choiceAvailable);
        String json;
        try {
            json = MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            ErrorLog.logger.warn("Evento disponibilità variante non serializzato: {}", e.getMessage());
            return;
        }
        publish(idAgency, Kind.PRODUCT, idProduct, json);
    }

    private void publish(long idAgency, Kind kind, long id, String json) {
        Runnable task = () -> {
            try {
                kafkaTemplate.send(TOPIC, Long.toString(idAgency), json);
            } catch (Exception e) {
                // Il cambio è salvato: gli altri dispositivi lo vedranno al prossimo caricamento
                ErrorLog.logger.warn("Evento disponibilità non inviato (agency {}, {} {}): {}", idAgency, kind, id, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }
}
