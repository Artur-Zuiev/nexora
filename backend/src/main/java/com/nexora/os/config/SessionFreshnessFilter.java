package com.nexora.os.config;

import com.nexora.os.auth.AppUser;
import com.nexora.os.auth.AppUserRepository;
import com.nexora.os.auth.NexoraPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Invalidates browser sessions after a member is disabled or password/role is changed. */
public class SessionFreshnessFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public SessionFreshnessFilter(AppUserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Only check session-backed authentication; HTTP Basic is checked each request.
        if (request.getSession(false) != null
                && (request.getHeader("Authorization") == null)
                && authentication != null
                && authentication.getPrincipal() instanceof NexoraPrincipal principal) {
            AppUser current = users.findByUsername(principal.getUsername()).orElse(null);
            if (current == null || !current.enabled
                    || !Objects.equals(current.tenantId, principal.tenantId())
                    || !Objects.equals(current.role.name(), principal.role())
                    || !Objects.equals(current.passwordHash, principal.getPassword())) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
