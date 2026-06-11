package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Pattern A – one row per document promoted into TIP retention via the API.
 *
 * module_code is a plain varchar sourced from the caller's Azure AD JWT claim
 * (e.g. appid). No FK to an upstream_module table.
 *
 * eligibility_date and has_ever_held_content are computed and set by
 * ClassificationService before the row is persisted – no DB trigger involved.
 */
@Entity
@Table(name = "cm_documents", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmDocument extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Azure AD appid claim of the calling module (e.g. "EW", "CM"). */
    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    /** Caller's own identifier – used for idempotency (unique with module_code). */
    @Column(name = "source_reference", nullable = false, length = 500)
    private String sourceReference;

    @Column(name = "filename", length = 500)
    private String filename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private RetentionSubCategory subCategory;

    /** Business event date supplied by the caller. */
    @Column(name = "basis_date", nullable = false)
    private LocalDate basisDate;

    /** Computed by ClassificationService: basisDate + sub-category retention duration. */
    @Column(name = "eligibility_date", nullable = false)
    private LocalDate eligibilityDate;

    /** Set to true on first successful classification; never reverts. */
    @Column(name = "has_ever_held_content", nullable = false)
    @Builder.Default
    private boolean hasEverHeldContent = false;

    @Column(name = "blob_storage_uri")
    private String blobStorageUri;
}
