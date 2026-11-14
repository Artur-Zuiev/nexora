package com.nexora.os.lead;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lead_activities", indexes = @Index(name = "idx_lead_activity_lead", columnList = "lead_id"))
public class LeadActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "lead_id", nullable = false)
    public Long leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    public Kind kind;

    @Column(name = "summary", nullable = false, length = 200)
    public String summary;

    @Column(nullable = false, updatable = false)
    public Instant createdAt;

    public enum Kind { CREATED, STATUS_CHANGED, NOTE_UPDATED }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static LeadActivity of(Long leadId, Kind kind, String summary) {
        LeadActivity activity = new LeadActivity();
        activity.leadId = leadId;
        activity.kind = kind;
        activity.summary = summary;
        return activity;
    }
}
