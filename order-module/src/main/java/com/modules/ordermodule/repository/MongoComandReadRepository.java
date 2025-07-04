package com.modules.ordermodule.repository;

import com.modules.ordermodule.model.ComandJpa;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoComandReadRepository extends MongoRepository<ComandJpa, String> {
}
