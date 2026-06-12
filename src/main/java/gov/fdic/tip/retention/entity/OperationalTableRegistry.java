package gov.fdic.tip.retention.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "operational_table_registry", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperationalTableRegistry extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "basis_date_column", nullable = false, length = 100)
    private String basisDateColumn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_sub_category_id", nullable = false)
    private RetentionSubCategory defaultSubCategory;

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
