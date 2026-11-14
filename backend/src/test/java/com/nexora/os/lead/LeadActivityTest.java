package com.nexora.os.lead;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeadActivityTest {

    @Test
    void recordsCreationWithoutStoringCustomerContentInTimeline() {
        LeadRepository leads = mock(LeadRepository.class);
        LeadActivityRepository activities = mock(LeadActivityRepository.class);
        LeadController controller = new LeadController(leads, activities);
        when(leads.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            lead.id = 12L;
            return lead;
        });

        controller.create(new CreateLeadRequest("Test Customer", "test@example.com", "Private note"));

        var captor = org.mockito.ArgumentCaptor.forClass(LeadActivity.class);
        verify(activities).save(captor.capture());
        LeadActivity recorded = captor.getValue();
        assertEquals(12L, recorded.leadId);
        assertEquals(LeadActivity.Kind.CREATED, recorded.kind);
        assertEquals("Lead created", recorded.summary);
        assertFalse(recorded.summary.contains("Private note"));
    }

    @Test
    void onlyRecordsActualStatusChanges() {
        LeadRepository leads = mock(LeadRepository.class);
        LeadActivityRepository activities = mock(LeadActivityRepository.class);
        LeadController controller = new LeadController(leads, activities);
        Lead lead = new Lead();
        lead.id = 7L;
        lead.status = Lead.Status.NEW;
        when(leads.findById(7L)).thenReturn(Optional.of(lead));
        when(leads.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.changeStatus(7L, new LeadController.StatusUpdate(Lead.Status.NEW));
        verifyNoInteractions(activities);

        controller.changeStatus(7L, new LeadController.StatusUpdate(Lead.Status.CONTACTED));
        verify(activities).save(argThat(a ->
                a.leadId.equals(7L) && a.kind == LeadActivity.Kind.STATUS_CHANGED));
    }

    @Test
    void historyOnlyReturnsRequestedLeadEntries() {
        LeadRepository leads = mock(LeadRepository.class);
        LeadActivityRepository activities = mock(LeadActivityRepository.class);
        LeadController controller = new LeadController(leads, activities);
        when(leads.existsById(7L)).thenReturn(true);
        LeadActivity activity = LeadActivity.of(7L, LeadActivity.Kind.NOTE_UPDATED, "Note updated");
        activity.id = 2L;
        activity.onCreate();
        when(activities.findTop50ByLeadIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(activity));

        var response = controller.history(7L);
        assertEquals(1, response.size());
        assertEquals("NOTE_UPDATED", response.get(0).kind());
        verify(activities).findTop50ByLeadIdOrderByCreatedAtDescIdDesc(7L);
    }
}
