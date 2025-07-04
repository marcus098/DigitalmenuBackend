package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.TableEntityR2dbc;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface WebfluxTableRepository extends R2dbcRepository<TableEntityR2dbc, Long> {
    Flux<TableEntityR2dbc> findByIdAgencyAndDeleted(Long idAgency, Boolean deleted);
    Flux<TableEntityR2dbc> findByIdAndIdAgencyAndDeleted(Long id, Long idAgency, Boolean deleted);

}
