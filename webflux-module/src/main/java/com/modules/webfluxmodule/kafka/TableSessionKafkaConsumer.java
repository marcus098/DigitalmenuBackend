package com.modules.webfluxmodule.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.webfluxmodule.services.TableSessionSinkManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableSessionKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final TableSessionSinkManager sinkManager;

    public TableSessionKafkaConsumer(ObjectMapper objectMapper, TableSessionSinkManager sinkManager) {
        this.objectMapper = objectMapper;
        this.sinkManager = sinkManager;
    }

    @KafkaListener(topics = "table-session-updated", groupId = "table-session-sse-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void listen(List<String> messages) {
        for (String json : messages) {
            try {
                Map<String, Object> event = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                Object sid = event.get("sessionId");
                Object type = event.get("type");
                if (sid == null || type == null) continue;
                Map<String, Object> payload = Map.of(
                        "type", type.toString(),
                        "sessionId", sid.toString()
                );
                sinkManager.broadcast(sid.toString(), payload);
            } catch (Exception e) {
                ErrorLog.logger.error("Errore parsing table-session-updated: " + json, e);
            }
        }
    }
}
