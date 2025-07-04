package com.modules.common.finders;

import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;

import java.util.List;

public interface OrderUtils {
    List<Comand> findByTableSessionIdAndIdAgencyAndStatusIn(String sessionId, long idAgency, List<String> comandStatuses);
    List<Comand> saveAll(List<Comand> comands);
    List<EntityLog<?>> saveAllLogs(List<EntityLog<?>> logs);

}
