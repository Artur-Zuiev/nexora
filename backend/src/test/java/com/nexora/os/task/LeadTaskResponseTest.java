package com.nexora.os.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeadTaskResponseTest {
    @Test
    void mapsTaskToStableApiFields() {
        LeadTask task = new LeadTask();
        task.id = 2L;
        task.leadId = 42L;
        task.title = "Call";
        task.dueDate = LocalDate.of(2027, 1, 3);
        task.status = LeadTask.Status.OPEN;
        task.createdAt = Instant.parse("2026-11-14T00:00:00Z");
        LeadTaskResponse response = LeadTaskResponse.from(task);
        assertEquals(2L, response.id());
        assertEquals(42L, response.leadId());
        assertEquals("Call", response.title());
        assertEquals(task.dueDate, response.dueDate());
        assertEquals(task.createdAt, response.createdAt());
    }
}
