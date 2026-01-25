package com.capstone.orderservice.security.internal;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

@Getter
public class InternalServiceAuthentication implements Authentication {

    private final String serviceName;
    private final boolean authenticated;
    private final Collection<? extends GrantedAuthority> authorities;

    public InternalServiceAuthentication(String serviceName) {
        this.serviceName = serviceName;
        this.authenticated = true;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"));
    }

    @Override
    public String getName() {
        return serviceName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return getName();
    }

    @Override
    public Object getPrincipal() {
        return getName();
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        throw new IllegalArgumentException("Cannot change authentication status");
    }
}
