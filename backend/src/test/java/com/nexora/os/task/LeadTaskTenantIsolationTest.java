package com.nexora.os.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexora.os.config.CurrentTenant;
import com.nexora.os.lead.LeadRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LeadTaskTenantIsolationTest {
    private final LeadTaskRepository tasks = mock(LeadTaskRepository.class);
    private final LeadRepository leads = mock(LeadRepository.class);
    private final CurrentTenant tenant = mock(CurrentTenant.class);
    private final LeadTaskController controller = new LeadTaskController(tasks, leads, tenant);

    @Test
    void cannotAttachTaskToAnotherOrganizationsLead() {
        when(tenant.id()).thenReturn("org-a");
        when(leads.existsByIdAndTenantId(9L, "org-a")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> controller.create(
            new LeadTaskController.CreateTask(9L, "Call", LocalDate.of(2027, 1, 5))));
        verify(tasks, never()).save(any());
    }

    @Test
    void cannotChangeAnotherOrganizationsTask() {
        when(tenant.id()).thenReturn("org-a");
        when(tasks.findByIdAndTenantId(20L, "org-a")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> controller.updateStatus(
            20L, new LeadTaskController.UpdateStatus(LeadTask.Status.DONE)));
        verify(tasks, never()).save(any());
    }

    @Test
    void taskCreationUsesServerTenant() {
        when(tenant.id()).thenReturn("org-a");
        when(leads.existsByIdAndTenantId(9L, "org-a")).thenReturn(true);
        when(tasks.save(any(LeadTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.create(new LeadTaskController.CreateTask(9L, "Call", LocalDate.of(2027, 1, 5)));
        verify(tasks).save(argThat(task -> "org-a".equals(task.tenantId) && task.leadId == 9L));
    }
}
