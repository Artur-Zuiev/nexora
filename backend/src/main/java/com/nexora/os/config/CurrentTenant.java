package com.nexora.os.config;

import com.nexora.os.auth.NexoraPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the tenant strictly from the authenticated database identity.
 * Never use a request parameter/body as the organization selection source.
 */
@Component
public class CurrentTenant {
    public static final String LOCAL_ORGANIZATION = "local-admin";

    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof NexoraPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.tenantId();
    }
}
