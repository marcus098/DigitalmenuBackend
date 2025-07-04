package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.IngredientR2dbc;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface WebfluxIngredientRepository extends R2dbcRepository<IngredientR2dbc, Long> {
    Flux<IngredientR2dbc> findByIdAgencyAndDeleted(Long idAgency, Boolean deleted);

    Flux<IngredientR2dbc> findByIdAgencyAndDeletedAndAvailable(Long idAgency, Boolean deleted, Boolean available);
}
