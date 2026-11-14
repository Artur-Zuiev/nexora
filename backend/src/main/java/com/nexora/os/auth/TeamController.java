package com.nexora.os.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only tenant admins may view their organization's team roster. */
@RestController
@RequestMapping("/api/team")
public class TeamController {
    private final AppUserRepository users;

    public TeamController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<TeamMember> list(@AuthenticationPrincipal NexoraPrincipal principal) {
        return users.findByTenantIdOrderByUsernameAsc(principal.tenantId()).stream()
                .map(TeamMember::from).toList();
    }

    /** Do not expose password hashes or login secrets in any team response. */
    public record TeamMember(Long id, String username, String role, boolean enabled, Instant createdAt) {
        static TeamMember from(AppUser user) {
            return new TeamMember(user.id, user.username, user.role.name(), user.enabled, user.createdAt);
        }
    }
}
