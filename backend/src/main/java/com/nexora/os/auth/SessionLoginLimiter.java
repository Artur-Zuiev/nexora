package com.nexora.os.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bounded local-process limiter for JSON session login.
 * No passwords, headers or request bodies are stored.
 */
@Component
public class SessionLoginLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, Failure> attempts = new ConcurrentHashMap<>();

    private String key(String username, String remoteAddress) {
        return remoteAddress + ":" + username.trim().toLowerCase(Locale.ROOT);
    }

    public void check(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        Failure state = attempts.get(key);
        if (state == null) return;
        if (!Instant.now().isBefore(state.expiresAt())) {
            attempts.remove(key, state);
        } else if (state.count() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts");
        }
    }

    public void failure(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        attempts.compute(key, (k, previous) -> {
            Instant now = Instant.now();
            if (previous == null || !now.isBefore(previous.expiresAt())) {
                return new Failure(1, now.plus(WINDOW));
            }
            return new Failure(Math.min(MAX_ATTEMPTS, previous.count() + 1), previous.expiresAt());
        });
        if (attempts.size() > MAX_KEYS) attempts.clear();
    }

    public void success(String username, String remoteAddress) {
        attempts.remove(key(username, remoteAddress));
    }

    private record Failure(int count, Instant expiresAt) {}
}
