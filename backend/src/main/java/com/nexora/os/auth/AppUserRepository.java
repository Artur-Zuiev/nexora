package com.nexora.os.auth;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByIdAndTenantId(Long id, String tenantId);
    List<AppUser> findByTenantIdOrderByUsernameAsc(String tenantId);
}
