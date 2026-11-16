package com.expense.springboot.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;

    public AccountUserDetailsService(UserAccountRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email).map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
