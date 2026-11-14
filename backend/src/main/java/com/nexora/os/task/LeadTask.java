package com.nexora.os.task;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lead_tasks", indexes = {
        @Index(name = "idx_lead_task_lead", columnList = "lead_id"),
        @Index(name = "idx_lead_task_due", columnList = "due_date")
})
public class LeadTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "lead_id", nullable = false)
    public Long leadId;

    @Column(nullable = false, length = 160)
    public String title;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.OPEN;

    @Column(nullable = false, updatable = false)
    public Instant createdAt;

    public enum Status { OPEN, DONE }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
