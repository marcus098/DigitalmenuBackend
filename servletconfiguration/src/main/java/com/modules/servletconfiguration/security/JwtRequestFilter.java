package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Request;
import com.modules.servletconfiguration.model.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.BufferedReader;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private HandlerExceptionResolver handlerExceptionResolver;
    @Lazy
    @Autowired
    private UserUtils userService;
    @Autowired
    private JwtService jwtService;


    //public JwtRequestFilter(HandlerExceptionResolver handlerExceptionResolver, UserService userService, JwtService jwtService) {
    //    this.handlerExceptionResolver = handlerExceptionResolver;
    //    this.userService = userService;
    //    this.jwtService = jwtService;
    //}

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull jakarta.servlet.FilterChain filterChain) throws ServletException, IOException {
        final String authenticationHeader = request.getHeader("Authorization");
        if (authenticationHeader == null || !authenticationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String jwt = authenticationHeader.substring(7);
            String email = jwtService.extractEmail(jwt);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (email != null && authentication == null) {
                UserDto userDto = userService.loadUserByEmail(email);
                if (jwtService.isTokenValid(jwt, userDto)) {
                    CustomUserDetails userDetails = new CustomUserDetails(userDto);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);

                }
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            System.out.println("ERRORE");
            ErrorLog.logger.error("errore", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    // Metodo per estrarre il corpo della richiesta
    private String getRequestBody(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }

    //private boolean isValidRequestBody(String requestBody) {
    //    try {
    //        Request request = new ObjectMapper().readValue(requestBody, Request.class);
    //        return request.validate();
    //    } catch (Exception e) {
    //        ErrorLog.logger.error("Errore check validate ", e);
    //        return false;
    //    }
    //}
}
