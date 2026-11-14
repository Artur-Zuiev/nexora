package com.nexora.os.auth;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Authenticated identity with tenant membership read from the server database. */
public class NexoraPrincipal extends User {
    private final String tenantId;
    private final AppUser.Role role;
    private final String credentialFingerprint;

    public NexoraPrincipal(AppUser user) {
        super(user.username, user.passwordHash, user.enabled, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())));
        this.tenantId = user.tenantId;
        this.role = user.role;
        this.credentialFingerprint = fingerprint(user.passwordHash);
    }

    /** Non-secret change detector retained even after Spring erases credentials. */
    public String credentialFingerprint() { return credentialFingerprint; }

    public static String fingerprint(String encodedPassword) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encodedPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public String role() { return role.name(); }

    public String tenantId() {
        return tenantId;
    }
}
