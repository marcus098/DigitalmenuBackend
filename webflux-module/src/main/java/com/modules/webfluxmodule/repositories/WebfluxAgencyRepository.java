package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.AgencyR2dbc;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WebfluxAgencyRepository extends R2dbcRepository<AgencyR2dbc, Long> {
    Mono<AgencyR2dbc> findByNameAndDeleted(String name, Boolean deleted);
    Mono<AgencyR2dbc> findByIdAndDeleted(Long id, Boolean deleted);
    Flux<AgencyR2dbc> findAllByIdAndDeleted(Long id, Boolean deleted);
    Mono<Boolean> existsByIdOrNameAndDeleted(long id, String name, boolean deleted);
    Mono<AgencyR2dbc> findByIdOrNameAndDeleted(long id, String name, boolean deleted);
}
