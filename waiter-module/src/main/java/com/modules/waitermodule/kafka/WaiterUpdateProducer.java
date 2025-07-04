package com.modules.waitermodule.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class WaiterUpdateProducer {

    private static final String TOPIC = "waiter-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public WaiterUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

//    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}