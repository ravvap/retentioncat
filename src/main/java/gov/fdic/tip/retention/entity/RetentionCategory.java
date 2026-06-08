package gov.fdic.tip.retention.entity;

import gov.fdic.tip.retention.enums.RetentionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps to tip_retention_categories.
 * <p>
 * Business rules enforced here:
 * <ul>
 *   <li>code is immutable after creation (US-1.3 AC-5)</li>
 *   <li>status lifecycle: draft → active → inactive (US-1.2, US-1.4)</li>
 *   <li>has_ever_held_content is one-way true; never cleared</li>
 * </ul>
 */
@Entity
@Table(name = "tip_retention_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Stable machine identifier. Immutable after creation.
     * Regex: ^[A-Z][A-Z0-9_]{0,63}$  (1–64 chars, uppercase letters/digits/underscores).
     * Globally unique across all Categories regardless of status.
     */
    @Column(name = "code", nullable = false, unique = true, updatable = false, length = 64)
    private String code;

    /**
     * Human-readable label. 1–200 chars, case-insensitively unique across non-deleted categories.
     * Mutable at any status (draft, active, inactive).
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Optional description. Up to 2000 chars. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Lifecycle status.
     * draft   → created but not yet activated; cannot hold Sub-Categories
     * active  → fully operational; can hold Sub-Categories
     * inactive→ retired; cannot receive new Sub-Categories but existing ones still function
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private RetentionStatus status = RetentionStatus.draft;

    /**
     * One-way flag: set to true the first time any content is classified into
     * any Sub-Category beneath this Category. Never cleared.
     * Governs delete safety logic (US-1.5 AC-2).
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

    // ── Relationships ──────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RetentionSubCategory> subCategories = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ── Business helpers ───────────────────────────────────────────────────────

    /** Returns true when this category is able to accept new Sub-Categories. */
    public boolean isActive() {
        return RetentionStatus.active == this.status;
    }

    /** Returns true when the category is in draft or inactive (eligible to activate). */
    public boolean canBeActivated() {
        return RetentionStatus.draft == this.status || RetentionStatus.inactive == this.status;
    }

    /** Returns true when deactivation is allowed (only active categories). */
    public boolean canBeDeactivated() {
        return RetentionStatus.active == this.status;
    }

    /**
     * A Category can only be hard-deleted if:
     * 1. It is in inactive status.
     * 2. It has zero current Sub-Categories.
     * 3. If it has ever held content, the X-Confirmation-Code header must be supplied by the caller
     *    (enforced at the service layer, not here).
     */
    public boolean isEligibleForDeletion(int currentSubCategoryCount) {
        return RetentionStatus.inactive == this.status && currentSubCategoryCount == 0;
    }
}
