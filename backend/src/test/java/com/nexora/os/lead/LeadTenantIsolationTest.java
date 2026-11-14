package com.nexora.os.lead;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexora.os.config.CurrentTenant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LeadTenantIsolationTest {
    private final LeadRepository leads = mock(LeadRepository.class);
    private final LeadActivityRepository activities = mock(LeadActivityRepository.class);
    private final CurrentTenant tenant = mock(CurrentTenant.class);
    private final LeadController controller = new LeadController(leads, activities, tenant);

    @Test
    void onlyListsCurrentTenantsLeads() {
        when(tenant.id()).thenReturn("org-a");
        Lead visible = new Lead();
        visible.id = 1L; visible.tenantId = "org-a"; visible.name = "Visible";
        when(leads.findByTenantIdOrderByIdAsc("org-a")).thenReturn(List.of(visible));

        assertEquals(1, controller.all().size());
        verify(leads).findByTenantIdOrderByIdAsc("org-a");
        verify(leads, never()).findAll();
    }

    @Test
    void cannotUpdateLeadOutsideTenant() {
        when(tenant.id()).thenReturn("org-a");
        when(leads.findByIdAndTenantId(20L, "org-a")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                controller.changeStatus(20L, new LeadController.StatusUpdate(Lead.Status.WON)));
        verify(leads, never()).save(any());
        verifyNoInteractions(activities);
    }

    @Test
    void cannotReadOtherTenantsActivity() {
        when(tenant.id()).thenReturn("org-a");
        when(leads.existsByIdAndTenantId(20L, "org-a")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> controller.history(20L));
        verifyNoInteractions(activities);
    }

    @Test
    void newLeadGetsServerAssignedTenant() {
        when(tenant.id()).thenReturn("org-a");
        when(leads.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            lead.id = 30L;
            return lead;
        });
        controller.create(new CreateLeadRequest("Customer", "a@example.test", ""));
        verify(leads).save(argThat(lead -> "org-a".equals(lead.tenantId)));
        verify(activities).save(argThat(a -> "org-a".equals(a.tenantId) && a.leadId == 30L));
    }
}
