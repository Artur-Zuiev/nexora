package com.nexora.os.export;

import com.nexora.os.config.CurrentTenant;
import com.nexora.os.lead.Lead;
import com.nexora.os.lead.LeadRepository;
import com.nexora.os.task.LeadTask;
import com.nexora.os.task.LeadTaskRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only bulk export of records for the authenticated tenant. */
@RestController
@RequestMapping("/api/exports")
public class CsvExportController {
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private final CurrentTenant tenant;
    private final LeadRepository leads;
    private final LeadTaskRepository tasks;

    public CsvExportController(CurrentTenant tenant, LeadRepository leads, LeadTaskRepository tasks) {
        this.tenant = tenant;
        this.leads = leads;
        this.tasks = tasks;
    }

    @GetMapping(value = "/leads.csv", produces = "text/csv")
    public ResponseEntity<byte[]> leads() {
        List<Lead> records = leads.findByTenantIdOrderByIdAsc(tenant.id());
        StringBuilder csv = new StringBuilder("id,name,email,status,note\r\n");
        for (Lead lead : records) {
            csv.append(CsvExport.row(lead.id, lead.name, lead.email, lead.status, lead.note));
        }
        return download("nexora-leads.csv", csv.toString());
    }

    @GetMapping(value = "/tasks.csv", produces = "text/csv")
    public ResponseEntity<byte[]> tasks() {
        List<LeadTask> records = tasks.findByTenantIdOrderByDueDateAscIdAsc(tenant.id());
        StringBuilder csv = new StringBuilder("id,lead_id,title,due_date,status,created_at\r\n");
        for (LeadTask task : records) {
            csv.append(CsvExport.row(task.id, task.leadId, task.title, task.dueDate,
                    task.status, task.createdAt));
        }
        return download("nexora-tasks.csv", csv.toString());
    }

    private static ResponseEntity<byte[]> download(String filename, String content) {
        // UTF-8 BOM improves non-ASCII handling in spreadsheet applications.
        byte[] body = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
