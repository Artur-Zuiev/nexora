package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class SecurityAuditIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired SecurityAudit audit;



    @Test
    void adminSeesOnlyOwnEventsAndNoSecretsAreReturned() throws Exception {
        String marker = "audit-" + UUID.randomUUID();
        String foreignTenant = "org-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id,name) VALUES (?,?)", foreignTenant, "Foreign test org");
        audit.record("local-admin", "admin", SecurityAudit.Event.MEMBER_CREATED, marker);
        audit.record(foreignTenant, "foreign-admin", SecurityAudit.Event.MEMBER_PASSWORD_RESET, "secret-other-account");
        JsonNode events = mapper.readTree(mvc.perform(get("/api/security/events")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(events.isArray());
        assertTrue(events.size() >= 1);
        String output = events.toString();
        assertTrue(output.contains(marker));
        assertFalse(output.contains("secret-other-account"));
        assertFalse(output.contains("passwordHash"));
        assertFalse(output.contains("adminPassword"));
    }

    @Test
    void memberCannotReadAuditLogButPasswordChangeIsRecorded() throws Exception {
        String user = "audit-member-" + UUID.randomUUID();
        String previous = "old-member-password-123";
        String updated = "new-member-password-456";
        jdbc.update("INSERT INTO app_users (tenant_id,username,password_hash,role) " +
            "VALUES ('local-admin',?,?,'MEMBER')", user, encoder.encode(previous));
        mvc.perform(get("/api/security/events")
                .with(SessionTestSupport.asUser(mvc, user, previous)))
                .andExpect(status().isForbidden());

        String payload = mapper.writeValueAsString(
            java.util.Map.of("currentPassword", previous, "newPassword", updated));
        mvc.perform(post("/api/me/password")
                .with(SessionTestSupport.asUser(mvc, user, previous))
                .contentType("application/json").content(payload))
                .andExpect(status().isOk());
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM security_audit_events " +
            "WHERE tenant_id='local-admin' AND actor_username=? " +
            "AND event_type='SELF_PASSWORD_CHANGED'", Integer.class, user);
        assertEquals(1, count);
    }

    @Test
    void creatingMemberProducesAuditEntryWithoutPassword() throws Exception {
        String username = "audit-new-" + UUID.randomUUID();
        String password = "different-member-password-123";
        String payload = mapper.writeValueAsString(java.util.Map.of(
            "username", username, "password", password));
        mvc.perform(post("/api/team")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated());
        String eventName = jdbc.queryForObject(
            "SELECT event_type FROM security_audit_events " +
            "WHERE tenant_id='local-admin' AND target_username=?", String.class, username);
        assertEquals("MEMBER_CREATED", eventName);
        Integer leaks = jdbc.queryForObject(
            "SELECT count(*) FROM security_audit_events " +
            "WHERE actor_username=? OR target_username=?", Integer.class, password, password);
        assertEquals(0, leaks);
    }
}
