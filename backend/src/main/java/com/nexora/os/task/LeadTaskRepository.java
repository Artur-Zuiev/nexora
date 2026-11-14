package com.nexora.os.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadTaskRepository extends JpaRepository<LeadTask, Long> {
    List<LeadTask> findAllByOrderByDueDateAscIdAsc();
    List<LeadTask> findByLeadIdOrderByDueDateAscIdAsc(Long leadId);
}
