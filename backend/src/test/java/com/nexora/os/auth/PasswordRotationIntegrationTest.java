package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.nexora.os.config.SessionTestSupport;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
@Transactional
class PasswordRotationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    private String makeMember(String password) {
        String username = "test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (tenant_id, username, password_hash, role) " +
                "VALUES ('local-admin', ?, ?, 'MEMBER')", username, encoder.encode(password));
        return username;
    }

    private String body(String current, String replacement) throws Exception {
        return json.writeValueAsString(Map.of("currentPassword", current, "newPassword", replacement));
    }

    @Test
    void successfulRotationMakesPreviousPasswordInvalid() throws Exception {
        String previous = "previous-password-123";
        String replacement = "replacement-password-456";
        String username = makeMember(previous);

        mvc.perform(post("/api/me/password")
                .with(SessionTestSupport.asUser(mvc, username, previous))
                .contentType("application/json")
                .content(body(previous, replacement)))
                .andExpect(status().isOk());

        String hashed = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE username = ?",
                String.class, username);
        assertNotEquals(replacement, hashed);
        assertTrue(encoder.matches(replacement, hashed));
        assertFalse(encoder.matches(previous, hashed));

        mvc.perform(get("/api/me").with(SessionTestSupport.asUser(mvc, username, previous)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(SessionTestSupport.asUser(mvc, username, replacement)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordCannotChangeCredentials() throws Exception {
        String previous = "previous-password-123";
        String username = makeMember(previous);
        mvc.perform(post("/api/me/password")
                .with(SessionTestSupport.asUser(mvc, username, previous))
                .contentType("application/json")
                .content(body("incorrect-password-xyz", "replacement-password-456")))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/me").with(SessionTestSupport.asUser(mvc, username, previous)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsSameAndTooShortPasswords() throws Exception {
        String previous = "previous-password-123";
        String username = makeMember(previous);
        mvc.perform(post("/api/me/password")
                .with(SessionTestSupport.asUser(mvc, username, previous))
                .contentType("application/json")
                .content(body(previous, previous)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/password")
                .with(SessionTestSupport.asUser(mvc, username, previous))
                .contentType("application/json")
                .content(body(previous, "too-short")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").with(SessionTestSupport.asUser(mvc, username, previous)))
                .andExpect(status().isOk());
    }

}
