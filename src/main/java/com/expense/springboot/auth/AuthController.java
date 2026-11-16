package com.expense.springboot.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(AccountService accounts, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.accounts = accounts;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) token = (CsrfToken) request.getAttribute("_csrf");
        if (token == null) throw new IllegalStateException("CSRF token was not initialized.");
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody(required = false) Credentials body) {
        if (body == null) throw new IllegalArgumentException("A registration request is required.");
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(accounts.register(body.email(), body.password())));
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody(required = false) Credentials body,
                              HttpServletRequest request, HttpServletResponse response) {
        if (body == null || body.email() == null || body.password() == null) {
            throw new IllegalArgumentException("Email and password are required.");
        }
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Rotate the anonymous session's CSRF token after authentication.
        csrfTokenRepository.saveToken(null, request, response);
        return UserResponse.from((AccountPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal AccountPrincipal user) {
        return UserResponse.from(user);
    }

    public record Credentials(String email, String password) { }
    public record CsrfResponse(String token, String headerName) { }
    public record UserResponse(long id, String email) {
        static UserResponse from(UserAccount account) { return new UserResponse(account.id(), account.email()); }
        static UserResponse from(AccountPrincipal account) { return new UserResponse(account.id(), account.email()); }
    }
}
