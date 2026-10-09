package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Request;
import com.modules.servletconfiguration.model.CustomUserDetails;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    @Lazy
    @Autowired(required = false)
    private ImpersonationHooks impersonationHooks;


    //public JwtRequestFilter(HandlerExceptionResolver handlerExceptionResolver, UserService userService, JwtService jwtService) {
    //    this.handlerExceptionResolver = handlerExceptionResolver;
    //    this.userService = userService;
    //    this.jwtService = jwtService;
    //}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain filterChain) throws ServletException, IOException {
        final String authenticationHeader = request.getHeader("Authorization");
        if (authenticationHeader == null || !authenticationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        // Solo la validazione del token sta nel try: le eccezioni dei controller/filtri a valle
        // non devono essere trasformate in 401.
        CustomUserDetails impersonated = null;
        Long impersonatedAgency = null;
        try {
            String jwt = authenticationHeader.substring(7);
            String email = jwtService.extractEmail(jwt);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (email != null && authentication == null) {
                UserDto userDto = userService.loadUserByEmail(email);
                if (jwtService.isTokenValid(jwt, userDto)) {
                    Claims claims = jwtService.extractAll(jwt);
                    CustomUserDetails userDetails;
                    if (JwtService.isImpersonation(claims)) {
                        // Token emesso da un superadmin: valido solo finché il superadmin esiste ed è ancora tale.
                        Long impBy = JwtService.impersonatedBy(claims);
                        if (impBy == null || impersonationHooks == null || !impersonationHooks.isActiveSuperadmin(impBy)) {
                            ErrorLog.logger.warn("Token di impersonazione rifiutato (superadmin {} non valido) su {} {}", impBy, request.getMethod(), request.getRequestURI());
                            SecurityContextHolder.clearContext();
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            return;
                        }
                        userDetails = new CustomUserDetails(userDto, impBy, JwtService.impersonatedByEmail(claims));
                        impersonated = userDetails;
                        impersonatedAgency = userDto.getIdAgency() > 0 ? userDto.getIdAgency() : null;
                    } else {
                        userDetails = new CustomUserDetails(userDto);
                    }
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    if (impersonated != null) {
                        request.setAttribute(ImpersonationPolicy.REQUEST_ATTR_IMPERSONATED_BY, impersonated.getImpersonatedBy());
                    }
                }
            }
        } catch (Exception e) {
            // Token malformato/scaduto/firma non valida: 401 senza stack trace verso il client.
            ErrorLog.logger.warn("JWT non valido su {} {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (impersonated != null && !handleImpersonation(impersonated, impersonatedAgency, request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Applica {@link ImpersonationPolicy} e registra le richieste mutanti. false = risposta già scritta (403). */
    private boolean handleImpersonation(CustomUserDetails user, Long idAgency, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String method = request.getMethod();
        String path = pathWithinApplication(request);
        if (ImpersonationPolicy.isBlocked(method, path)) {
            ErrorLog.logger.warn("Impersonazione: bloccata {} {} (superadmin {}, utente {})", method, path, user.getImpersonatedBy(), user.getId());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"" + ImpersonationPolicy.BLOCKED_MESSAGE.replace("\"", "\\\"") + "\"}");
            return false;
        }
        if (ImpersonationPolicy.isMutating(method)) {
            try {
                // asincrono: l'audit non deve mai bloccare né rallentare la richiesta
                impersonationHooks.recordImpersonatedRequest(user.getImpersonatedBy(), user.getImpersonatedByEmail(),
                        idAgency, method, path, request.getRemoteAddr());
            } catch (Exception e) {
                ErrorLog.logger.error("Impersonazione: errore audit {} {}", method, path, e);
            }
        }
        return true;
    }

    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (uri == null) return "";
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
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
