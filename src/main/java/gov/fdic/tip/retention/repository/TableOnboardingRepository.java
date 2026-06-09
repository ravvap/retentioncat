package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.TableOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TableOnboardingRepository extends JpaRepository<TableOnboarding, UUID> {
    Optional<TableOnboarding> findBySchemaNameAndTableNameAndActiveTrue(
            String schemaName, String tableName);
    boolean existsBySchemaNameAndTableName(String schemaName, String tableName);
}
