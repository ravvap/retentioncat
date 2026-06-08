package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Maps to tip_retention_audit_archive.
 * Application code only reads this table; writes happen exclusively through
 * DB triggers (tip_retention_emit_audit) or via the service layer for
 * taxonomy-level events (category created/edited/activated/etc.).
 */
@Entity
@Table(name = "tip_retention_audit_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entity_id", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> entityId;

    @Column(name = "schema_name", length = 255)
    private String schemaName;

    @Column(name = "table_name", length = 255)
    private String tableName;

    @Column(name = "sub_category_id")
    private UUID subCategoryId;

    @Column(name = "eligibility_date")
    private LocalDate eligibilityDate;

    @Column(name = "basis_date")
    private LocalDate basisDate;

    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payload = Map.of();

    @Column(name = "actor_user_id", length = 255)
    private String actorUserId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 255)
    @Builder.Default
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    @Builder.Default
    private String updatedBy = "system";
}
