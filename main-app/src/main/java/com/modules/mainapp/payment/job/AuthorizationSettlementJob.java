package com.modules.mainapp.payment.job;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ogni 15 minuti: incassa (o annulla, se la comanda è stata eliminata) le autorizzazioni carta ferme da più di
 * {@link PaymentService#AUTO_CAPTURE_AFTER}, ben prima della scadenza dell'autorizzazione (7 giorni per le carte
 * online). Capture e cancel sono idempotenti: più istanze in parallelo non incassano due volte.
 */
@Component
public class AuthorizationSettlementJob {

    private final PaymentService paymentService;

    public AuthorizationSettlementJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 120_000)
    public void run() {
        try {
            int settled = paymentService.settleStaleAuthorizations(LocalDateTime.now());
            if (settled > 0) ErrorLog.logger.info("Autorizzazioni di pagamento chiuse dal job: {}", settled);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore job autorizzazioni di pagamento", e);
        }
    }
}
