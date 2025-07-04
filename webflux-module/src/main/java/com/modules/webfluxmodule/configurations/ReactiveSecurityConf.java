package com.modules.webfluxmodule.configurations;

import com.modules.webfluxmodule.services.JwtService;
import com.modules.webfluxmodule.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class ReactiveSecurityConf {

    private final JwtService jwtService;
    private final UserService userService;
    private final ReactiveAuthenticationManager reactiveAuthenticationManager;

    @Autowired
    public ReactiveSecurityConf(JwtService jwtService, ReactiveAuthenticationManager reactiveAuthenticationManager, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.reactiveAuthenticationManager = reactiveAuthenticationManager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOrigin("*");
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/public/client/**").permitAll()
                        .pathMatchers("/api/public/updates/**").permitAll()
                        .pathMatchers("/api/test").permitAll()
                        .pathMatchers("/api/auth/admin").permitAll()
                        .pathMatchers("/api/dashboard/getAll").authenticated()
                        .pathMatchers("/api/comands/*").authenticated()
                        .anyExchange().authenticated()
                )
                .authenticationManager(reactiveAuthenticationManager)
                .securityContextRepository((NoOpServerSecurityContextRepository.getInstance()))
                .addFilterAt(new JwtReactiveRequestFilter(jwtService, userService), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
