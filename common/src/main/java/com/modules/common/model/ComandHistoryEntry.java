package com.modules.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modules.common.model.enums.ComandHistoryAction;
import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Voce dello storico di una comanda, salvata nella comanda stessa (al massimo {@link #MAX_ENTRIES} voci: le più
 * vecchie vengono scartate). {@code by} = chi ha fatto l'azione: utente dello staff {id, name}, oppure
 * {id: null, name: "Cliente"} per gli ordini dal menu e {id: null, name: "Sistema"} per job e pagamenti online.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComandHistoryEntry {
    public static final int MAX_ENTRIES = 100;
    public static final String CLIENT_NAME = "Cliente";
    public static final String SYSTEM_NAME = "Sistema";

    private LocalDateTime at;
    private ComandTakenBy by;
    private ComandHistoryAction action;
    /** Solo STATUS: stato precedente. */
    private ComandStatus from;
    /** STATUS: nuovo stato; CREATED: stato iniziale. */
    private ComandStatus to;
    /** Solo EDITED (vuota per le altre azioni). */
    private List<ComandItemChange> changes = new ArrayList<>();

    public ComandHistoryEntry() {}

    public ComandHistoryEntry(ComandHistoryAction action, ComandTakenBy by, ComandStatus from, ComandStatus to,
                              List<ComandItemChange> changes) {
        this.at = LocalDateTime.now();
        this.action = action;
        this.by = by;
        this.from = from;
        this.to = to;
        this.changes = changes == null ? new ArrayList<>() : new ArrayList<>(changes);
    }

    public static ComandHistoryEntry created(ComandTakenBy by, ComandStatus status) {
        return new ComandHistoryEntry(ComandHistoryAction.CREATED, by, null, status, null);
    }

    public static ComandHistoryEntry status(ComandTakenBy by, ComandStatus from, ComandStatus to) {
        return new ComandHistoryEntry(ComandHistoryAction.STATUS, by, from, to, null);
    }

    public static ComandHistoryEntry edited(ComandTakenBy by, List<ComandItemChange> changes) {
        return new ComandHistoryEntry(ComandHistoryAction.EDITED, by, null, null, changes);
    }

    public static ComandHistoryEntry checkout(ComandTakenBy by) {
        return new ComandHistoryEntry(ComandHistoryAction.CHECKOUT, by, null, null, null);
    }

    public static ComandTakenBy client() { return new ComandTakenBy(null, CLIENT_NAME); }

    public static ComandTakenBy system() { return new ComandTakenBy(null, SYSTEM_NAME); }

    public LocalDateTime getAt() { return at; }
    public void setAt(LocalDateTime at) { this.at = at; }
    public ComandTakenBy getBy() { return by; }
    public void setBy(ComandTakenBy by) { this.by = by; }
    public ComandHistoryAction getAction() { return action; }
    public void setAction(ComandHistoryAction action) { this.action = action; }
    public ComandStatus getFrom() { return from; }
    public void setFrom(ComandStatus from) { this.from = from; }
    public ComandStatus getTo() { return to; }
    public void setTo(ComandStatus to) { this.to = to; }
    public List<ComandItemChange> getChanges() { return changes; }
    public void setChanges(List<ComandItemChange> changes) { this.changes = changes == null ? new ArrayList<>() : changes; }
}
