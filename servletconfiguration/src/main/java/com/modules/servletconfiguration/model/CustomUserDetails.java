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

    public CustomUserDetails(UserDto userDto) {
        this.username = userDto.getUsername();
        this.role = userDto.getRole();
        this.id = userDto.getId();
        this.email = userDto.getEmail();
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

