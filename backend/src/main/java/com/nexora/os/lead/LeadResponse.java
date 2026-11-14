package com.nexora.os.lead;

/**
 * Public API projection. Do not return mutable JPA entities directly.
 */
public record LeadResponse(Long id, String name, String email, String note, Lead.Status status) {
    public static LeadResponse from(Lead lead) {
        return new LeadResponse(lead.id, lead.name, lead.email, lead.note, lead.status);
    }
}
