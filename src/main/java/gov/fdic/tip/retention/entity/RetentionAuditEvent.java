package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Permanent, append-only audit archive covering both Pattern A and Pattern B.
 *
 * Written explicitly by ClassificationService / RecordClassificationService –
 * never by a DB trigger.
 *
 * DB RULES prevent UPDATE and DELETE on the underlying table.
 * This entity therefore has no @LastModifiedDate / @CreatedBy auditing;
 * occurred_at and performed_by are set once at construction.
 *
 * All taxonomy fields (category_code, sub_category_code, duration) are
 * denormalised snapshots so the audit trail remains accurate even if the
 * taxonomy changes later.
 */
@Entity
@Table(name = "retention_audit_archive", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionAuditEvent {

    public enum EventType {
        DOCUMENT_CLASSIFIED,
        RECORD_CLASSIFIED,
        CLASSIFICATION_FAILED
    }

    public enum ClassificationPattern { A, B }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_pattern", nullable = false, length = 1)
    private ClassificationPattern classificationPattern;

    // ── Pattern A reference ──────────────────────────────────────────────────

    /** FK to cm_documents – populated for Pattern A events only. */
    @Column(name = "cm_document_id")
    private UUID cmDocumentId;

    // ── Pattern B reference ──────────────────────────────────────────────────

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "table_schema", length = 100)
    private String tableSchema;

    @Column(name = "table_name", length = 100)
    private String tableName;

    // ── Who ──────────────────────────────────────────────────────────────────

    /** Azure AD appid / module code from JWT. */
    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    // ── Taxonomy snapshot ────────────────────────────────────────────────────

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "sub_category_code", nullable = false, length = 50)
    private String subCategoryCode;

    // ── Retention snapshot ───────────────────────────────────────────────────

    @Column(name = "basis_date")
    private LocalDate basisDate;

    @Column(name = "eligibility_date")
    private LocalDate eligibilityDate;

    @Column(name = "retention_duration_value")
    private Short retentionDurationValue;

    @Column(name = "retention_duration_unit", length = 10)
    private String retentionDurationUnit;

    @Column(name = "has_ever_held_content")
    private Boolean hasEverHeldContent;

    // ── Failure detail ───────────────────────────────────────────────────────

    @Column(name = "reason")
    private String reason;

    @Column(name = "event_detail", columnDefinition = "jsonb")
    private String eventDetail;

    // ── When / who ───────────────────────────────────────────────────────────

    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    /** JWT subject / name of the caller. */
    @Column(name = "performed_by", nullable = false, length = 200)
    private String performedBy;
}
