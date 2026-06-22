package com.modules.productmodule.repository.mongo;

import com.modules.common.dto.ProductDto;
import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoProductRepository extends MongoRepository<EntityLog<ProductDto>, String> {
}
