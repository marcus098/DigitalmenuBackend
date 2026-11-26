package com.modules.printmodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.event.ComandCreatedEvent;
import com.modules.ordermodule.event.ComandItemsEditedEvent;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Ascolta gli eventi di dominio delle comande e accoda le stampe (asincrono: non rallenta l'ordine). */
@Component
public class PrintEventListener {

    private final PrintJobService printJobService;

    public PrintEventListener(PrintJobService printJobService) {
        this.printJobService = printJobService;
    }

    @Async
    @EventListener
    public void onComandCreated(ComandCreatedEvent event) {
        handle(event.comandId(), event.idAgency(), PrintJobService.Trigger.CREATED);
    }

    @Async
    @EventListener
    public void onComandStatusChanged(ComandStatusChangedEvent event) {
        if (isAccepted(event.oldStatus(), event.newStatus())) {
            handle(event.comandId(), event.idAgency(), PrintJobService.Trigger.ACCEPTED);
        }
    }

    /** Righe modificate: "VARIAZIONE ORDINE" sulle stampanti che hanno già la comanda (vedi enqueueVariation). */
    @Async
    @EventListener
    public void onComandItemsEdited(ComandItemsEditedEvent event) {
        try {
            if (event.comandId() == null || event.idAgency() == null) return;
            printJobService.enqueueVariation(event.comandId(), Long.parseLong(event.idAgency().trim()), event.changes(),
                    event.editedBy(), event.editedAt());
        } catch (Exception e) {
            ErrorLog.logger.error("Stampa: errore accodamento variazione comanda " + event.comandId(), e);
        }
    }

    static boolean isAccepted(ComandStatus oldStatus, ComandStatus newStatus) {
        return newStatus == ComandStatus.PROGRESS && (oldStatus == ComandStatus.AWAIT || oldStatus == ComandStatus.PENDING);
    }

    private void handle(String comandId, String idAgency, PrintJobService.Trigger trigger) {
        try {
            if (comandId == null || idAgency == null) return;
            printJobService.enqueueForComand(comandId, Long.parseLong(idAgency.trim()), trigger);
        } catch (Exception e) {
            ErrorLog.logger.error("Stampa: errore accodamento comanda " + comandId, e);
        }
    }
}
