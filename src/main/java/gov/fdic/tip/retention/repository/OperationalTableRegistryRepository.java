package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.OperationalTableRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperationalTableRegistryRepository extends JpaRepository<OperationalTableRegistry, UUID> {
    Optional<OperationalTableRegistry> findBySchemaNameAndTableNameAndActiveTrue(
            String schemaName, String tableName);
    boolean existsBySchemaNameAndTableName(String schemaName, String tableName);
}
