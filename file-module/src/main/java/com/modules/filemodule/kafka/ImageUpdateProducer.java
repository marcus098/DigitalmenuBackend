package com.modules.filemodule.kafka;

import com.modules.servletconfiguration.kafka.ImageKafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ImageUpdateProducer implements ImageKafkaProducer {

    private static final String TOPIC = "image-updated";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ImageUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendUpdate(String productJson) {
        System.out.println(String.format("Invio messaggio al topic %s: %s", TOPIC, productJson));
        this.kafkaTemplate.send(TOPIC, productJson);
    }
}