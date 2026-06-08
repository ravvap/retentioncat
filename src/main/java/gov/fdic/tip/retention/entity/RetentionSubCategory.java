package gov.fdic.tip.retention.entity;

import gov.fdic.tip.retention.enums.RetentionDurationUnit;
import gov.fdic.tip.retention.enums.RetentionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to tip_retention_sub_categories.
 * <p>
 * Business rules enforced here:
 * <ul>
 *   <li>code is immutable after creation (US-1.8 AC-7)</li>
 *   <li>retentionDurationValue must be a positive integer</li>
 *   <li>retentionDurationUnit is days | months | years</li>
 *   <li>status lifecycle: draft → active → inactive (US-1.7, US-1.10)</li>
 *   <li>has_ever_held_content is one-way true; never cleared (US-1.11 AC-1)</li>
 *   <li>parent Category must be Active to create or activate a Sub-Category (US-1.6, US-1.7)</li>
 * </ul>
 */
@Entity
@Table(
    name = "tip_retention_sub_categories",
    uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "code"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionSubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Parent Category FK. Moving a Sub-Category (US-1.9) updates this field;
     * target Category must be Active.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private RetentionCategory category;

    /**
     * Stable machine identifier. 1–64 chars, uppercase letters/digits/underscores.
     * Unique within the parent Category. Immutable after creation.
     */
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    /**
     * Human-readable label. 1–200 chars, case-insensitively unique within the parent Category.
     * Mutable at any status.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Optional description, up to 2000 chars. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Positive integer portion of the retention period (e.g. 7 for "7 years").
     * Editing this value triggers an asynchronous cascade re-evaluation of all classified items.
     */
    @Column(name = "retention_duration_value", nullable = false)
    private Integer retentionDurationValue;

    /**
     * Unit portion of the retention period: days | months | years.
     * Editing this value also triggers the cascade.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_duration_unit", nullable = false, length = 16)
    private RetentionDurationUnit retentionDurationUnit;

    /**
     * Lifecycle status.
     * draft    → created, cannot receive classifications
     * active   → receiving new classifications
     * inactive → closed to new classifications; existing items retain their retention
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private RetentionStatus status = RetentionStatus.draft;

    /**
     * One-way flag set by the DB INSERT trigger the first time content is classified here.
     * A Sub-Category with has_ever_held_content=true is HARD-BLOCKED from deletion (US-1.11 AC-1).
     * No Admin override or confirmation header can bypass this.
     */
    @Column(name = "has_ever_held_content", nullable = false)
    @Builder.Default
    private Boolean hasEverHeldContent = false;

    // ── Audit columns ──────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ── Business helpers ───────────────────────────────────────────────────────

    public boolean isActive() {
        return RetentionStatus.active == this.status;
    }

    /** Eligible to activate if draft or inactive AND parent Category is also active. */
    public boolean canBeActivated() {
        return (RetentionStatus.draft == this.status || RetentionStatus.inactive == this.status)
                && category != null && category.isActive();
    }

    public boolean canBeDeactivated() {
        return RetentionStatus.active == this.status;
    }

    /**
     * Sub-Category delete gate (stricter than Category).
     * Must be inactive AND has_ever_held_content must be FALSE.
     * There is no confirmation-header escape for Sub-Categories (US-1.11 AC-1).
     */
    public boolean isEligibleForDeletion() {
        return RetentionStatus.inactive == this.status && !hasEverHeldContent;
    }

    /**
     * Computes the canonical eligibility date for a given basis date.
     * Mirrors the DB function tip_compute_eligibility_date (ADR-RET-001).
     * Returns null when basisDate is null (NULL short-circuit).
     */
    public LocalDate computeEligibilityDate(LocalDate basisDate) {
        if (basisDate == null) return null;
        return switch (retentionDurationUnit) {
            case days   -> basisDate.plusDays(retentionDurationValue);
            case months -> basisDate.plusMonths(retentionDurationValue);
            case years  -> basisDate.plusYears(retentionDurationValue);
        };
    }
}
