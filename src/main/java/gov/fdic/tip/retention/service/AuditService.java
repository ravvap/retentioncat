package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.response.AuditEventResponse;
import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import gov.fdic.tip.retention.repository.RetentionAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * US-1.Audit-Lean: Reconstruct retention history for any record.
 * Serves the unified audit archive covering both Pattern A and Pattern B.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RetentionAuditEventRepository auditRepo;

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(
            String categoryCode,
            String subCategoryCode,
            String moduleCode,
            RetentionAuditEvent.EventType eventType,
            RetentionAuditEvent.ClassificationPattern pattern,
            OffsetDateTime from,
            OffsetDateTime to,
            int page, int size) {

        return auditRepo.search(
                categoryCode, subCategoryCode, moduleCode,
                eventType, pattern, from, to,
                PageRequest.of(page, size)
        ).map(this::toResponse);
    }

    private AuditEventResponse toResponse(RetentionAuditEvent e) {
        return AuditEventResponse.builder()
                .eventId(e.getId())
                .eventType(e.getEventType().name())
                .classificationPattern(e.getClassificationPattern().name())
                .moduleCode(e.getModuleCode())
                .sourceReference(e.getSourceReference())
                .categoryCode(e.getCategoryCode())
                .subCategoryCode(e.getSubCategoryCode())
                .retentionDurationValue(e.getRetentionDurationValue() != null
                        ? e.getRetentionDurationValue() : 0)
                .retentionDurationUnit(e.getRetentionDurationUnit())
                .basisDate(e.getBasisDate())
                .eligibilityDate(e.getEligibilityDate())
                .hasEverHeldContent(e.getHasEverHeldContent())
                .cmDocumentId(e.getCmDocumentId())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .tableSchema(e.getTableSchema())
                .tableName(e.getTableName())
                .reason(e.getReason())
                .occurredAt(e.getOccurredAt())
                .performedBy(e.getPerformedBy())
                .build();
    }
}
