package gov.fdic.tip.retention.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "retention_audit_archive", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionAuditEvent {
    public enum EventType { DOCUMENT_CLASSIFIED, RECORD_CLASSIFIED, CLASSIFICATION_FAILED }
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

    @Column(name = "cm_document_id")
    private UUID cmDocumentId;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "table_schema", length = 100)
    private String tableSchema;

    @Column(name = "table_name", length = 100)
    private String tableName;

    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "sub_category_code", nullable = false, length = 50)
    private String subCategoryCode;

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

    @Column(name = "reason")
    private String reason;

    @Column(name = "event_detail", columnDefinition = "jsonb")
    private String eventDetail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "performed_by", nullable = false, length = 200)
    private String performedBy;
}
