package com.modules.ordermodule.job;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ordermodule.service.OrderComandService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ogni minuto: annulla le comande non pagate entro 15 minuti (AWAIT_PAYMENT → DELETED) e rifiuta automaticamente
 * gli ordini "su richiesta" non gestiti entro la scadenza (AWAIT_APPROVAL → DELETED, "Nessuna risposta dal locale").
 * Le transizioni sono update condizionali: più istanze in parallelo non producono doppi effetti.
 */
@Component
public class ComandExpiryJob {

    private final OrderComandService orderComandService;

    public ComandExpiryJob(OrderComandService orderComandService) {
        this.orderComandService = orderComandService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int expired = orderComandService.expireUnpaid(now);
            if (expired > 0) ErrorLog.logger.info("Comande non pagate scadute: {}", expired);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore job scadenza comande non pagate", e);
        }
        try {
            int rejected = orderComandService.autoRejectExpiredApprovals(now);
            if (rejected > 0) ErrorLog.logger.info("Ordini su richiesta rifiutati automaticamente: {}", rejected);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore job rifiuto automatico ordini su richiesta", e);
        }
    }
}
