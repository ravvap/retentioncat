package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "retention_bucket", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionBucket extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bucket_code", nullable = false, unique = true, length = 50)
    private String bucketCode;

    @Column(name = "bucket_name", nullable = false, length = 200)
    private String bucketName;

    @Column(name = "retention_years", nullable = false)
    private short retentionYears;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
