package com.modules.ordermodule.repository;

import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoComandLogRepository extends MongoRepository<EntityLog<?>, String> {

}
