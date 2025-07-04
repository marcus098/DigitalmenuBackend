package com.modules.webfluxmodule.configurations;

import com.modules.webfluxmodule.services.UserService;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CustomReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final UserService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public CustomReactiveAuthenticationManager(UserService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<org.springframework.security.core.Authentication> authenticate(org.springframework.security.core.Authentication authentication) {
        String username = authentication.getPrincipal().toString();
        String password = authentication.getCredentials().toString();

        return userDetailsService.loadUserByEmailReactive(username)
                .flatMap(userDetails -> {
                    if (passwordEncoder.matches(password, userDetails.getPassword())) {
                        return Mono.just(new UsernamePasswordAuthenticationToken(
                                userDetails,
                                password,
                                userDetails.getAuthorities()
                        ));
                    }
                    return Mono.empty();
                });
    }

}
