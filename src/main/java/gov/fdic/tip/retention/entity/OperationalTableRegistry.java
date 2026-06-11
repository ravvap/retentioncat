package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pattern B – one row per upstream operational table enrolled for retention.
 *
 * No FK to an upstream_module table – module identity is a plain varchar sourced
 * from the Azure AD JWT appid/oid claim at registration time.
 *
 * At INSERT time, the upstream module calls POST /v1/classify-record, passing
 * schema + table + pk_value + basis_date. This service looks up this registry
 * to resolve the default sub-category and compute the eligibility date.
 */
@Entity
@Table(name = "operational_table_registry", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperationalTableRegistry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** PostgreSQL schema of the upstream table, e.g. "examination". */
    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    /** Upstream table name, e.g. "findings". */
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    /**
     * Name of the column in the upstream table whose value becomes basis_date.
     * Stored at registration time; used to guide the upstream module on which
     * date to pass to POST /v1/classify-record.
     */
    @Column(name = "basis_date_column", nullable = false, length = 100)
    private String basisDateColumn;

    /**
     * Default sub-category applied when the upstream module does not supply
     * an override in the POST /v1/classify-record request.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_sub_category_id", nullable = false)
    private RetentionSubCategory defaultSubCategory;

    /**
     * Azure AD appid (or oid) claim of the module that owns this registration.
     * Sourced from JWT at registration time; stored for audit purposes.
     */
    @Column(name = "owning_module_code", nullable = false, length = 100)
    private String owningModuleCode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "registered_by", nullable = false, length = 200)
    private String registeredBy;
}
