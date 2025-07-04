package com.modules.tablemodule.kafka;

import com.modules.servletconfiguration.kafka.TableKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableUpdateProducer implements TableKafkaProducer {

    private static final String TOPIC = "table-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TableUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}