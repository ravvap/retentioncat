package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Base class providing full audit columns for all mutable entities:
 * created_at / created_by / updated_at / updated_by / deleted_at / deleted_by
 * <p>
 * Spring Data JPA Auditing populates created_at, created_by, updated_at, updated_by.
 * deleted_at / deleted_by are set explicitly by service layer (soft-delete pattern).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** Set by service layer on logical deletion (soft-delete). */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
