package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
@Transactional
class MemberPasswordResetIntegrationTest {
    private static final String ADMIN = "integration-test-secret-123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private long member(String tenant, String username, String password, String role) {
        return jdbc.queryForObject(
                "INSERT INTO app_users (tenant_id, username, password_hash, role) " +
                "VALUES (?,?,?,?) RETURNING id", Long.class,
                tenant, username, encoder.encode(password), role);
    }

    private String body(String adminPassword, String password) throws Exception {
        return json.writeValueAsString(Map.of("adminPassword", adminPassword, "newPassword", password));
    }

    @Test
    void adminReauthenticationResetsOnlyOwnMemberAndInvalidatesPriorPassword() throws Exception {
        String username = "member-" + UUID.randomUUID();
        String old = "old-long-member-pass-123";
        String replacement = "replacement-long-pass-456";
        long id = member("local-admin", username, old, "MEMBER");
        mvc.perform(post("/api/team/" + id + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json").content(body(ADMIN, replacement)))
                .andExpect(status().isOk());
        assertTrue(encoder.matches(replacement,
                jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class, id)));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, basic(username, old)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, basic(username, replacement)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongAdminPasswordDoesNotChangeMemberPassword() throws Exception {
        String username = "member-" + UUID.randomUUID();
        String old = "old-long-member-pass-123";
        long id = member("local-admin", username, old, "MEMBER");
        mvc.perform(post("/api/team/" + id + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json")
                .content(body("incorrect-admin-password", "replacement-long-pass-456")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, basic(username, old)))
                .andExpect(status().isOk());
    }

    @Test
    void memberAndForeignOrganizationAreDenied() throws Exception {
        String org = "org-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?,?)", org, "Other workspace");
        long foreign = member(org, "foreign-" + UUID.randomUUID(), "old-long-member-pass-123", "MEMBER");
        String localUser = "member-" + UUID.randomUUID();
        member("local-admin", localUser, "old-long-member-pass-123", "MEMBER");
        mvc.perform(post("/api/team/" + foreign + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json")
                .content(body(ADMIN, "replacement-long-pass-456")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/team/" + foreign + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic(localUser, "old-long-member-pass-123"))
                .contentType("application/json")
                .content(body("old-long-member-pass-123", "replacement-long-pass-456")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotResetAdminOrDisabledMemberAndInvalidLengthIsRejected() throws Exception {
        Long adminId = jdbc.queryForObject("SELECT id FROM app_users WHERE username='admin'", Long.class);
        mvc.perform(post("/api/team/" + adminId + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json")
                .content(body(ADMIN, "replacement-long-pass-456")))
                .andExpect(status().isConflict());
        String user = "disabled-" + UUID.randomUUID();
        long disabledId = member("local-admin", user, "old-long-member-pass-123", "MEMBER");
        jdbc.update("UPDATE app_users SET enabled=false WHERE id=?", disabledId);
        mvc.perform(post("/api/team/" + disabledId + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json")
                .content(body(ADMIN, "replacement-long-pass-456")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/team/" + disabledId + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", ADMIN))
                .contentType("application/json")
                .content(body(ADMIN, "short")))
                .andExpect(status().isBadRequest());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
