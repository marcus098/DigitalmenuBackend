package com.modules.mainapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduler dei job @Scheduled con più thread: di default Spring ne usa uno solo, e un job lungo
 * (traduzioni notturne, riepiloghi AI) bloccherebbe scadenza comande, stampa e promemoria prenotazioni.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setErrorHandler(t -> com.modules.common.logs.errorlog.ErrorLog.logger.error("Errore in un job pianificato", t));
        return scheduler;
    }
}
