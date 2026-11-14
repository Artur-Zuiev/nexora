package com.nexora.os.auth;

import com.nexora.os.config.CurrentTenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Development-only bootstrap; creates the first local admin and never resets
 * an existing admin's password. No public user provisioning endpoint exists.
 */
@Component
public class LocalAdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String initialPassword;

    public LocalAdminBootstrap(AppUserRepository users, PasswordEncoder encoder,
            @Value("${NEXORA_ADMIN_PASSWORD:}") String initialPassword) {
        this.users = users;
        this.encoder = encoder;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (initialPassword == null || initialPassword.length() < 12 || initialPassword.isBlank()) {
            throw new IllegalStateException("Set NEXORA_ADMIN_PASSWORD to at least 12 characters");
        }

        if (users.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser();
            admin.username = "admin";
            admin.tenantId = CurrentTenant.LOCAL_ORGANIZATION;
            admin.passwordHash = encoder.encode(initialPassword);
            admin.role = AppUser.Role.ADMIN;
            users.save(admin);
        }
    }
}
