package com.modules.pushmodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

/**
 * Notifiche push ai dispositivi dello staff del locale (app "Sala"), anche ad app chiusa.
 * Solo per le richieste dei clienti che richiedono attenzione: mai per quanto inserito dallo staff stesso.
 * I metodi tornano subito (l'invio è asincrono) e non sollevano mai eccezioni: un problema con le push non deve
 * mai far fallire la creazione di un ordine o di una prenotazione.
 */
@Service
public class StaffPushNotifier {

    @Autowired
    private StaffPushDispatcher dispatcher;

    /**
     * Nuovo ordine di un cliente (tavolo/QR, asporto, domicilio, da approvare, prepagato appena confermato).
     * Le comande stanno su MongoDB standalone (nessuna transazione da attendere: sono già salvate). Non si passa da
     * afterCommit di proposito: questo metodo viene chiamato anche dal listener AFTER_COMMIT dei pagamenti, dove una
     * nuova TransactionSynchronization non verrebbe più eseguita.
     */
    public void newOrder(long idAgency, List<String> comandIds) {
        try {
            List<String> ids = comandIds == null ? List.of() : comandIds.stream().filter(Objects::nonNull).toList();
            if (ids.isEmpty()) return;
            dispatcher.newOrder(idAgency, ids);
        } catch (Exception e) {
            ErrorLog.logger.warn("PUSH: impossibile accodare la notifica ordine (agency {}): {}", idAgency, e.getMessage());
        }
    }

    public void newOrder(long idAgency, String comandId) {
        if (comandId != null) newOrder(idAgency, List.of(comandId));
    }

    /** Nuova prenotazione dal sito pubblico: inviata dopo il commit della transazione JPA, se presente. */
    public void newReservation(ReservationJpa reservation) {
        try {
            if (reservation == null) return;
            long idAgency = reservation.getIdAgency();
            PushContent content = StaffPushMessages.reservation(reservation);
            afterCommit(() -> dispatcher.send(idAgency, content));
        } catch (Exception e) {
            ErrorLog.logger.warn("PUSH: impossibile accodare la notifica prenotazione: {}", e.getMessage());
        }
    }

    /** Come in AvailabilityUpdateProducer: chi riceve la notifica deve già poter leggere il dato salvato. */
    static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        ErrorLog.logger.warn("PUSH: invio dopo commit fallito: {}", e.getMessage());
                    }
                }
            });
        } else {
            task.run();
        }
    }
}
