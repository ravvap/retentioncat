package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionCategoryRepository extends JpaRepository<RetentionCategory, UUID> {
    Optional<RetentionCategory> findByCodeAndActiveTrue(String code);
    Page<RetentionCategory> findAllByActiveTrue(Pageable pageable);
}
