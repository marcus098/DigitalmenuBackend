package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.Users;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface WebfluxUserRepository extends R2dbcRepository<Users, Long> {
    Mono<Users> findByIdAndDeleted(Long id, Boolean deleted);
    Mono<Users> findByEmailAndDeleted(String email, Boolean deleted);
}
