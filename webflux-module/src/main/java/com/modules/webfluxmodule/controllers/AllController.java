package com.modules.webfluxmodule.controllers;

import com.modules.webfluxmodule.models.ListToExport;
import com.modules.webfluxmodule.models.db.ComandReactive;
import com.modules.webfluxmodule.models.db.Users;
import com.modules.webfluxmodule.services.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RequestMapping("/api")
@RestController
public class AllController {

    private final AllService allService;
    private final NotificationSinkManager sinkManager;
    private final AgencyService agencyService;
    private final JwtService jwtService;
    private final UserService userService;

    public AllController(AllService allService,
                         NotificationSinkManager sinkManager,
                         AgencyService agencyService,
                         JwtService jwtService,
                         UserService userService) {
        this.allService = allService;
        this.sinkManager = sinkManager;
        this.agencyService = agencyService;
        this.jwtService = jwtService;
        this.userService = userService;
    }


    private Mono<Users> getAuthenticatedUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(auth -> (Users) auth.getPrincipal())
                .onErrorResume(e -> Mono.empty());
    }

    // --------------------------------------------------------------------
    // ------------- ENDPOINT REST STANDARD (INVARIATI) -------------------
    // --------------------------------------------------------------------

    @GetMapping("/dashboard/getAll")
    public Mono<ListToExport> getAllDashboard() {
        return getAuthenticatedUser()
                .flatMap(user -> {
                    Long agencyId = user.getId_agency();
                    return allService.getAll(Mono.just(user), agencyId, "", true);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato")));
    }

    @GetMapping("/comands/getCompleted/{date}")
    public Flux<ComandReactive> getCompleted(@PathVariable String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            return getAuthenticatedUser()
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato")))
                    .flatMapMany(user -> allService.getComandsByStatus(true, LocalDateTime.of(parsedDate, LocalTime.MIDNIGHT), user.getId_agency()));
        } catch (DateTimeParseException e) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato data non valido. Usa YYYY-MM-DD."));
        }
    }

    @GetMapping("/comands/getDeleted/{date}")
    public Flux<ComandReactive> getDeleted(@PathVariable String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            return getAuthenticatedUser()
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato")))
                    .flatMapMany(user -> allService.getComandsByStatus(false, LocalDateTime.of(parsedDate, LocalTime.MIDNIGHT), user.getId_agency()));
        } catch (DateTimeParseException e) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato data non valido. Usa YYYY-MM-DD."));
        }
    }

    @GetMapping("/comands/table/{tableId}")
    public Flux<ComandReactive> getComandsBySessionId(@PathVariable long tableId) {
        return getAuthenticatedUser()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato")))
                .flatMapMany(user -> allService.getComandsByTableSessionId(tableId, user.getId_agency()));
    }

    @GetMapping("/public/client/getAll/{localname}")
    public Mono<ListToExport> getAllClient(@PathVariable("localname") String localname) {
        System.out.println(localname);
        return allService.getAll(Mono.empty(), -1L, localname, false);
    }


    // --------------------------------------------------------------------
    // ------------- ENDPOINT SSE (SERVER-SENT EVENTS) --------------------
    // --------------------------------------------------------------------

    /**
     * Endpoint per lo streaming di aggiornamenti ad admin autenticati.
     */
    @GetMapping(value = "/auth/admin", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamAuthUpdates(@RequestParam(required = true) String token) {
        if (jwtService.isExpired(token)) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token scaduto"));
        }

        long userId = jwtService.extractUserID(token);
        Mono<Users> userMono = userService.loadUserById(userId);

        return userMono.flatMapMany(user -> {
            if (user == null || user.isDeleted()) {
                return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Utente non valido"));
            }

            Long agencyId = user.getId_agency();
            String sessionId = agencyId + "_" + UUID.randomUUID().toString();
            System.out.println(sessionId);
            // Registra il client e ottiene il suo sink personale
            Sinks.Many<Map<String, Object>> userSink = sinkManager.register(agencyId, sessionId);

            // Restituisce il flusso e gestisce la disconnessione
            return userSink.asFlux()
                    .doOnCancel(() -> sinkManager.unregister(agencyId, sessionId))
                    .doOnTerminate(() -> sinkManager.unregister(agencyId, sessionId));
        });
    }

    /**
     * Endpoint per lo streaming di aggiornamenti a client pubblici (non autenticati).
     * Anche questo endpoint ora usa il pattern register/unregister per coerenza.
     */
    @GetMapping(value = "/public/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamPublicUpdates(
            @RequestParam(required = false) Long idAgency,
            @RequestParam(required = false) String localname
    ) {
        // Trova l'ID dell'agenzia basandosi sui parametri forniti
        return agencyService.findByIdAgencyOrNameAndDeleted(idAgency, localname)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Azienda o nome non trovati")))
                .flatMapMany(foundAgencyId -> {
                    // Genera un ID di sessione univoco anche per il client pubblico
                    String sessionId = UUID.randomUUID().toString();

                    // Registra la connessione pubblica e ottiene un sink personale
                    Sinks.Many<Map<String, Object>> publicSink = sinkManager.register(foundAgencyId.getId(), sessionId);

                    // Restituisce il flusso e gestisce la disconnessione
                    return publicSink.asFlux()
                            .doOnCancel(() -> sinkManager.unregister(foundAgencyId.getId(), sessionId))
                            .doOnTerminate(() -> sinkManager.unregister(foundAgencyId.getId(), sessionId));
                });
    }
}