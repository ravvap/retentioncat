package gov.fdic.tip.retention.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cm_documents", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmDocument extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

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

    @Column(name = "basis_date", nullable = false)
    private LocalDate basisDate;

    @Column(name = "eligibility_date", nullable = false)
    private LocalDate eligibilityDate;

    @Column(name = "has_ever_held_content", nullable = false)
    @Builder.Default
    private boolean hasEverHeldContent = true;

    @Column(name = "blob_storage_uri")
    private String blobStorageUri;
}
