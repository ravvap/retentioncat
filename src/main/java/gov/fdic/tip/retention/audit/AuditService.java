package gov.fdic.tip.retention.audit;

import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import gov.fdic.tip.retention.repository.RetentionAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Application-level audit emission for taxonomy CRUD events.
 * Document/record classification events are written exclusively by DB triggers.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RetentionAuditEventRepository auditRepo;

    public void emitCategoryCreated(UUID categoryId, String code, String name,
                                    String status, String actor) {
        emit("retention.category.created", "category",
             Map.of("id", categoryId),
             Map.of("code", code, "name", name, "status", status),
             actor, null);
    }

    public void emitCategoryEdited(UUID categoryId, Map<String, Object> before,
                                   Map<String, Object> after, String actor) {
        emit("retention.category.edited", "category",
             Map.of("id", categoryId),
             Map.of("before", before, "after", after),
             actor, null);
    }

    public void emitCategoryActivated(UUID categoryId, String priorStatus,
                                      String newStatus, String actor) {
        emit("retention.category.activated", "category",
             Map.of("id", categoryId),
             Map.of("prior_status", priorStatus, "new_status", newStatus),
             actor, null);
    }

    public void emitCategoryDeactivated(UUID categoryId, String actor, String comment) {
        emit("retention.category.deactivated", "category",
             Map.of("id", categoryId),
             Map.of("new_status", "inactive", "comment", comment != null ? comment : ""),
             actor, null);
    }

    public void emitCategoryDeleted(UUID categoryId, String code, String name, String actor) {
        emit("retention.category.deleted", "category",
             Map.of("id", categoryId),
             Map.of("code", code, "name", name),
             actor, null);
    }

    public void emitSubCategoryCreated(UUID subCatId, UUID categoryId, String code,
                                       String name, int durationValue, String durationUnit,
                                       String actor) {
        emit("retention.subcategory.created", "sub-category",
             Map.of("id", subCatId),
             Map.of("category_id", categoryId, "code", code, "name", name,
                    "retention_duration_value", durationValue,
                    "retention_duration_unit", durationUnit),
             actor, null);
    }

    public void emitSubCategoryEdited(UUID subCatId, Map<String, Object> before,
                                      Map<String, Object> after, String reason,
                                      String actor, UUID correlationId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("before", before);
        payload.put("after", after);
        if (reason != null) payload.put("reason", reason);
        emit("retention.subcategory.edited", "sub-category",
             Map.of("id", subCatId), payload, actor, correlationId);
    }

    public void emitSubCategoryActivated(UUID subCatId, String priorStatus, String actor) {
        emit("retention.subcategory.activated", "sub-category",
             Map.of("id", subCatId),
             Map.of("prior_status", priorStatus, "new_status", "active"),
             actor, null);
    }

    public void emitSubCategoryDeactivated(UUID subCatId, String actor, String comment) {
        emit("retention.subcategory.deactivated", "sub-category",
             Map.of("id", subCatId),
             Map.of("new_status", "inactive", "comment", comment != null ? comment : ""),
             actor, null);
    }

    public void emitSubCategoryMoved(UUID subCatId, UUID priorCategoryId,
                                     UUID newCategoryId, String actor) {
        emit("retention.subcategory.moved", "sub-category",
             Map.of("id", subCatId),
             Map.of("prior_category_id", priorCategoryId, "new_category_id", newCategoryId),
             actor, null);
    }

    public void emitSubCategoryDeleted(UUID subCatId, String code, String name, String actor) {
        emit("retention.subcategory.deleted", "sub-category",
             Map.of("id", subCatId),
             Map.of("code", code, "name", name),
             actor, null);
    }

    public void emitSubCategoryCascadeQueued(UUID subCatId, UUID correlationId,
                                              int affectedCount, String actor) {
        emit("retention.subcategory.cascade.queued", "sub-category",
             Map.of("id", subCatId),
             Map.of("affected_count", affectedCount, "correlation_id", correlationId),
             actor, correlationId);
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private void emit(String eventType, String entityType, Map<String, Object> entityId,
                      Map<String, Object> payload, String actor, UUID correlationId) {
        auditRepo.save(RetentionAuditEvent.builder()
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .payload(payload)
            .actorUserId(actor)
            .correlationId(correlationId)
            .occurredAt(OffsetDateTime.now())
            .createdBy(actor != null ? actor : "system")
            .updatedBy(actor != null ? actor : "system")
            .build());
    }
}
