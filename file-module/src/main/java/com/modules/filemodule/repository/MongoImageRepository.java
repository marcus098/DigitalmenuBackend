package com.modules.filemodule.repository;

import com.modules.common.dto.ImageDto;
import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoImageRepository extends MongoRepository<EntityLog<ImageDto>, String> {
}
