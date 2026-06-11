package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Base class for all mutable entities.
 * created_at / created_by → set once on INSERT by Spring Data JPA auditing.
 * updated_at / updated_by → refreshed on every UPDATE.
 * deleted_at / deleted_by → soft-delete; set by service, never hard-deleted in lean MVP.
 */
@Getter @Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 200)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 200)
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
