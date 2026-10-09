package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import com.modules.servletconfiguration.exceptions.UnauthorizedException;
import com.modules.servletconfiguration.model.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserProvider {
    @Autowired
    private UserUtils userUtils;

    public UserDto getAuthenticatedUser() {
        System.out.println("AuthProvider - Thread: " + Thread.currentThread().getName());
        System.out.println("AuthProvider - Auth: " + SecurityContextHolder.getContext().getAuthentication());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
            return userUtils.loadUserByUsername(customUserDetails.getUsername());
        }
        throw new UnauthorizedException("User not authenticated");
    }

    public long getUserId() {
        return getAuthenticatedUser().getId();
    }

    /**
     * Locale dell'utente autenticato. I superadmin non hanno un locale: invece di restituire un id fittizio
     * (che potrebbe finire in una query) la chiamata fallisce.
     */
    public long getAgencyId() {
        long idAgency = getAuthenticatedUser().getIdAgency();
        if (idAgency <= 0) {
            throw new AccessDeniedException("Nessun locale associato all'utente");
        }
        return idAgency;
    }

    /** Dettagli dell'utente autenticato (con eventuale superadmin che sta impersonando), null se non autenticato. */
    public CustomUserDetails getCurrentDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details;
        }
        return null;
    }
}
