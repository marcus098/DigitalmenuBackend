package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import com.modules.servletconfiguration.exceptions.UnauthorizedException;
import com.modules.servletconfiguration.model.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
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

    public long getAgencyId() {
        return getAuthenticatedUser().getIdAgency();
    }
}
