package com.modules.categorymodule.repository.mongo;

import com.modules.categorymodule.model.CategoryLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoCategoryRepository extends MongoRepository<CategoryLog, String> {
}
