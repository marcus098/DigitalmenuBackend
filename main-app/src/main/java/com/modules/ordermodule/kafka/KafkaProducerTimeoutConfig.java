package com.modules.ordermodule.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Il producer Kafka è autoconfigurato da Spring Boot. Di default send() può bloccare il thread
 * della richiesta fino a 60s (max.block.ms) se il broker non è raggiungibile: lo riduciamo così
 * che un Kafka down non congeli le API (l'evento viene perso e loggato dal callback di invio).
 */
@Configuration
public class KafkaProducerTimeoutConfig {

    private static final int MAX_BLOCK_MS = 2000;

    @Bean
    public DefaultKafkaProducerFactoryCustomizer shortMaxBlockProducerCustomizer() {
        return factory -> factory.updateConfigs(Map.of(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS));
    }
}
