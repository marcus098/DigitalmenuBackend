package com.modules.common.finders;

import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;

import java.util.List;

public interface OrderUtils {
    List<Comand> findByTableSessionIdAndIdAgencyAndStatusIn(String sessionId, long idAgency, List<String> comandStatuses);
    List<Comand> saveAll(List<Comand> comands);
    List<EntityLog<?>> saveAllLogs(List<EntityLog<?>> logs);

    /**
     * Cambio stato condizionale (solo status + updatedAt): aggiorna le comande indicate dell'agency il cui stato
     * attuale è tra {@code fromStatuses}. Non sovrascrive gli altri campi (es. paid scritto dal webhook).
     * @return numero di comande aggiornate
     */
    long updateStatusIfIn(List<String> comandIds, long idAgency, List<ComandStatus> fromStatuses, ComandStatus newStatus);

}
