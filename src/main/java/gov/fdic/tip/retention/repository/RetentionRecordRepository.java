package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionRecordRepository extends JpaRepository<RetentionRecord, UUID> {

    Optional<RetentionRecord> findByUpstreamModuleIdAndUpstreamReference(
            UUID upstreamModuleId, String upstreamReference);

    @Query("""
        SELECT r FROM RetentionRecord r
        WHERE r.upstreamModule.moduleCode = :moduleCode
          AND (:bucketCode IS NULL OR r.retentionBucket.bucketCode = :bucketCode)
        """)
    Page<RetentionRecord> findByModuleAndBucket(
            @Param("moduleCode") String moduleCode,
            @Param("bucketCode") String bucketCode,
            Pageable pageable);
}
