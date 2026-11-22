package com.modules.tablemodule.service;

import com.modules.common.finders.OrderUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.LogOperation;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chiusura delle comande di una sessione al tavolo quando il tavolo viene chiuso/liberato, unica per tutti i percorsi
 * (GET /api/tables/freeAndClose, GET /api/tables/deleteAndClose, POST /api/tables/{id}/session/close):
 * <ul>
 *   <li>mai accettate dalla sala (AWAIT, ordini da QR; AWAIT_PAYMENT, prepagamento non concluso) → DELETED,
 *       con {@link ComandStatusChangedEvent} così le autorizzazioni di pagamento vengono annullate;</li>
 *   <li>accettate o in cucina (PENDING, PROGRESS) → COMPLETED.</li>
 * </ul>
 * Gli update sono condizionali sullo stato letto: una transizione concorrente (es. il cameriere accetta l'ordine
 * proprio ora, o il webhook di pagamento) non viene sovrascritta.
 */
@Component
public class TableComandCloser {

    /** Mai prese in carico dallo staff: alla chiusura del tavolo vanno annullate, non incassate. */
    static final List<ComandStatus> NEVER_ACCEPTED = List.of(ComandStatus.AWAIT, ComandStatus.AWAIT_PAYMENT);
    /** Accettate o in preparazione: alla chiusura del tavolo vengono completate. */
    static final List<ComandStatus> ACCEPTED = List.of(ComandStatus.PENDING, ComandStatus.PROGRESS);

    @Autowired
    private OrderUtils orderUtils;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public record Result(int cancelled, int completed) {
        public boolean any() { return cancelled + completed > 0; }
    }

    public static ComandStatus targetStatus(ComandStatus current) {
        if (NEVER_ACCEPTED.contains(current)) return ComandStatus.DELETED;
        if (ACCEPTED.contains(current)) return ComandStatus.COMPLETED;
        return null;
    }

    /**
     * @param sessionId sessione al tavolo (tableSessionId delle comande); null/vuota = nessuna comanda
     * @param caller    descrizione per i log ("freeAndClose", "session/close", ...)
     */
    public Result closeSessionComands(String sessionId, long idAgency, long idUser, String caller) {
        if (sessionId == null || sessionId.isBlank()) return new Result(0, 0);
        List<String> statuses = new ArrayList<>();
        NEVER_ACCEPTED.forEach(s -> statuses.add(s.name()));
        ACCEPTED.forEach(s -> statuses.add(s.name()));
        List<Comand> comands = orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(sessionId, idAgency, statuses);
        if (comands == null || comands.isEmpty()) return new Result(0, 0);

        int cancelled = 0;
        int completed = 0;
        List<EntityLog<?>> logs = new ArrayList<>();
        for (Comand comand : comands) {
            ComandStatus oldStatus = comand.getStatus();
            ComandStatus newStatus = targetStatus(oldStatus);
            if (newStatus == null) continue;
            // update condizionale per singola comanda (solo status): non sovrascrive paid/paymentIntentId del webhook
            // e permette di pubblicare l'evento solo per le comande effettivamente annullate
            long moved = orderUtils.updateStatusIfIn(List.of(comand.getId()), idAgency, List.of(oldStatus), newStatus);
            if (moved <= 0) continue;
            comand.setStatus(newStatus);
            logs.add(new EntityLog<>(LogOperation.OTHER, oldStatus, newStatus,
                    "function " + caller + " (chiusura tavolo) for comand " + comand.getId(), idUser, idAgency));
            if (newStatus == ComandStatus.DELETED) {
                cancelled++;
                try {
                    // annulla eventuali pagamenti autorizzati e non incassati (PaymentEventsListener)
                    eventPublisher.publishEvent(new ComandStatusChangedEvent(comand.getId(), String.valueOf(idAgency),
                            oldStatus, ComandStatus.DELETED));
                } catch (Exception e) {
                    ErrorLog.logger.error("Errore pubblicazione ComandStatusChangedEvent comandId=" + comand.getId(), e);
                }
            } else {
                completed++;
            }
        }
        if (!logs.isEmpty()) orderUtils.saveAllLogs(logs);
        return new Result(cancelled, completed);
    }
}
