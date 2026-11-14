package com.nexora.os.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** On a fresh CI PostgreSQL instance, Spring Boot must apply V1 and V2 before startup. */
@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
class SchemaMigrationIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatedTenantColumns() {
        for (String table : new String[]{"leads", "lead_tasks", "lead_activities"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'tenant_id'",
                    Integer.class, table);
            assertEquals(1, count, "Missing tenant_id in " + table);
        }
    }

    @Test
    void expectedMigrationsAreRecorded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history " +
                "WHERE version IN ('1','2') AND success = true",
                Integer.class);
        assertTrue(count != null && count >= 2);
    }
}
