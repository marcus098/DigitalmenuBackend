package com.modules.printmodule.config;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.printmodule.model.PrintJobDoc;
import com.modules.printmodule.model.PrinterDoc;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Crea gli indici di `printer` e `print_job` all'avvio (auto-index-creation è disattivato di default). */
@Component
public class PrintModuleIndexes {

    private final MongoTemplate mongo;

    public PrintModuleIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @SuppressWarnings("deprecation")
    public void ensureIndexes() {
        try {
            IndexOperations printers = mongo.indexOps(PrinterDoc.class);
            printers.ensureIndex(new Index().on("deviceTokenHash", Sort.Direction.ASC).unique().named("ux_device_token"));
            printers.ensureIndex(new Index().on("idAgency", Sort.Direction.ASC).named("ix_agency"));

            IndexOperations jobs = mongo.indexOps(PrintJobDoc.class);
            jobs.ensureIndex(new Index().on("printerId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.ASC).named("ix_printer_status_created"));
            jobs.ensureIndex(new Index().on("dedupKey", Sort.Direction.ASC).unique().sparse().named("ux_dedup"));
            jobs.ensureIndex(new Index().on("status", Sort.Direction.ASC).on("sentAt", Sort.Direction.ASC).named("ix_status_sent"));
            jobs.ensureIndex(new Index().on("printedAt", Sort.Direction.ASC).expire(Duration.ofDays(30)).named("ttl_printed"));
            jobs.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofDays(90)).named("ttl_created"));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore creazione indici printmodule", e);
        }
    }
}
