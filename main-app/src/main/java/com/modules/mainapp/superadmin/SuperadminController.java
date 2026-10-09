package com.modules.mainapp.superadmin;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.DataResponse;
import com.modules.mainapp.superadmin.SuperadminDtos.*;
import com.modules.servletconfiguration.model.CustomUserDetails;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

/** API della piattaforma: solo ROLE_SUPERADMIN (anche in SecurityConf). Risposte {data: ...}, errori {message}. */
@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class SuperadminController {

    private final SuperadminService service;
    private final AuthenticatedUserProvider authUserProvider;

    public SuperadminController(SuperadminService service, AuthenticatedUserProvider authUserProvider) {
        this.service = service;
        this.authUserProvider = authUserProvider;
    }

    @GetMapping("/agencies")
    public ResponseEntity<?> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status) {
        return wrap(() -> service.listAgencies(q, status));
    }

    @GetMapping("/agencies/{id}")
    public ResponseEntity<?> detail(@PathVariable long id) {
        return wrap(() -> service.getAgency(id));
    }

    @PutMapping("/agencies/{id}/subscription")
    public ResponseEntity<?> updateSubscription(@PathVariable long id, @RequestBody(required = false) SubscriptionUpdate body,
                                                HttpServletRequest request) {
        return wrap(() -> service.updateSubscription(id, body, actor(request)));
    }

    @PostMapping("/agencies/{id}/notes")
    public ResponseEntity<?> addNote(@PathVariable long id, @RequestBody(required = false) NoteCreate body,
                                     HttpServletRequest request) {
        return wrap(() -> service.addNote(id, body, actor(request)));
    }

    @PatchMapping("/notes/{noteId}")
    public ResponseEntity<?> patchNote(@PathVariable long noteId, @RequestBody(required = false) NotePatch body) {
        return wrap(() -> service.setPinned(noteId, body));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<?> deleteNote(@PathVariable long noteId, HttpServletRequest request) {
        return wrap(() -> {
            service.deleteNote(noteId, actor(request));
            return true;
        });
    }

    @PostMapping("/agencies/{id}/impersonate")
    public ResponseEntity<?> impersonate(@PathVariable long id, @RequestBody(required = false) ImpersonateRequest body,
                                         HttpServletRequest request) {
        return wrap(() -> service.impersonate(id, body, actor(request)));
    }

    @GetMapping("/audit")
    public ResponseEntity<?> audit(@RequestParam(required = false) Long agencyId,
                                   @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return wrap(() -> service.audit(agencyId, limit));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return wrap(service::stats);
    }

    private SuperadminService.Actor actor(HttpServletRequest request) {
        CustomUserDetails details = authUserProvider.getCurrentDetails();
        if (details == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non autenticato");
        }
        return new SuperadminService.Actor(details.getId(), details.getEmail(), request.getRemoteAddr());
    }

    private static ResponseEntity<?> wrap(Supplier<Object> action) {
        try {
            return ResponseEntity.ok(new DataResponse<>(action.get()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason() != null ? e.getReason() : "Errore"));
        } catch (Exception e) {
            ErrorLog.logger.error("Superadmin: errore", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Errore interno"));
        }
    }
}
