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

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AvailabilityUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Inviato dopo il commit: chi riceve l'evento non deve poter leggere il valore vecchio. */
    public void send(long idAgency, Kind kind, long id, boolean available) {
        String json = "{\"idAgency\":" + idAgency + ",\"kind\":\"" + kind.name() + "\",\"id\":" + id + ",\"available\":" + available + "}";
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
