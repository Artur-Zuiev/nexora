package com.nexora.os.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temporary local-only backfill for rows created before the tenant_id column
 * existed. Replace this mechanism with reviewed Flyway migrations before any
 * organization or account is added.
 */
@Component
public class LegacyTenantBackfill implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public LegacyTenantBackfill(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.update("UPDATE leads SET tenant_id = ? WHERE tenant_id IS NULL", CurrentTenant.LOCAL_ORGANIZATION);
        jdbc.update("UPDATE lead_tasks SET tenant_id = ? WHERE tenant_id IS NULL", CurrentTenant.LOCAL_ORGANIZATION);
        jdbc.update("UPDATE lead_activities SET tenant_id = ? WHERE tenant_id IS NULL", CurrentTenant.LOCAL_ORGANIZATION);
    }
}
