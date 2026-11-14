package com.nexora.os.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local-only, in-memory login throttling before Spring HTTP Basic.
 * Keyed by normalized username + directly observed remote address.
 * Never stores or logs the password or Authorization header.
 *
 * NOT a distributed or production rate limiter: counters reset on restart,
 * concurrent requests are only approximately limited, and username/IP
 * combinations can be varied to evade this protection.
 */
public final class LoginAttemptFilter extends OncePerRequestFilter {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Clock clock;
    private final ConcurrentHashMap<String, Failure> attempts = new ConcurrentHashMap<>();

    public LoginAttemptFilter() {
        this(Clock.systemUTC());
    }

    LoginAttemptFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = basicUsername(request.getHeader("Authorization"));
        if (username == null) {
            chain.doFilter(request, response);
            return;
        }
        String address = request.getRemoteAddr(); // Do not trust user-controlled X-Forwarded-For.
        String key = address + ":" + username;
        Instant now = clock.instant();
        Failure previous = attempts.get(key);
        if (previous != null && !now.isBefore(previous.expiresAt())) {
            attempts.remove(key, previous);
            previous = null;
        }
        if (previous != null && previous.count() >= MAX_FAILURES) {
            long seconds = Math.max(1, Duration.between(now, previous.expiresAt()).toSeconds());
            response.setHeader("Retry-After", Long.toString(seconds));
            response.sendError(429, "Too many failed authentication attempts");
            return;
        }

        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            attempts.compute(key, (ignored, old) -> {
                Instant timestamp = clock.instant();
                if (old == null || !timestamp.isBefore(old.expiresAt())) {
                    return new Failure(1, timestamp.plus(WINDOW));
                }
                return new Failure(Math.min(MAX_FAILURES, old.count() + 1), old.expiresAt());
            });
            // Bound in-memory bookkeeping. A saturated map is cleared instead
            // of accumulating unbounded attacker-controlled usernames.
            if (attempts.size() > MAX_TRACKED_KEYS) {
                attempts.clear();
            }
        } else if (response.getStatus() != 429 && response.getStatus() < 500) {
            // A successfully authenticated request (including 403 for lack
            // of permission) is enough to clear stale failure counts.
            attempts.remove(key);
        }
    }

    private static String basicUsername(String authorization) {
        if (authorization == null || authorization.length() > 1024
                || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(6).trim());
            String pair = new String(decoded, StandardCharsets.UTF_8);
            int delimiter = pair.indexOf(':');
            if (delimiter < 1) return null;
            String username = pair.substring(0, delimiter).trim().toLowerCase(Locale.ROOT);
            return username.length() <= 120 && !username.isBlank() ? username : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record Failure(int count, Instant expiresAt) {}
}
