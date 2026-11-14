package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Real PostgreSQL + Spring Security tests; all inserted test data rolls back. */
@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
@Transactional
class MultiOrganizationAuthorizationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    @Test
    void authenticatedUsersCannotReadOrAttachTasksToOtherOrganizationsLeads() throws Exception {
        String org = "org-" + UUID.randomUUID();
        String user = "member-" + UUID.randomUUID();
        String password = "member-integration-password-123";
        String hash = encoder.encode(password);
        assertNotEquals(password, hash);
        assertTrue(encoder.matches(password, hash));

        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, ?)", org, "Test company");
        jdbc.update("INSERT INTO app_users (tenant_id, username, password_hash, role) " +
                    "VALUES (?, ?, ?, ?)", org, user, hash, "MEMBER");
        Long foreignLead = jdbc.queryForObject(
                "INSERT INTO leads (tenant_id, name, email, status) " +
                "VALUES (?, ?, ?, 'NEW') RETURNING id",
                Long.class, org, "Tenant B private lead", "private@example.test");
        Long localLead = jdbc.queryForObject(
                "INSERT INTO leads (tenant_id, name, email, status) " +
                "VALUES ('local-admin', ?, ?, 'NEW') RETURNING id",
                Long.class, "Local admin lead", "admin@example.test");

        JsonNode adminView = json.readTree(mvc.perform(get("/api/leads")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(containsId(adminView, localLead));
        assertFalse(containsId(adminView, foreignLead));

        JsonNode memberInfo = json.readTree(mvc.perform(get("/api/me")
                .with(SessionTestSupport.asUser(mvc, user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(user, memberInfo.path("username").asText());
        assertEquals(org, memberInfo.path("organizationId").asText());
        assertEquals("MEMBER", memberInfo.path("role").asText());
        assertFalse(memberInfo.has("passwordHash"));

        JsonNode memberView = json.readTree(mvc.perform(get("/api/leads")
                .with(SessionTestSupport.asUser(mvc, user, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(containsId(memberView, foreignLead));
        assertFalse(containsId(memberView, localLead));

        // Members may read their own organization's data, but not modify CRM records.
        mvc.perform(post("/api/tasks")
                .with(SessionTestSupport.asUser(mvc, user, password))
                .contentType("application/json")
                .content("{\"leadId\":" + foreignLead + ",\"title\":\"Follow up\",\"dueDate\":\"2027-02-01\"}"))
                .andExpect(status().isForbidden());

        // The team roster is only visible to admins of their own organization.
        mvc.perform(get("/api/team")
                .with(SessionTestSupport.asUser(mvc, user, password)))
                .andExpect(status().isForbidden());

        JsonNode localTeam = json.readTree(mvc.perform(get("/api/team")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(localTeam.isArray());
        assertTrue(localTeam.size() >= 1);
        for (JsonNode teamMember : localTeam) {
            assertFalse(teamMember.has("passwordHash"));
            assertFalse(teamMember.path("username").asText().equals(user));
        }

        mvc.perform(get("/api/leads/" + foreignLead + "/activities")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/tasks")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json")
                .content("{\"leadId\":" + foreignLead + ",\"title\":\"Cross-tenant task\",\"dueDate\":\"2027-02-01\"}"))
                .andExpect(status().isNotFound());
    }

    private static boolean containsId(JsonNode nodes, Long id) {
        for (JsonNode node : nodes) {
            if (node.path("id").asLong() == id) return true;
        }
        return false;
    }

}
