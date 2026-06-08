package gov.fdic.tip.retention.repository;

import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionAuditEventRepository extends JpaRepository<RetentionAuditEvent, UUID> {

    List<RetentionAuditEvent> findByEventTypeStartingWithAndOccurredAtBetweenOrderByOccurredAtDesc(
        String eventTypePrefix, OffsetDateTime from, OffsetDateTime to);

    List<RetentionAuditEvent> findByActorUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        String actorUserId, OffsetDateTime from, OffsetDateTime to);

    List<RetentionAuditEvent> findByCorrelationIdOrderByOccurredAtAsc(UUID correlationId);
}
