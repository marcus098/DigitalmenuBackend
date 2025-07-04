package com.modules.productmodule.kafka;

import com.modules.servletconfiguration.kafka.ProductsKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductUpdateProducer implements ProductsKafkaProducer {

    private static final String TOPIC = "product-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ProductUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}