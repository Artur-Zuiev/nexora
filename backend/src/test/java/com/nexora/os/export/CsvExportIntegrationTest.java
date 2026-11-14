package com.nexora.os.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexora.os.config.SessionTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
@Transactional
class CsvExportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminExportsOnlyOwnTenantWithNoCacheAndEscapedValues() throws Exception {
        String own = "CSV-" + UUID.randomUUID();
        String other = "OTHER-" + UUID.randomUUID();
        String tenant = "foreign-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id,name) VALUES (?,?)", tenant, "Other");
        jdbc.update("INSERT INTO leads (tenant_id,name,email,note,status) VALUES ('local-admin',?,? ,?,'NEW')",
                own, "csv@example.test", "=HYPERLINK(\"https://example.test\")");
        jdbc.update("INSERT INTO leads (tenant_id,name,email,status) VALUES (?,? ,? ,'NEW')",
                tenant, other, "foreign@example.test");
        Long leadId = jdbc.queryForObject("SELECT id FROM leads WHERE name=?", Long.class, own);
        jdbc.update("INSERT INTO lead_tasks (tenant_id,lead_id,title,due_date,status,created_at) " +
                "VALUES ('local-admin',?,?,'2026-12-02','OPEN', CURRENT_TIMESTAMP)", leadId, "=2+2");

        var session = SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123");
        var leadResponse = mvc.perform(get("/api/exports/leads.csv").with(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"nexora-leads.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        String csv = leadResponse.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFid,name,email,status,note"));
        assertTrue(csv.contains(own));
        assertFalse(csv.contains(other));
        assertTrue(csv.contains("'=HYPERLINK"));

        String tasks = mvc.perform(get("/api/exports/tasks.csv")
                .with(SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(tasks.contains("'=2+2"));
        assertTrue(tasks.contains("2026-12-02"));
    }

    @Test
    void memberCannotBulkExportAndAnonymousIsDenied() throws Exception {
        String user = "member-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (tenant_id,username,password_hash,role) VALUES ('local-admin',?,?,'MEMBER')",
                user, encoder.encode("safe-member-password-123"));
        mvc.perform(get("/api/exports/leads.csv"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/exports/leads.csv")
                .with(SessionTestSupport.asUser(mvc, user, "safe-member-password-123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/exports/tasks.csv")
                .with(SessionTestSupport.asUser(mvc, user, "safe-member-password-123")))
                .andExpect(status().isForbidden());
    }
}
