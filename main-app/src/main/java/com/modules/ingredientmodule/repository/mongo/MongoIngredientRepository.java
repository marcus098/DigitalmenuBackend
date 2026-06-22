package com.modules.ingredientmodule.repository.mongo;

import com.modules.common.dto.IngredientDto;
import com.modules.common.model.EntityLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoIngredientRepository extends MongoRepository<EntityLog<IngredientDto>, String> {
}
