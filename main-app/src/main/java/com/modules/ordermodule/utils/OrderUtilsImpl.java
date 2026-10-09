package com.modules.ordermodule.utils;

import com.modules.common.finders.OrderUtils;
import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.service.ComandStatusUpdater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderUtilsImpl implements OrderUtils {
    @Autowired
    private MongoComandLogRepository mongoComandLogRepository;
    @Autowired
    private MongoComandRepository comandRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

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

    @Override
    public long updateStatusIfIn(List<String> comandIds, long idAgency, List<ComandStatus> fromStatuses, ComandStatus newStatus) {
        if (comandIds == null || comandIds.isEmpty()) return 0;
        Query q = new Query(Criteria.where("_id").in(comandIds)
                .and("idAgency").is(idAgency)
                .and("status").in(fromStatuses.stream().map(Enum::name).toList()));
        Update u = new Update().set("status", newStatus.name()).set("updatedAt", LocalDateTime.now());
        return mongoTemplate.updateMulti(q, u, ComandStatusUpdater.COLLECTION).getModifiedCount();
    }
}
