package com.modules.servletconfiguration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncExecutorConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        return new DelegatingSecurityContextExecutor(Executors.newFixedThreadPool(10));
    }
}

