package com.modules.webfluxmodule.services;

import com.modules.webfluxmodule.models.db.Users;
import com.modules.webfluxmodule.repositories.WebfluxUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WaiterService {
    @Autowired
    private WebfluxUserRepository webfluxUserRepository;

    public Mono<Users> loadUserByIdR2dbc(long userId) {
        // Recupera l'utente dal database in modo reattivo
        return webfluxUserRepository.findByIdAndDeleted(userId, false);
    }

    public Mono<Users> loadUserByEmailReactive(String email) {
        return webfluxUserRepository.findByEmailAndDeleted(email, false);
    }
}
