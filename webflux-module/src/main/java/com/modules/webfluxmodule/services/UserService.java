package com.modules.webfluxmodule.services;

import com.modules.webfluxmodule.models.db.Users;
import com.modules.webfluxmodule.repositories.WebfluxUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {
    @Autowired
    private WebfluxUserRepository webfluxUserRepository;

    public Mono<Users> loadUserByEmailReactive(String email){
        return webfluxUserRepository.findByEmailAndDeleted(email, false);
    }

    public Mono<Users> loadUserById(long id){
        return webfluxUserRepository.findByIdAndDeleted(id, false);
    }

}
