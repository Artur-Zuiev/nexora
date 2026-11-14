package com.nexora.os.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Authenticated identity with tenant membership read from the server database. */
public class NexoraPrincipal extends User {
    private final String tenantId;

    public NexoraPrincipal(AppUser user) {
        super(user.username, user.passwordHash, user.enabled, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())));
        this.tenantId = user.tenantId;
    }

    public String tenantId() {
        return tenantId;
    }
}
