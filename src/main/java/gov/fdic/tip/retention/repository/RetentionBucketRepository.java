package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionBucketRepository extends JpaRepository<RetentionBucket, UUID> {
    Optional<RetentionBucket> findByBucketCodeAndActiveTrue(String bucketCode);
}
