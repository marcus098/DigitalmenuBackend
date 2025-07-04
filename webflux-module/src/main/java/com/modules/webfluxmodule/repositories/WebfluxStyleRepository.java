package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.StyleR2dbc;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface WebfluxStyleRepository extends R2dbcRepository<StyleR2dbc, Long> {
    Mono<StyleR2dbc> findByIdAgency (Long idAgency);
}
