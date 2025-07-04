package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.ImageR2dbc;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface WebfluxImageRepository extends R2dbcRepository<ImageR2dbc, Long> {
    Flux<ImageR2dbc> findByIdAgencyAndDeleted(Long idAgency, Boolean deleted);

}
