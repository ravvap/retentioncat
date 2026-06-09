package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per document promoted into retention via the API (Story 1 – US-1.13-Lean).
 * The document's actual bytes live in Azure Blob Storage; only metadata is stored here.
 */
@Entity
@Table(name = "retention_record", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionRecord extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upstream_module_id", nullable = false)
    private UpstreamModule upstreamModule;

    /** The calling system's own identifier for the document. */
    @Column(name = "upstream_reference", nullable = false, length = 500)
    private String upstreamReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retention_bucket_id", nullable = false)
    private RetentionBucket retentionBucket;

    /**
     * Business event date supplied by the caller (e.g. date examination closed).
     * AC-5: the calling team is responsible for supplying a sensible basis date.
     */
    @Column(name = "basis_date", nullable = false)
    private LocalDate basisDate;

    /** Computed by the service: basisDate + retentionBucket.retentionYears. */
    @Column(name = "retention_date", nullable = false)
    private LocalDate retentionDate;

    /** Azure Blob Storage URI; actual file bytes are NOT stored in the DB. */
    @Column(name = "blob_storage_uri")
    private String blobStorageUri;
}
