package com.nexora.os.lead;

import com.nexora.os.config.CurrentTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadRepository repository;
    private final LeadActivityRepository activities;
    private final CurrentTenant tenant;

    public LeadController(LeadRepository repository, LeadActivityRepository activities, CurrentTenant tenant) {
        this.repository = repository;
        this.activities = activities;
        this.tenant = tenant;
    }

    @GetMapping
    public List<LeadResponse> all() {
        return repository.findByTenantIdOrderByIdAsc(tenant.id()).stream().map(LeadResponse::from).toList();
    }

    @GetMapping("/{id}/activities")
    public List<ActivityResponse> history(@PathVariable Long id) {
        if (!repository.existsByIdAndTenantId(id, tenant.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        }
        return activities.findTop50ByLeadIdAndTenantIdOrderByCreatedAtDescIdDesc(id, tenant.id()).stream()
                .map(ActivityResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.tenantId = tenant.id();
        lead.name = request.name().trim();
        lead.email = request.email().trim();
        lead.note = request.note();
        lead.status = Lead.Status.NEW;
        Lead saved = repository.save(lead);
        activities.save(LeadActivity.of(tenant.id(), saved.id, LeadActivity.Kind.CREATED, "Lead created"));
        return ResponseEntity.status(HttpStatus.CREATED).body(LeadResponse.from(saved));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public LeadResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate update) {
        Lead lead = findLead(id);
        if (!Objects.equals(lead.status, update.status())) {
            Lead.Status previous = lead.status;
            lead.status = update.status();
            activities.save(LeadActivity.of(tenant.id(), id, LeadActivity.Kind.STATUS_CHANGED,
                    "Status: " + previous + " → " + update.status()));
        }
        return LeadResponse.from(repository.save(lead));
    }

    @PatchMapping("/{id}/note")
    @Transactional
    public LeadResponse updateNote(@PathVariable Long id, @Valid @RequestBody NoteUpdate update) {
        Lead lead = findLead(id);
        if (!Objects.equals(lead.note == null ? "" : lead.note, update.note())) {
            lead.note = update.note();
            activities.save(LeadActivity.of(tenant.id(), id, LeadActivity.Kind.NOTE_UPDATED, "Note updated"));
        }
        return LeadResponse.from(repository.save(lead));
    }

    private Lead findLead(Long id) {
        return repository.findByIdAndTenantId(id, tenant.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    public record NoteUpdate(@NotNull @Size(max = 2000) String note) {}
    public record StatusUpdate(@NotNull Lead.Status status) {}
    public record ActivityResponse(Long id, String kind, String summary, Instant createdAt) {
        static ActivityResponse from(LeadActivity activity) {
            return new ActivityResponse(activity.id, activity.kind.name(), activity.summary, activity.createdAt);
        }
    }
}
