package com.modules.servletconfiguration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;

/**
 * Unica configurazione dell'executor asincrono (ex AsyncConfig + AsyncExecutorConfig).
 * Il bean si chiama "taskExecutor" (nome cercato di default da @Async / Spring Boot)
 * e propaga il SecurityContext ai thread che esegue.
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig implements AsyncConfigurer {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(10);
        delegate.setMaxPoolSize(50);
        delegate.setQueueCapacity(500);
        delegate.setAllowCoreThreadTimeOut(true);
        delegate.setDaemon(true);
        delegate.setThreadNamePrefix("async-");
        delegate.initialize();
        return new DelegatingSecurityContextExecutor(delegate);
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}
