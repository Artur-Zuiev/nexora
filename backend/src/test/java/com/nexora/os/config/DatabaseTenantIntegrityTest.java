package com.nexora.os.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexora.os.lead.LeadRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes against real PostgreSQL in CI. Each case rolls back its inserted data.
 */
@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@Transactional
class DatabaseTenantIntegrityTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LeadRepository leads;

    @Test
    void flywayV3InstalledOrganizationAndTenantKeys() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
                Integer.class);
        assertEquals(1, applied);

        Integer constraints = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname IN (" +
                "'fk_leads_organization', 'fk_lead_tasks_organization', " +
                "'fk_lead_activities_organization', 'fk_lead_tasks_same_tenant_lead', " +
                "'fk_lead_activities_same_tenant_lead')",
                Integer.class);
        assertEquals(5, constraints);
    }

    @Test
    void taskCannotReferenceLeadFromAnotherTenant() {
        String tenantB = "test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, name) VALUES (?, ?)", tenantB, "Other company");

        Long leadId = jdbc.queryForObject(
                "INSERT INTO leads (tenant_id, name, email, status) " +
                "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, tenantB, "Other company client", "client@example.test", "NEW");

        assertTrue(leads.findByIdAndTenantId(leadId, "local-admin").isEmpty());
        assertTrue(leads.findByIdAndTenantId(leadId, tenantB).isPresent());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO lead_tasks (tenant_id, lead_id, title, due_date, status, created_at) " +
                "VALUES (?, ?, ?, CURRENT_DATE, 'OPEN', CURRENT_TIMESTAMP)",
                "local-admin", leadId, "Cross-tenant task"));
    }
}
