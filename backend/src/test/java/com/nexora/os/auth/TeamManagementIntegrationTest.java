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
class TeamManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminCanCreateMemberAndDisableTheirAccess() throws Exception {
        String username = "new-" + UUID.randomUUID();
        String password = "dev-member-password-123";
        String request = json.writeValueAsString(new TeamController.CreateMember(username, password));
        var created = mvc.perform(post("/api/team")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        JsonNode member = json.readTree(created.getContentAsString());
        long id = member.path("id").asLong();
        assertEquals(username, member.path("username").asText());
        assertEquals("MEMBER", member.path("role").asText());
        assertFalse(member.has("password"));
        assertFalse(member.has("passwordHash"));
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class, id);
        assertNotEquals(password, storedHash);
        assertTrue(encoder.matches(password, storedHash));

        mvc.perform(get("/api/me").with(SessionTestSupport.asUser(mvc, username, password)))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/team/" + id + "/enabled")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        SessionTestSupport.expectLoginFailure(mvc, username, password);
    }

    @Test
    void membersCannotProvisionUsersAndAdminsCannotModifyForeignMembers() throws Exception {
        String org = "org-" + UUID.randomUUID();
        String username = "member-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, 'Other org')", org);
        jdbc.update("INSERT INTO app_users (tenant_id, username, password_hash, role) VALUES (?,?,?,'MEMBER')",
                org, username, encoder.encode("member-password-123"));
        Long id = jdbc.queryForObject("SELECT id FROM app_users WHERE username=?", Long.class, username);

        mvc.perform(post("/api/team")
                .with(SessionTestSupport.asUser(mvc, username, "member-password-123"))
                .contentType("application/json").content("{\"username\":\"blocked\",\"password\":\"long-enough-pass\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/team/" + id + "/enabled")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/team/" + id + "/enabled")
                .with(SessionTestSupport.asUser(mvc, username, "member-password-123"))
                .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInputAndOwnAdminDisablingAreRejected() throws Exception {
        mvc.perform(post("/api/team")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content("{\"username\":\"A\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        Long adminId = jdbc.queryForObject("SELECT id FROM app_users WHERE username='admin'", Long.class);
        mvc.perform(patch("/api/team/" + adminId + "/enabled")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/team/" + adminId + "/enabled")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }


}
