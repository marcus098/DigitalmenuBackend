package com.modules.tablemodule.repository.mongo;

import com.modules.common.dto.TableDto;
import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoTableRepository extends MongoRepository<EntityLog<TableDto>, String> {
}
