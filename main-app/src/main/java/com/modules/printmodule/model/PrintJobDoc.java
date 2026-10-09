package com.modules.printmodule.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Job di stampa. Il contenuto è renderizzato al momento dell'accodamento (vedi TicketRenderer):
 * ogni riga ha un prefisso di stile di 2 caratteri ("N|", "B|", "C|", "H|").
 */
@Getter
@Setter
@Document(collection = "print_job")
public class PrintJobDoc {

    @Id
    private String id;
    private Long idAgency;
    private String printerId;
    private String comandId;
    private PrintJobKind kind;
    private PrintJobStatus status = PrintJobStatus.PENDING;
    private int attempts;
    private Instant createdAt;
    private Instant sentAt;
    private Instant printedAt;
    private String lastError;
    private int copies = 1;
    /** Valorizzata solo per NEW_ORDER: printerId:comandId:NEW_ORDER. Indice unique sparse → idempotenza. */
    private String dedupKey;
    private List<String> content = new ArrayList<>();
}
