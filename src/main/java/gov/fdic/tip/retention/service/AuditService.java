package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.AuditEventResponse;
import gov.fdic.tip.retention.entity.AuditEvent;
import gov.fdic.tip.retention.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Story 3 – US-1.Audit-Lean: Reconstruct retention history for any record.
 *
 * AC-1  One audit trail covers both document promotions and auto-classifications.
 * AC-2  Each event has enough context to stand on its own (denormalised snapshot).
 * AC-3  Audit events are permanent; DB rules prevent UPDATE/DELETE.
 * AC-4  Pre-built query endpoints serve common auditor questions.
 * AC-5  One-off queries serviced by a designated analyst (not in scope of API).
 * AC-6  Querying by bucket returns events from all upstream modules.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditRepo;

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(
            String bucketCode,
            String moduleCode,
            AuditEvent.EventType eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());

        return auditRepo.search(bucketCode, moduleCode, eventType, from, to, pageable)
                .map(this::toResponse);
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return AuditEventResponse.builder()
                .eventId(e.getId())
                .eventType(e.getEventType().name())
                .upstreamModuleCode(e.getUpstreamModuleCode())
                .upstreamReference(e.getUpstreamReference())
                .retentionBucketCode(e.getRetentionBucketCode())
                .basisDate(e.getBasisDate())
                .retentionDate(e.getRetentionDate())
                .occurredAt(e.getOccurredAt())
                .performedBy(e.getPerformedBy())
                .eventDetail(e.getEventDetail())
                .build();
    }
}
