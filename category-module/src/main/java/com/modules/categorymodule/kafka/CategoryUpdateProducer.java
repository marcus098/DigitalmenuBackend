package com.modules.categorymodule.kafka;

import com.modules.servletconfiguration.kafka.CategoriesKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CategoryUpdateProducer implements CategoriesKafkaProducer {

    private static final String TOPIC = "categories-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CategoryUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}