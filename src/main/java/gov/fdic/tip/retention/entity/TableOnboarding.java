package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks every upstream database table enrolled for automatic retention classification.
 * Story 2 – US-1.24-Lean.
 */
@Entity
@Table(name = "table_onboarding", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TableOnboarding extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upstream_module_id", nullable = false)
    private UpstreamModule upstreamModule;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    /**
     * The column in the upstream table whose value is used as the basis date.
     * Chosen and locked at onboarding time (AC-4).
     */
    @Column(name = "basis_date_column", nullable = false, length = 100)
    private String basisDateColumn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retention_bucket_id", nullable = false)
    private RetentionBucket retentionBucket;

    /** Name of the PostgreSQL trigger installed on the upstream table. */
    @Column(name = "trigger_name", length = 200)
    private String triggerName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "onboarded_at", nullable = false)
    private OffsetDateTime onboardedAt;

    @Column(name = "onboarded_by", nullable = false, length = 100)
    private String onboardedBy;
}
