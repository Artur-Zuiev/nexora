package com.nexora.os.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadTaskRepository extends JpaRepository<LeadTask, Long> {
    List<LeadTask> findByTenantIdOrderByDueDateAscIdAsc(String tenantId);
    List<LeadTask> findByLeadIdAndTenantIdOrderByDueDateAscIdAsc(Long leadId, String tenantId);
    Optional<LeadTask> findByIdAndTenantId(Long id, String tenantId);
}
