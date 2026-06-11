package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface RetentionAuditEventRepository extends JpaRepository<RetentionAuditEvent, UUID> {

    /**
     * Flexible audit search used by GET /v1/audit-events.
     * All parameters are optional – null means "no filter on this column".
     */
    @Query("""
        SELECT a FROM RetentionAuditEvent a
        WHERE (:categoryCode  IS NULL OR a.categoryCode           = :categoryCode)
          AND (:subCatCode    IS NULL OR a.subCategoryCode        = :subCatCode)
          AND (:moduleCode    IS NULL OR a.moduleCode             = :moduleCode)
          AND (:eventType     IS NULL OR a.eventType              = :eventType)
          AND (:pattern       IS NULL OR a.classificationPattern  = :pattern)
          AND (:from          IS NULL OR a.occurredAt             >= :from)
          AND (:to            IS NULL OR a.occurredAt             <= :to)
        ORDER BY a.occurredAt DESC
        """)
    Page<RetentionAuditEvent> search(
            @Param("categoryCode") String categoryCode,
            @Param("subCatCode")   String subCatCode,
            @Param("moduleCode")   String moduleCode,
            @Param("eventType")    RetentionAuditEvent.EventType eventType,
            @Param("pattern")      RetentionAuditEvent.ClassificationPattern pattern,
            @Param("from")         OffsetDateTime from,
            @Param("to")           OffsetDateTime to,
            Pageable pageable);
}
