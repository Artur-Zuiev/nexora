package com.nexora.os.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;

class LoginAttemptFilterTest {
    @Test
    void fiveFailuresCauseTemporary429AndLaterExpire() throws Exception {
        MutableClock clock = new MutableClock();
        LoginAttemptFilter filter = new LoginAttemptFilter(clock);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, call(filter, "alex", "wrong", "192.0.2.1", 401).getStatus());
        }
        MockHttpServletResponse blocked = call(filter, "alex", "wrong", "192.0.2.1", 200);
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        clock.advance(Duration.ofMinutes(16));
        assertEquals(200, call(filter, "alex", "correct", "192.0.2.1", 200).getStatus());
    }

    @Test
    void successfulAuthenticationResetsFailureCounter() throws Exception {
        LoginAttemptFilter filter = new LoginAttemptFilter();
        for (int i = 0; i < 4; i++) {
            assertEquals(401, call(filter, "member", "bad", "192.0.2.2", 401).getStatus());
        }
        assertEquals(200, call(filter, "member", "valid", "192.0.2.2", 200).getStatus());
        for (int i = 0; i < 4; i++) {
            assertEquals(401, call(filter, "member", "bad", "192.0.2.2", 401).getStatus());
        }
        assertEquals(200, call(filter, "member", "valid", "192.0.2.2", 200).getStatus());
    }

    @Test
    void usernameAndObservedAddressAreIndependent() throws Exception {
        LoginAttemptFilter filter = new LoginAttemptFilter();
        for (int i = 0; i < 5; i++) call(filter, "member", "bad", "192.0.2.3", 401);
        assertEquals(429, call(filter, "member", "bad", "192.0.2.3", 200).getStatus());
        assertEquals(200, call(filter, "member", "valid", "192.0.2.4", 200).getStatus());
        assertEquals(200, call(filter, "other", "valid", "192.0.2.3", 200).getStatus());
    }

    @Test
    void noCredentialsDoNotCountAsFailedPasswordAttempt() throws Exception {
        LoginAttemptFilter filter = new LoginAttemptFilter();
        for (int i = 0; i < 8; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/leads");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response,
                (req, res) -> ((HttpServletResponse) res).setStatus(401));
            assertEquals(401, response.getStatus());
        }
    }

    private static MockHttpServletResponse call(LoginAttemptFilter filter, String user,
            String password, String ip, int downstreamStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/leads");
        request.setRemoteAddr(ip);
        String token = Base64.getEncoder().encodeToString(
            (user + ":" + password).getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response,
            (req, res) -> ((HttpServletResponse) res).setStatus(downstreamStatus));
        return response;
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-11-14T10:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
