package com.nexora.os.auth;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only read endpoint; the organization is taken from the principal. */
@RestController
public class SecurityAuditController {
    private final SecurityAudit audit;

    public SecurityAuditController(SecurityAudit audit) {
        this.audit = audit;
    }

    @GetMapping("/api/security/events")
    public List<SecurityAudit.EventView> recent(@AuthenticationPrincipal NexoraPrincipal principal) {
        return audit.recentForTenant(principal.tenantId());
    }
}
