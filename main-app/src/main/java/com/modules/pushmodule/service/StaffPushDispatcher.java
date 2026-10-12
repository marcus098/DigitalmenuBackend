package com.modules.pushmodule.service;

import com.modules.common.dto.TableDto;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.pushmodule.repository.PushTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lavoro asincrono delle notifiche allo staff: legge i dati, compone il messaggio e lo invia a tutti i dispositivi
 * del locale. Non va chiamato direttamente: passare da {@link StaffPushNotifier}. Ogni errore viene solo loggato.
 */
@Service
public class StaffPushDispatcher {

    /** Stati in cui un nuovo ordine richiede l'attenzione dello staff (se nel frattempo è stato annullato, niente push). */
    static final Set<ComandStatus> ATTENTION = Set.of(
            ComandStatus.AWAIT, ComandStatus.PENDING, ComandStatus.PROGRESS, ComandStatus.AWAIT_APPROVAL);

    @Autowired
    private PushTokenRepository tokenRepository;
    @Autowired
    private MongoComandReadRepository comandReadRepository;
    @Autowired
    private TableUtils tableUtils;
    @Autowired
    private ExpoPushClient expoPushClient;

    /** Nuovo ordine: una sola notifica anche se l'invio del tavolo ha creato più comande. */
    @Async
    public void newOrder(long idAgency, List<String> comandIds) {
        try {
            PushContent content = buildOrder(idAgency, comandIds);
            if (content != null) deliver(idAgency, content);
        } catch (Exception e) {
            ErrorLog.logger.warn("PUSH: notifica nuovo ordine non inviata (agency {}): {}", idAgency, e.getMessage());
        }
    }

    /** Contenuto già pronto (es. prenotazione). */
    @Async
    public void send(long idAgency, PushContent content) {
        try {
            deliver(idAgency, content);
        } catch (Exception e) {
            ErrorLog.logger.warn("PUSH: notifica non inviata (agency {}): {}", idAgency, e.getMessage());
        }
    }

    PushContent buildOrder(long idAgency, List<String> comandIds) {
        if (comandIds == null || comandIds.isEmpty()) return null;
        List<ComandJpa> comands = new ArrayList<>();
        for (String id : comandIds) {
            if (id == null) continue;
            comandReadRepository.findById(id)
                    .filter(c -> c.getIdAgency() != null && c.getIdAgency() == idAgency)
                    .filter(c -> ATTENTION.contains(c.getStatus()))
                    .ifPresent(comands::add);
        }
        if (comands.isEmpty()) return null;

        ComandJpa first = comands.get(0);
        int articles = comands.stream().mapToInt(StaffPushMessages::countArticles).sum();
        String tableName = null;
        if (first.getComandWaiterType() == ComandWaiterType.TABLE && first.getIdTable() != null && first.getIdTable() > 0) {
            tableName = tableUtils.findByIdAndIdAgencyAndDeleted(first.getIdTable(), idAgency)
                    .map(TableDto::getName).orElse(null);
        }
        return StaffPushMessages.order(first, articles, tableName);
    }

    void deliver(long idAgency, PushContent content) {
        if (content == null) return;
        List<String> tokens = tokenRepository.findStaffTokens(idAgency).stream().filter(Objects::nonNull).toList();
        if (tokens.isEmpty()) return;
        expoPushClient.send(tokens, content);
    }
}
