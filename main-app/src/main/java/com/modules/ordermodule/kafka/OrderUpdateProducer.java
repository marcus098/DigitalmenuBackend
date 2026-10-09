package com.modules.ordermodule.kafka;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.servletconfiguration.kafka.OrderKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderUpdateProducer implements OrderKafkaProducer {

    private static final String TOPIC = "order-updated";
    private static final Pattern ID_AGENCY = Pattern.compile("\"idAgency\"\\s*:\\s*\"?(\\d+)");
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Invia l'evento usando idAgency (se presente nel JSON) come chiave, così gli eventi della stessa agency restano ordinati. */
    @Override
    public void sendUpdate(String productJson) {
        Matcher m = productJson == null ? null : ID_AGENCY.matcher(productJson);
        sendUpdate(m != null && m.find() ? m.group(1) : null, productJson);
    }

    public void sendUpdate(long idAgency, String json) {
        sendUpdate(Long.toString(idAgency), json);
    }

    private void sendUpdate(String key, String json) {
        this.kafkaTemplate.send(TOPIC, key, json).whenComplete((result, ex) -> {
            if (ex != null) {
                ErrorLog.logger.error("Errore invio kafka topic " + TOPIC + " key=" + key + " payload=" + json, ex);
            }
        });
    }
}
