package com.nexora.os.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Local-only tenancy boundary. Do NOT use a client-supplied tenant identifier.
 * Replace the fixed mapping with an authenticated organization membership
 * before enabling multiple accounts.
 */
@Component
public class CurrentTenant {
    public static final String LOCAL_ORGANIZATION = "local-admin";

    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !auth.getName().equals("admin")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return LOCAL_ORGANIZATION;
    }
}
