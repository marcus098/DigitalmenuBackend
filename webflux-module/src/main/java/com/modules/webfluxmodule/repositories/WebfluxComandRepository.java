package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.ComandReactive;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

public interface WebfluxComandRepository extends ReactiveMongoRepository<ComandReactive, String> {
    Flux<ComandReactive> findByStatusInAndIdAgency(List<String> statuses, long idAgency);
    Flux<ComandReactive> findAllByStatusAndIdAgencyAndCreatedAtBetween(
            String status, long idAgency, LocalDateTime from, LocalDateTime to
    );
    Flux<ComandReactive> findAllByStatusInAndIdAgencyAndTableSessionId(List<String> statuses, long idAgency, String sessionId);
    Flux<ComandReactive> findByIdAgency(Long idAgency);
}
