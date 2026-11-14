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
    private final SecurityAudit audit;

    public TeamController(AppUserRepository users, PasswordEncoder encoder, SecurityAudit audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
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
            AppUser created = users.saveAndFlush(member);
            audit.record(principal.tenantId(), principal.getUsername(),
                    SecurityAudit.Event.MEMBER_CREATED, created.username);
            return ResponseEntity.status(HttpStatus.CREATED).body(TeamMember.from(created));
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
        boolean changed = member.enabled != request.enabled();
        member.enabled = request.enabled();
        AppUser saved = users.save(member);
        if (changed) {
            audit.record(principal.tenantId(), principal.getUsername(),
                    request.enabled() ? SecurityAudit.Event.MEMBER_ENABLED
                                      : SecurityAudit.Event.MEMBER_DISABLED, saved.username);
        }
        return TeamMember.from(saved);
    }

    /**
     * Local administrator recovery for a MEMBER who cannot remember their password.
     * The admin must re-enter their own password to authorize this sensitive action.
     * This is not a public account recovery workflow.
     */
    @PostMapping("/{id}/reset-password")
    @Transactional
    public void resetMemberPassword(@AuthenticationPrincipal NexoraPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ResetMemberPassword request) {
        AppUser administrator = users.findByUsername(principal.getUsername())
                .filter(account -> account.enabled
                        && account.role == AppUser.Role.ADMIN
                        && account.tenantId.equals(principal.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (!encoder.matches(request.adminPassword(), administrator.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator password is incorrect");
        }
        AppUser member = users.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (member.role != AppUser.Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only member passwords can be reset");
        }
        if (!member.enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is disabled");
        }
        if (encoder.matches(request.newPassword(), member.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        member.passwordHash = encoder.encode(request.newPassword());
        users.saveAndFlush(member);
        audit.record(principal.tenantId(), principal.getUsername(),
                SecurityAudit.Event.MEMBER_PASSWORD_RESET, member.username);
    }

    public record ResetMemberPassword(
            @NotBlank String adminPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}

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
