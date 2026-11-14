package com.nexora.os.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Transactional, tenant-scoped local audit timeline. Call inside the same
 * transaction as the state change, so a rollback removes both.
 */
@Service
public class SecurityAudit {
    private final JdbcTemplate jdbc;

    public SecurityAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String tenant, String actor, Event type, String target) {
        jdbc.update(
                "INSERT INTO security_audit_events (tenant_id, actor_username, event_type, target_username) VALUES (?,?,?,?)",
                tenant, actor, type.name(), target);
    }

    public List<EventView> recentForTenant(String tenant) {
        return jdbc.query(
                "SELECT id, actor_username, event_type, target_username, created_at " +
                "FROM security_audit_events WHERE tenant_id = ? " +
                "ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs, rowNum) -> new EventView(
                        rs.getLong("id"),
                        rs.getString("actor_username"),
                        rs.getString("event_type"),
                        rs.getString("target_username"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()),
                tenant);
    }

    public enum Event {
        MEMBER_CREATED, MEMBER_DISABLED, MEMBER_ENABLED,
        MEMBER_PASSWORD_RESET, SELF_PASSWORD_CHANGED
    }

    public record EventView(
            Long id, String actorUsername, String eventType,
            String targetUsername, Instant createdAt) {}
}
