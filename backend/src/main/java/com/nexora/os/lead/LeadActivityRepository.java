package com.nexora.os.lead;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, Long> {
    List<LeadActivity> findTop50ByLeadIdOrderByCreatedAtDescIdDesc(Long leadId);
}
