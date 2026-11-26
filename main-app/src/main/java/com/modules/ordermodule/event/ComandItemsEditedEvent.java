package com.modules.ordermodule.event;

import com.modules.common.model.ComandItemChange;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Published (Spring ApplicationEvent) after the lines of an existing comand have been changed and persisted.
 * {@code changes} = differenze calcolate (mai vuota); {@code editedBy} = nome di chi ha modificato.
 * Usato dalla stampa per lo scontrino "VARIAZIONE ORDINE" in cucina.
 */
public record ComandItemsEditedEvent(String comandId, String idAgency, List<ComandItemChange> changes,
                                     String editedBy, LocalDateTime editedAt) {
}
