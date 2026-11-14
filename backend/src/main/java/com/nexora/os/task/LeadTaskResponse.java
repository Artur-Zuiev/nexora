package com.nexora.os.task;

import java.time.Instant;
import java.time.LocalDate;

/** Explicit API response without persistence implementation details. */
public record LeadTaskResponse(Long id, Long leadId, String title, LocalDate dueDate,
                               LeadTask.Status status, Instant createdAt) {
    public static LeadTaskResponse from(LeadTask task) {
        return new LeadTaskResponse(task.id, task.leadId, task.title,
                task.dueDate, task.status, task.createdAt);
    }
}
