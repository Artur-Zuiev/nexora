package com.nexora.os.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Local development team management. SecurityConfig enforces ADMIN on every
 * /api/team route. Users cannot select another organization in the request.
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public TeamController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<TeamMember> list(@AuthenticationPrincipal NexoraPrincipal principal) {
        return users.findByTenantIdOrderByUsernameAsc(principal.tenantId()).stream()
                .map(TeamMember::from).toList();
    }

    /**
     * Only MEMBER accounts can be created by this local endpoint.
     * Passwords are accepted over localhost HTTP Basic only; do not deploy.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<TeamMember> add(@AuthenticationPrincipal NexoraPrincipal principal,
                                          @Valid @RequestBody CreateMember request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser member = new AppUser();
        member.tenantId = principal.tenantId();
        member.username = username;
        member.passwordHash = encoder.encode(request.password());
        member.role = AppUser.Role.MEMBER;
        member.enabled = true;
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(TeamMember.from(users.saveAndFlush(member)));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public TeamMember setEnabled(@AuthenticationPrincipal NexoraPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody SetEnabled request) {
        AppUser member = users.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (member.username.equals(principal.getUsername()) && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot disable your own account");
        }
        if (member.role == AppUser.Role.ADMIN && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disabling admins is not supported yet");
        }
        member.enabled = request.enabled();
        return TeamMember.from(users.save(member));
    }

    public record CreateMember(
            @NotBlank
            @Size(min = 3, max = 120)
            @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]*", message = "Use letters, numbers, dots, dashes or underscores")
            String username,
            @NotBlank @Size(min = 12, max = 128) String password
    ) {}

    public record SetEnabled(@NotNull Boolean enabled) {}

    /** Never include a user's password hash or plaintext password in responses. */
    public record TeamMember(Long id, String username, String role, boolean enabled, Instant createdAt) {
        static TeamMember from(AppUser user) {
            return new TeamMember(user.id, user.username, user.role.name(), user.enabled, user.createdAt);
        }
    }
}
