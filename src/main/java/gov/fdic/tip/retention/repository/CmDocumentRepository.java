package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.CmDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmDocumentRepository extends JpaRepository<CmDocument, UUID> {
    Optional<CmDocument> findByModuleCodeAndSourceReference(String moduleCode, String sourceReference);

    @Query("""
        SELECT d FROM CmDocument d
        WHERE (:moduleCode   IS NULL OR d.moduleCode                   = :moduleCode)
          AND (:categoryCode IS NULL OR d.subCategory.category.code    = :categoryCode)
          AND (:subCatCode   IS NULL OR d.subCategory.code             = :subCatCode)
          AND d.deletedAt IS NULL
        ORDER BY d.createdAt DESC
        """)
    Page<CmDocument> search(
            @Param("moduleCode")   String moduleCode,
            @Param("categoryCode") String categoryCode,
            @Param("subCatCode")   String subCatCode,
            Pageable pageable);
}
