package gov.fdic.tip.retention.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent, append-only audit trail entry.
 * Covers both document promotions and automatic table classifications.
 * Story 3 – US-1.Audit-Lean.
 * <p>
 * NOTE: This entity is intentionally read-only from the application layer.
 * The DB-level RULE prevents UPDATE/DELETE at the database level.
 * The service layer only ever calls auditEventRepository.save(…) for inserts.
 */
@Entity
@Table(name = "audit_event", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEvent {

    public enum EventType {
        DOCUMENT_PROMOTED,
        AUTO_CLASSIFIED,
        PROMOTION_FAILED,
        AUTO_CLASSIFICATION_FAILED,
        RECORD_CORRECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "retention_record_id")
    private UUID retentionRecordId;

    @Column(name = "auto_classification_id")
    private UUID autoClassificationId;

    @Column(name = "upstream_module_code", nullable = false, length = 50)
    private String upstreamModuleCode;

    @Column(name = "upstream_reference", length = 500)
    private String upstreamReference;

    @Column(name = "retention_bucket_code", nullable = false, length = 50)
    private String retentionBucketCode;

    @Column(name = "basis_date")
    private LocalDate basisDate;

    @Column(name = "retention_date")
    private LocalDate retentionDate;

    /** Free-form JSON context (error details, blob URI, table name, etc.) */
    @Column(name = "event_detail", columnDefinition = "jsonb")
    private String eventDetail;   // stored/retrieved as a JSON string

    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;
}
