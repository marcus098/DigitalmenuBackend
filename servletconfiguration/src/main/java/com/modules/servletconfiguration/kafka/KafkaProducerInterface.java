package com.modules.servletconfiguration.kafka;

public interface KafkaProducerInterface {
    void sendUpdate(String productJson);
}
