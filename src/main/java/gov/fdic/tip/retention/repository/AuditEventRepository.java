package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /** AC-6: query by bucket returns events from ALL upstream modules. */
    @Query("""
        SELECT a FROM AuditEvent a
        WHERE (:bucketCode  IS NULL OR a.retentionBucketCode  = :bucketCode)
          AND (:moduleCode  IS NULL OR a.upstreamModuleCode   = :moduleCode)
          AND (:eventType   IS NULL OR a.eventType            = :eventType)
          AND (:from        IS NULL OR a.occurredAt           >= :from)
          AND (:to          IS NULL OR a.occurredAt           <= :to)
        ORDER BY a.occurredAt DESC
        """)
    Page<AuditEvent> search(
            @Param("bucketCode")  String bucketCode,
            @Param("moduleCode")  String moduleCode,
            @Param("eventType")   AuditEvent.EventType eventType,
            @Param("from")        OffsetDateTime from,
            @Param("to")          OffsetDateTime to,
            Pageable pageable);
}
