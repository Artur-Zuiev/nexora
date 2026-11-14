package com.nexora.os.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Local-only HTTP Basic account management. No public password reset endpoint.
 */
@RestController
public class AccountController {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    public AccountController(AppUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @GetMapping("/api/me")
    public Map<String, String> me(@AuthenticationPrincipal NexoraPrincipal principal) {
        return Map.of(
                "username", principal.getUsername(),
                "organizationId", principal.tenantId(),
                "role", principal.role()
        );
    }

    @PostMapping("/api/me/password")
    @Transactional
    public void changePassword(@AuthenticationPrincipal NexoraPrincipal principal,
                               @Valid @RequestBody ChangePassword request) {
        AppUser user = users.findByUsername(principal.getUsername())
                .filter(account -> account.enabled && account.tenantId.equals(principal.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));

        if (!passwords.matches(request.currentPassword(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwords.matches(request.newPassword(), user.passwordHash)
                || request.currentPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        user.passwordHash = passwords.encode(request.newPassword());
        users.saveAndFlush(user);
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}
}
