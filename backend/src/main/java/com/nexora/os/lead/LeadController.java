package com.nexora.os.lead;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadRepository repository;

    public LeadController(LeadRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Lead> all() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Lead> create(@Valid @RequestBody CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.name = request.name().trim();
        lead.email = request.email().trim();
        lead.note = request.note();
        lead.status = Lead.Status.NEW;
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(lead));
    }

    @PatchMapping("/{id}/status")
    public Lead changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate update) {
        Lead lead = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        lead.status = update.status();
        return repository.save(lead);
    }

    public record StatusUpdate(@NotNull Lead.Status status) {}
}
