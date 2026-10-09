package com.modules.servletconfiguration.model;

import com.modules.common.dto.UserDto;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final String username;
    private final long id;
    private final String email;
    private final String role;
    /** id del superadmin che sta usando questo account (token di impersonazione), null = accesso normale. */
    private final Long impersonatedBy;
    private final String impersonatedByEmail;

    public CustomUserDetails(UserDto userDto) {
        this(userDto, null, null);
    }

    public CustomUserDetails(UserDto userDto, Long impersonatedBy, String impersonatedByEmail) {
        this.username = userDto.getUsername();
        this.role = userDto.getRole();
        this.id = userDto.getId();
        this.email = userDto.getEmail();
        this.impersonatedBy = impersonatedBy;
        this.impersonatedByEmail = impersonatedByEmail;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public long getId() {
        return id;
    }

    public Long getImpersonatedBy() {
        return impersonatedBy;
    }

    public String getImpersonatedByEmail() {
        return impersonatedByEmail;
    }

    public boolean isImpersonated() {
        return impersonatedBy != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
