package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionSubCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionSubCategoryRepository extends JpaRepository<RetentionSubCategory, UUID> {

    /** Used by classification: must be active AND classification_allowed. */
    Optional<RetentionSubCategory> findByCodeAndActiveTrueAndClassificationAllowedTrue(String code);

    Page<RetentionSubCategory> findByCategoryCodeAndActiveTrue(String categoryCode, Pageable pageable);

    Page<RetentionSubCategory> findAllByActiveTrue(Pageable pageable);
}
