package com.modules.ordermodule.kafka;

import com.modules.common.logs.errorlog.ErrorLog;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableSessionUpdateProducer {

    public static final String TOPIC = "table-session-updated";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TableSessionUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendUpdate(String sessionId, String type) {
        try {
            String json = String.format("{\"sessionId\":\"%s\",\"type\":\"%s\"}", sessionId, type);
            this.kafkaTemplate.send(TOPIC, sessionId, json);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio kafka table-session event sessionId=" + sessionId, e);
        }
    }
}
