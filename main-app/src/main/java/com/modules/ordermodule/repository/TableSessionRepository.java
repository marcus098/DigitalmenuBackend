package com.modules.ordermodule.repository;

import com.modules.common.model.enums.SessionStatus;
import com.modules.ordermodule.model.TableSessionJpa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableSessionRepository extends MongoRepository<TableSessionJpa, String> {
    Optional<TableSessionJpa> findByTableIdAndStatus(long tableId, SessionStatus status);
}
