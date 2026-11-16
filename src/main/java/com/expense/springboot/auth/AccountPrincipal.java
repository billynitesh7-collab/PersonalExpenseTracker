package com.expense.springboot.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated identity passed by Spring Security; controllers take its ID from the session principal. */
public final class AccountPrincipal implements UserDetails {
    private final long id;
    private final String email;
    private final String passwordHash;

    public AccountPrincipal(UserAccount account) {
        this.id = account.id();
        this.email = account.email();
        this.passwordHash = account.passwordHash();
    }

    public long id() { return id; }
    public String email() { return email; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
