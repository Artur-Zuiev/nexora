package com.nexora.os.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * First phase of browser-session authentication. Legacy HTTP Basic remains
 * temporarily available for existing development clients and tests.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;

    public SessionController(AuthenticationManager manager, SecurityContextRepository contexts) {
        this.manager = manager;
        this.contexts = contexts;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CSRF unavailable");
        }
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public Account login(@Valid @RequestBody LoginRequest request,
                         HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        if (!(authentication.getPrincipal() instanceof NexoraPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        servletRequest.getSession(true);
        servletRequest.changeSessionId(); // Session fixation protection for programmatic login.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, servletRequest, servletResponse);
        return new Account(principal.getUsername(), principal.tenantId(), principal.role());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record Account(String username, String organizationId, String role) {}
}
