package com.modules.mainapp.payment.listener;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.PaymentAuthorizedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.PaymentRefundedEvent;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.service.OrderComandService;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Collega pagamenti e comande:
 * <ul>
 *   <li>{@link PaymentCompletedEvent} → marca la comanda come pagata (update atomico e condizionale su Mongo,
 *       quindi idempotente); se era in AWAIT_PAYMENT la porta allo stato iniziale e fa partire la pipeline "creata"
 *       (stampa + dashboard) tramite {@link OrderComandService#onPaymentCompleted};</li>
 *   <li>{@link PaymentAuthorizedEvent} → ordine "su richiesta" autorizzato: AWAIT_PAYMENT → AWAIT_APPROVAL;</li>
 *   <li>{@link PaymentRefundedEvent} → rimborso totale: paid = false, refunded = true;</li>
 *   <li>{@link ComandStatusChangedEvent} verso DELETED → annulla gli intent Stripe ancora aperti/autorizzati.</li>
 * </ul>
 * Gli eventi di pagamento sono pubblicati dentro la transazione JPA del webhook: i listener girano AFTER_COMMIT
 * (lo stato del pagamento è già persistito), con fallbackExecution per le chiamate fuori transazione (test).
 */
@Component
public class PaymentEventsListener {

    static final String COMAND_COLLECTION = "comand";

    private final MongoTemplate mongoTemplate;
    private final MongoComandRepository mongoComandRepository;
    private final OrderUpdateProducer orderUpdateProducer;
    private final PaymentService paymentService;
    private final OrderComandService orderComandService;

    public PaymentEventsListener(MongoTemplate mongoTemplate,
                                 MongoComandRepository mongoComandRepository,
                                 OrderUpdateProducer orderUpdateProducer,
                                 PaymentService paymentService,
                                 OrderComandService orderComandService) {
        this.mongoTemplate = mongoTemplate;
        this.mongoComandRepository = mongoComandRepository;
        this.orderUpdateProducer = orderUpdateProducer;
        this.paymentService = paymentService;
        this.orderComandService = orderComandService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.comandId() == null || event.comandId().isBlank()) return;

        // $set condizionale (paid != true): niente read-modify-write che possa sovrascrivere un cambio stato concorrente
        Query query = new Query(Criteria.where("_id").is(event.comandId()).and("paid").ne(true));
        Update update = new Update()
                .set("paid", true)
                .set("paidAt", LocalDateTime.now())
                .set("paymentAuthorized", false)
                .set("paymentIntentId", event.paymentIntentId());
        long modified = mongoTemplate.updateFirst(query, update, COMAND_COLLECTION).getModifiedCount();

        // Prepagamento: AWAIT_PAYMENT → stato iniziale + stampa/dashboard. Idempotente, chiamato anche se paid era
        // già true (un tentativo precedente può essersi interrotto prima della transizione).
        boolean created = false;
        try {
            created = orderComandService.onPaymentCompleted(event.comandId());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore transizione comanda prepagata {}", event.comandId(), e);
        }
        if (modified == 0) {
            ErrorLog.logger.info("Comanda {} già marcata pagata o inesistente: nessun aggiornamento", event.comandId());
            return;
        }

        mongoComandRepository.findById(event.comandId()).ifPresent(c -> {
            if (c.getStatus() == ComandStatus.DELETED) {
                ErrorLog.logger.error("Comanda {} pagata ({} cent, intent {}) ma già annullata: valutare il rimborso",
                        c.getId(), event.amountCents(), event.paymentIntentId());
            }
            // Le comande ancora nascoste (AWAIT_PAYMENT) non vanno notificate; se è appena partita la pipeline
            // "creata" il relativo evento Kafka è già stato inviato, questo aggiunge solo il flag paid.
            if (c.getStatus() == ComandStatus.AWAIT_PAYMENT) return;
            sendPaidEvent(c, true);
        });
        if (created) ErrorLog.logger.info("Comanda prepagata {} confermata", event.comandId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentAuthorized(PaymentAuthorizedEvent event) {
        if (event.comandId() == null || event.comandId().isBlank()) return;
        boolean moved = orderComandService.onPaymentAuthorized(event.comandId(), event.paymentIntentId());
        if (moved) return;
        // Autorizzazione arrivata per una comanda non più in attesa di pagamento (es. scaduta e annullata):
        // annulla l'autorizzazione così il cliente non resta con l'importo bloccato.
        mongoComandRepository.findById(event.comandId()).ifPresent(c -> {
            if (c.getStatus() == ComandStatus.DELETED) {
                try {
                    paymentService.cancelOpenIntentsForComand(c.getId());
                } catch (Exception e) {
                    ErrorLog.logger.error("Errore annullamento autorizzazione tardiva comanda {}", c.getId(), e);
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event.comandId() == null || event.comandId().isBlank() || !event.full()) return;
        // Rimborso totale: la comanda non risulta più pagata (un rimborso parziale mantiene paid)
        Query query = new Query(Criteria.where("_id").is(event.comandId()).and("refunded").ne(true));
        Update update = new Update().set("paid", false).set("refunded", true);
        if (mongoTemplate.updateFirst(query, update, COMAND_COLLECTION).getModifiedCount() == 0) return;
        mongoComandRepository.findById(event.comandId()).ifPresent(c -> sendPaidEvent(c, false));
    }

    @EventListener
    public void onComandStatusChanged(ComandStatusChangedEvent event) {
        if (event.newStatus() != ComandStatus.DELETED) return;
        try {
            paymentService.cancelOpenIntentsForComand(event.comandId());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore annullamento intent per comanda eliminata {}", event.comandId(), e);
        }
    }

    private void sendPaidEvent(Comand c, boolean paid) {
        try {
            String json = String.format("{\"id\":\"%s\",\"status\":\"%s\",\"idAgency\":%d,\"paid\":%s}",
                    c.getId(), c.getStatus(), c.getIdAgency(), paid);
            orderUpdateProducer.sendUpdate(c.getIdAgency(), json);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio kafka order event (pagamento) comandId={}", c.getId(), e);
        }
    }
}
