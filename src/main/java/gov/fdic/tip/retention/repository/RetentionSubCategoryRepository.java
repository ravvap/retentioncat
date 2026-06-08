package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.enums.RetentionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionSubCategoryRepository extends JpaRepository<RetentionSubCategory, UUID> {

    /** US-1.6 AC-2: code unique within parent Category. */
    boolean existsByCategoryIdAndCodeIgnoreCaseAndIdNot(UUID categoryId, String code, UUID excludeId);
    boolean existsByCategoryIdAndCodeIgnoreCase(UUID categoryId, String code);

    /** US-1.6 AC-3: name unique (case-insensitive) within parent Category. */
    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(UUID categoryId, String name, UUID excludeId);
    boolean existsByCategoryIdAndNameIgnoreCase(UUID categoryId, String name);

    List<RetentionSubCategory> findByCategoryIdOrderByNameAsc(UUID categoryId);

    List<RetentionSubCategory> findByCategoryIdAndStatusOrderByNameAsc(UUID categoryId, RetentionStatus status);

    /** US-1.9 AC-3: check for code/name conflicts before moving to a new parent. */
    Optional<RetentionSubCategory> findByCategoryIdAndCodeIgnoreCase(UUID categoryId, String code);
    Optional<RetentionSubCategory> findByCategoryIdAndNameIgnoreCase(UUID categoryId, String name);

    /**
     * Count classified documents for delete safety (US-1.11).
     * A Sub-Category with has_ever_held_content is hard-blocked; this count is for informational error messages.
     */
    @Query(value = """
        SELECT COUNT(*) FROM content_manager.documents d
        WHERE d.sub_category_id = :subCategoryId
        """, nativeQuery = true)
    long countClassifiedDocuments(@Param("subCategoryId") UUID subCategoryId);

    /** Search by name across hierarchy (US-1.12 AC-3). */
    @Query("SELECT sc FROM RetentionSubCategory sc WHERE LOWER(sc.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY sc.name")
    List<RetentionSubCategory> searchByName(@Param("search") String search);

    /** All sub-categories with a given status – used by cascade worker. */
    List<RetentionSubCategory> findByStatus(RetentionStatus status);
}
