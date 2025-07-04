package com.modules.ordermodule.utils;

import com.modules.common.finders.OrderUtils;
import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderUtilsImpl implements OrderUtils {
    @Autowired
    private MongoComandLogRepository mongoComandLogRepository;
    @Autowired
    private MongoComandRepository comandRepository;

    @Override
    public List<Comand> findByTableSessionIdAndIdAgencyAndStatusIn(String sessionId, long idAgency, List<String> comandStatuses) {
        return comandRepository.findByTableSessionIdAndIdAgencyAndStatusIn(sessionId, idAgency, comandStatuses);
    }

    @Override
    public List<Comand> saveAll(List<Comand> comands) {
        return comandRepository.saveAll(comands);
    }

    @Override
    public List<EntityLog<?>> saveAllLogs(List<EntityLog<?>> logs) {
        return mongoComandLogRepository.saveAll(logs);
    }
}
