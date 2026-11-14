package com.nexora.os.task;

import com.nexora.os.lead.LeadRepository;
import com.nexora.os.config.CurrentTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class LeadTaskController {
    private final LeadTaskRepository tasks;
    private final LeadRepository leads;
    private final CurrentTenant tenant;

    public LeadTaskController(LeadTaskRepository tasks, LeadRepository leads, CurrentTenant tenant) {
        this.tasks = tasks;
        this.leads = leads;
        this.tenant = tenant;
    }

    @GetMapping
    public List<LeadTaskResponse> all() {
        return tasks.findByTenantIdOrderByDueDateAscIdAsc(tenant.id()).stream().map(LeadTaskResponse::from).toList();
    }

    @GetMapping("/lead/{leadId}")
    public List<LeadTaskResponse> byLead(@PathVariable Long leadId) {
        if (!leads.existsByIdAndTenantId(leadId, tenant.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        return tasks.findByLeadIdAndTenantIdOrderByDueDateAscIdAsc(leadId, tenant.id()).stream().map(LeadTaskResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<LeadTaskResponse> create(@Valid @RequestBody CreateTask request) {
        if (!leads.existsByIdAndTenantId(request.leadId(), tenant.id()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        LeadTask task = new LeadTask();
        task.tenantId = tenant.id();
        task.leadId = request.leadId();
        task.title = request.title().trim();
        task.dueDate = request.dueDate();
        task.status = LeadTask.Status.OPEN;
        return ResponseEntity.status(HttpStatus.CREATED).body(LeadTaskResponse.from(tasks.save(task)));
    }

    @PatchMapping("/{id}/status")
    public LeadTaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatus request) {
        LeadTask task = tasks.findByIdAndTenantId(id, tenant.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.status = request.status();
        return LeadTaskResponse.from(tasks.save(task));
    }

    public record CreateTask(@NotNull Long leadId,
                             @NotBlank @Size(max = 160) String title,
                             @NotNull LocalDate dueDate) {}
    public record UpdateStatus(@NotNull LeadTask.Status status) {}
}
