package com.nexora.os.lead;

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

    public LeadController(LeadRepository repository, LeadActivityRepository activities) {
        this.repository = repository;
        this.activities = activities;
    }

    @GetMapping
    public List<Lead> all() {
        return repository.findAll();
    }

    @GetMapping("/{id}/activities")
    public List<ActivityResponse> history(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        }
        return activities.findTop50ByLeadIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(ActivityResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Lead> create(@Valid @RequestBody CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.name = request.name().trim();
        lead.email = request.email().trim();
        lead.note = request.note();
        lead.status = Lead.Status.NEW;
        Lead saved = repository.save(lead);
        activities.save(LeadActivity.of(saved.id, LeadActivity.Kind.CREATED, "Lead created"));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public Lead changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate update) {
        Lead lead = findLead(id);
        if (!Objects.equals(lead.status, update.status())) {
            Lead.Status previous = lead.status;
            lead.status = update.status();
            activities.save(LeadActivity.of(id, LeadActivity.Kind.STATUS_CHANGED,
                    "Status: " + previous + " → " + update.status()));
        }
        return repository.save(lead);
    }

    @PatchMapping("/{id}/note")
    @Transactional
    public Lead updateNote(@PathVariable Long id, @Valid @RequestBody NoteUpdate update) {
        Lead lead = findLead(id);
        if (!Objects.equals(lead.note == null ? "" : lead.note, update.note())) {
            lead.note = update.note();
            activities.save(LeadActivity.of(id, LeadActivity.Kind.NOTE_UPDATED, "Note updated"));
        }
        return repository.save(lead);
    }

    private Lead findLead(Long id) {
        return repository.findById(id)
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
