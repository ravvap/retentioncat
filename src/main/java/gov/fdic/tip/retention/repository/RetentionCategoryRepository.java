package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.enums.RetentionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionCategoryRepository extends JpaRepository<RetentionCategory, UUID> {

    /** US-1.1 AC-2: global code uniqueness check across all non-deleted categories. */
    boolean existsByCodeIgnoreCase(String code);

    /** US-1.1 AC-3 / US-1.3 AC-1: case-insensitive name uniqueness. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID excludeId);

    boolean existsByNameIgnoreCase(String name);

    /** Hierarchy view (US-1.12): filterable by status. */
    List<RetentionCategory> findByStatusOrderByNameAsc(RetentionStatus status);

    /** Hierarchy view without status filter. */
    List<RetentionCategory> findAllByOrderByNameAsc();

    /** Case-insensitive name search across all statuses (US-1.12 AC-3). */
    @Query("SELECT c FROM RetentionCategory c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY c.name")
    List<RetentionCategory> searchByName(@Param("search") String search);

    /** Counts current (non-deleted) sub-categories under a Category for delete gate. */
    @Query("SELECT COUNT(sc) FROM RetentionSubCategory sc WHERE sc.category.id = :categoryId AND sc.deletedAt IS NULL")
    long countActiveSubCategories(@Param("categoryId") UUID categoryId);

    /** All active Categories – used for move-Sub-Category destination picker (US-1.9). */
    List<RetentionCategory> findByStatusIn(List<RetentionStatus> statuses);

    Optional<RetentionCategory> findByCode(String code);
}
