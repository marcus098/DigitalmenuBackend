package com.modules.waitermodule.repository.mongo;

import com.modules.common.dto.WaiterDto;
import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoWaiterRepository extends MongoRepository<EntityLog<WaiterDto>, String> {
}
