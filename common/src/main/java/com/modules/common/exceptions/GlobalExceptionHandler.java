package com.modules.common.exceptions;

import com.modules.common.logs.errorlog.ErrorLog;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /** @PreAuthorize negato (AuthorizationDeniedException ne è sottoclasse): 403, non 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Accesso negato");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        // Eccezioni che portano già il proprio status HTTP (servlet e webflux): NoResourceFoundException /
        // NoHandlerFoundException (404 su path sconosciuti), ResponseStatusException, metodo non supportato, ecc.
        // Controllo con instanceof (non @ExceptionHandler sulle classi) perché le classi servlet non esistono in webflux.
        if (ex instanceof ErrorResponse er && !er.getStatusCode().is5xxServerError()) {
            String detail = er.getBody().getDetail();
            return ResponseEntity.status(er.getStatusCode()).headers(er.getHeaders()).body(detail != null ? detail : "");
        }
        // Log dell'eccezione, poi rispondi con 500
        ErrorLog.logger.error("Internal server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
    }
}

