package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.UpstreamModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UpstreamModuleRepository extends JpaRepository<UpstreamModule, UUID> {
    Optional<UpstreamModule> findByModuleCodeAndActiveTrue(String moduleCode);
    Optional<UpstreamModule> findByActiveTrue();
}
