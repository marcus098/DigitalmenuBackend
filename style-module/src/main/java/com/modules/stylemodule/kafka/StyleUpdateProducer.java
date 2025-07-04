package com.modules.stylemodule.kafka;

import com.modules.servletconfiguration.kafka.StyleKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StyleUpdateProducer implements StyleKafkaProducer {

    private static final String TOPIC = "style-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public StyleUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}