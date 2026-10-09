package com.modules.mainapp.payment.listener;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.repository.MongoComandRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Collega pagamenti e comande:
 * <ul>
 *   <li>{@link PaymentCompletedEvent} → marca la comanda come pagata (update atomico e condizionale su Mongo,
 *       quindi idempotente) e invia l'evento Kafka order-updated così dashboard e cassa si aggiornano;</li>
 *   <li>{@link ComandStatusChangedEvent} verso DELETED → annulla gli intent Stripe ancora aperti.</li>
 * </ul>
 * Listener sincroni: un errore Mongo in onPaymentCompleted fa fallire il webhook (5xx) e Stripe ritenta.
 */
@Component
public class PaymentEventsListener {

    static final String COMAND_COLLECTION = "comand";

    private final MongoTemplate mongoTemplate;
    private final MongoComandRepository mongoComandRepository;
    private final OrderUpdateProducer orderUpdateProducer;
    private final PaymentService paymentService;

    public PaymentEventsListener(MongoTemplate mongoTemplate,
                                 MongoComandRepository mongoComandRepository,
                                 OrderUpdateProducer orderUpdateProducer,
                                 PaymentService paymentService) {
        this.mongoTemplate = mongoTemplate;
        this.mongoComandRepository = mongoComandRepository;
        this.orderUpdateProducer = orderUpdateProducer;
        this.paymentService = paymentService;
    }

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.comandId() == null || event.comandId().isBlank()) return;

        // $set condizionale (paid != true): niente read-modify-write che possa sovrascrivere un cambio stato concorrente
        Query query = new Query(Criteria.where("_id").is(event.comandId()).and("paid").ne(true));
        Update update = new Update()
                .set("paid", true)
                .set("paidAt", LocalDateTime.now())
                .set("paymentIntentId", event.paymentIntentId());
        long modified = mongoTemplate.updateFirst(query, update, COMAND_COLLECTION).getModifiedCount();
        if (modified == 0) {
            ErrorLog.logger.info("Comanda {} già marcata pagata o inesistente: nessun aggiornamento", event.comandId());
            return;
        }

        mongoComandRepository.findById(event.comandId()).ifPresent(c -> {
            try {
                String json = String.format("{\"id\":\"%s\",\"status\":\"%s\",\"idAgency\":%d,\"paid\":true}",
                        c.getId(), c.getStatus(), c.getIdAgency());
                orderUpdateProducer.sendUpdate(c.getIdAgency(), json);
            } catch (Exception e) {
                ErrorLog.logger.error("Errore invio kafka order event (pagamento) comandId={}", event.comandId(), e);
            }
        });
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
}
