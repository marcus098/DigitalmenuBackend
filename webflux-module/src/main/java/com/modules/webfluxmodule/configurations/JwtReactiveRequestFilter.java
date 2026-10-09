package com.modules.webfluxmodule.configurations;

import com.modules.webfluxmodule.services.JwtService;
import com.modules.webfluxmodule.services.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import com.modules.webfluxmodule.models.db.Users;

import java.util.Optional;

@Component
public class JwtReactiveRequestFilter implements WebFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtReactiveRequestFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Controllo dell'header Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String jwt = authHeader.substring(7);
        String email;
        try {
            email = jwtService.extractEmail(jwt);
        } catch (Exception e) {
            // Token malformato, scaduto o con firma non valida: 401 invece di propagare l'eccezione (500)
            return unauthorized(exchange);
        }

        if (email != null) {
            // Utente inesistente o token non valido per l'utente => 401
            return userService.loadUserByEmailReactive(email)
                    .filter(userDetails -> isValidSafe(jwt, userDetails))
                    .map(userDetails -> Optional.<SecurityContext>of(new SecurityContextImpl(
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            )
                    )))
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(context -> context.isPresent()
                            ? chain.filter(exchange)
                                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context.get())))
                            : unauthorized(exchange));
        }
        return chain.filter(exchange);

    }

    private boolean isValidSafe(String jwt, Users user) {
        try {
            return jwtService.isTokenValid(jwt, user);
        } catch (Exception e) {
            return false;
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
