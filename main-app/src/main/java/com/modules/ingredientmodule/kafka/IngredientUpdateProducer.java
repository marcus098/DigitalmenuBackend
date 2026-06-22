package com.modules.ingredientmodule.kafka;

import com.modules.servletconfiguration.kafka.IngredientKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngredientUpdateProducer implements IngredientKafkaProducer {

    private static final String TOPIC = "ingredient-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public IngredientUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}