package com.modules.ordermodule.kafka;

import com.modules.servletconfiguration.kafka.OrderKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderUpdateProducer implements OrderKafkaProducer {

    private static final String TOPIC = "order-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}