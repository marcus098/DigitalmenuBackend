package com.modules.servletconfiguration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConf {

    private final JwtRequestFilter jwtRequestFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConf(JwtRequestFilter jwtRequestFilter, AuthenticationProvider authenticationProvider) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/login").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/getAgencyName/**").permitAll()
                        .requestMatchers("/api/signupAgency").permitAll()
                        .requestMatchers("/api/signupWaiter").permitAll()
                        .requestMatchers("/api/users/confirmEmail/**").permitAll()
                        .requestMatchers("/api/users/resendEmailVerification/**").permitAll()
                        .requestMatchers("/api/categories/api/public/**").permitAll()
                        .requestMatchers("/api/categories/**").authenticated()
                        .requestMatchers("/api/products/**").authenticated()
                        .requestMatchers("/api/ingredients/**").authenticated()
                        .requestMatchers("/api/waiters/**").authenticated()
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/comands/**").authenticated()
                        .requestMatchers("/api/dashboard/getAll").authenticated()
                        .requestMatchers("/api/tables/**").authenticated()
                        .requestMatchers("/api/auth/admin/**").authenticated()
                        .requestMatchers("/api/user/check").authenticated()
                        .requestMatchers("/api/private/images/**").permitAll()
                        .requestMatchers("/api/public/client/getAll").permitAll()
                        .anyRequest().authenticated()

                ).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();

    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of("*"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
        return urlBasedCorsConfigurationSource;
    }

}
