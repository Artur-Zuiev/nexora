package com.nexora.os.auth;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safe, minimal current-account metadata: no password hash is returned. */
@RestController
public class AccountController {
    @GetMapping("/api/me")
    public Map<String, String> me(@AuthenticationPrincipal NexoraPrincipal principal) {
        return Map.of(
                "username", principal.getUsername(),
                "organizationId", principal.tenantId(),
                "role", principal.role()
        );
    }
}
