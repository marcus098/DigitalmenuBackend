package com.modules.webfluxmodule.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AppConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService scheduledExecutorService() {
        // Un executor con un singolo thread è sufficiente per gestire la schedulazione dei task
        return Executors.newSingleThreadScheduledExecutor();
    }
}