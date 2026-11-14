package com.nexora.os.task;

import com.nexora.os.lead.LeadRepository;
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

    public LeadTaskController(LeadTaskRepository tasks, LeadRepository leads) {
        this.tasks = tasks;
        this.leads = leads;
    }

    @GetMapping
    public List<LeadTaskResponse> all() {
        return tasks.findAllByOrderByDueDateAscIdAsc().stream().map(LeadTaskResponse::from).toList();
    }

    @GetMapping("/lead/{leadId}")
    public List<LeadTaskResponse> byLead(@PathVariable Long leadId) {
        if (!leads.existsById(leadId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        return tasks.findByLeadIdOrderByDueDateAscIdAsc(leadId).stream().map(LeadTaskResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<LeadTaskResponse> create(@Valid @RequestBody CreateTask request) {
        if (!leads.existsById(request.leadId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        LeadTask task = new LeadTask();
        task.leadId = request.leadId();
        task.title = request.title().trim();
        task.dueDate = request.dueDate();
        task.status = LeadTask.Status.OPEN;
        return ResponseEntity.status(HttpStatus.CREATED).body(LeadTaskResponse.from(tasks.save(task)));
    }

    @PatchMapping("/{id}/status")
    public LeadTaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatus request) {
        LeadTask task = tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.status = request.status();
        return LeadTaskResponse.from(tasks.save(task));
    }

    public record CreateTask(@NotNull Long leadId,
                             @NotBlank @Size(max = 160) String title,
                             @NotNull LocalDate dueDate) {}
    public record UpdateStatus(@NotNull LeadTask.Status status) {}
}
