package com.nexora.os.lead;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByTenantIdOrderByIdAsc(String tenantId);
    Optional<Lead> findByIdAndTenantId(Long id, String tenantId);
    boolean existsByIdAndTenantId(Long id, String tenantId);
}
