package com.nexora.os.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class LeadResponseTest {
    @Test
    void mapsOnlyExpectedLeadFields() {
        Lead lead = new Lead();
        lead.id = 42L;
        lead.name = "Example";
        lead.email = "example@example.test";
        lead.note = "Call back";
        lead.status = Lead.Status.QUALIFIED;

        LeadResponse response = LeadResponse.from(lead);
        assertEquals(42L, response.id());
        assertEquals("Example", response.name());
        assertEquals("example@example.test", response.email());
        assertEquals("Call back", response.note());
        assertEquals(Lead.Status.QUALIFIED, response.status());
    }
}
