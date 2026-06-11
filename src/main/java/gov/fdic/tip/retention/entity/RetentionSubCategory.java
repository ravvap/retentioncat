package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Leaf retention bucket – the only level where a retention duration is defined.
 * Callers supply a sub_category_code; the service looks up duration from here.
 */
@Entity
@Table(name = "retention_sub_category", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionSubCategory extends Auditable {

    public enum DurationUnit { DAYS, MONTHS, YEARS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private RetentionCategory category;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Column(name = "retention_duration_value", nullable = false)
    private short retentionDurationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_duration_unit", nullable = false, length = 10)
    private DurationUnit retentionDurationUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fallback_sub_category_id")
    private RetentionSubCategory fallbackSubCategory;

    @Column(name = "classification_allowed", nullable = false)
    @Builder.Default
    private boolean classificationAllowed = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
