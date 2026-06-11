package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.ClassifyDocumentRequest;
import gov.fdic.tip.retention.dto.response.ClassifyDocumentResponse;
import gov.fdic.tip.retention.entity.*;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Pattern A – US-1.13-Lean: Classify Document at Creation.
 *
 * No database trigger involved. This service:
 *   1. Validates the sub-category (active + classification_allowed)
 *   2. Checks idempotency (moduleCode + sourceReference must be unique)
 *   3. Computes eligibilityDate = basisDate + sub-category retention duration
 *   4. Persists CmDocument
 *   5. Writes RetentionAuditEvent(DOCUMENT_CLASSIFIED)
 *   All steps run inside one @Transactional block – rollback on any failure.
 *
 * moduleCode comes from the caller's Azure AD JWT, not from a DB table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationService {

    private final CmDocumentRepository          documentRepo;
    private final RetentionSubCategoryRepository subCategoryRepo;
    private final RetentionAuditEventRepository  auditRepo;
    private final RetentionCalculator            calculator;

    // ── Classify (Pattern A) ────────────────────────────────────────────────

    @Transactional
    public ClassifyDocumentResponse classifyDocument(String moduleCode,
                                                      ClassifyDocumentRequest req) {
        long start = System.currentTimeMillis();

        // AC-3: validate sub-category
        RetentionSubCategory subCat = subCategoryRepo
                .findByCodeAndActiveTrueAndClassificationAllowedTrue(req.getSubCategoryCode())
                .orElseThrow(() -> {
                    log.warn("[CLASSIFY-A][INVALID_SUBCAT] module={} subCat={}",
                            moduleCode, req.getSubCategoryCode());
                    return new SubCategoryNotFoundException(req.getSubCategoryCode());
                });

        // AC-8: idempotency – return existing record for same module + reference
        Optional<CmDocument> existing =
                documentRepo.findByModuleCodeAndSourceReference(moduleCode, req.getSourceReference());
        if (existing.isPresent()) {
            log.info("[CLASSIFY-A][IDEMPOTENT] module={} ref={} elapsed={}ms",
                    moduleCode, req.getSourceReference(), elapsed(start));
            return toResponse(existing.get());
        }

        // AC-4: compute eligibility date in Java (no DB trigger)
        LocalDate eligibilityDate = calculator.compute(
                req.getBasisDate(),
                subCat.getRetentionDurationValue(),
                subCat.getRetentionDurationUnit());

        // Persist document
        CmDocument doc = CmDocument.builder()
                .moduleCode(moduleCode)
                .sourceSystem(req.getSourceSystem())
                .sourceReference(req.getSourceReference())
                .filename(req.getFilename())
                .sizeBytes(req.getSizeBytes())
                .sha256(req.getSha256())
                .subCategory(subCat)
                .basisDate(req.getBasisDate())
                .eligibilityDate(eligibilityDate)
                .hasEverHeldContent(true)          // AC-6: first classification → true
                .blobStorageUri(req.getBlobStorageUri())
                .build();

        doc = documentRepo.save(doc);

        // Write audit event (AC-5: every successful classification writes one row)
        RetentionAuditEvent audit = writeAudit(doc, subCat);

        log.info("[CLASSIFY-A][SUCCESS] module={} ref={} eligibility={} elapsed={}ms",
                moduleCode, req.getSourceReference(), eligibilityDate, elapsed(start));

        return toResponse(doc).toBuilder().auditEventId(audit.getId()).build();
    }

    // ── Query ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<ClassifyDocumentResponse> findById(UUID id) {
        return documentRepo.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ClassifyDocumentResponse> search(String moduleCode, String categoryCode,
                                                  String subCatCode, int page, int size) {
        return documentRepo.search(moduleCode, categoryCode, subCatCode,
                PageRequest.of(page, size)).map(this::toResponse);
    }

    // ── Failure audit (AC-9) ────────────────────────────────────────────────

    /**
     * Writes a CLASSIFICATION_FAILED audit event outside any active transaction.
     * Called by the controller's catch block so it survives a rollback.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logFailure(String moduleCode, String subCatCode,
                           String sourceRef, String reason) {
        try {
            auditRepo.save(RetentionAuditEvent.builder()
                    .eventType(RetentionAuditEvent.EventType.CLASSIFICATION_FAILED)
                    .classificationPattern(RetentionAuditEvent.ClassificationPattern.A)
                    .moduleCode(moduleCode)
                    .sourceReference(sourceRef)
                    .subCategoryCode(subCatCode != null ? subCatCode : "UNKNOWN")
                    .categoryCode("UNKNOWN")
                    .reason(reason)
                    .occurredAt(OffsetDateTime.now())
                    .performedBy(moduleCode)
                    .build());
        } catch (Exception e) {
            log.error("[CLASSIFY-A][AUDIT_FAIL] Could not write failure event", e);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RetentionAuditEvent writeAudit(CmDocument doc, RetentionSubCategory sc) {
        return auditRepo.save(RetentionAuditEvent.builder()
                .eventType(RetentionAuditEvent.EventType.DOCUMENT_CLASSIFIED)
                .classificationPattern(RetentionAuditEvent.ClassificationPattern.A)
                .cmDocumentId(doc.getId())
                .entityType("document")
                .entityId(doc.getId().toString())
                .moduleCode(doc.getModuleCode())
                .sourceReference(doc.getSourceReference())
                .categoryCode(sc.getCategory().getCode())
                .subCategoryCode(sc.getCode())
                .basisDate(doc.getBasisDate())
                .eligibilityDate(doc.getEligibilityDate())
                .retentionDurationValue(sc.getRetentionDurationValue())
                .retentionDurationUnit(sc.getRetentionDurationUnit().name())
                .hasEverHeldContent(true)
                .occurredAt(OffsetDateTime.now())
                .performedBy(doc.getModuleCode())
                .build());
    }

    private ClassifyDocumentResponse toResponse(CmDocument doc) {
        RetentionSubCategory sc  = doc.getSubCategory();
        RetentionCategory    cat = sc.getCategory();
        return ClassifyDocumentResponse.builder()
                .retentionRecordId(doc.getId())
                .sourceReference(doc.getSourceReference())
                .moduleCode(doc.getModuleCode())
                .categoryCode(cat.getCode())
                .categoryName(cat.getName())
                .subCategoryCode(sc.getCode())
                .subCategoryName(sc.getName())
                .retentionDurationValue(sc.getRetentionDurationValue())
                .retentionDurationUnit(sc.getRetentionDurationUnit().name())
                .basisDate(doc.getBasisDate())
                .eligibilityDate(doc.getEligibilityDate())
                .classifiedAt(doc.getCreatedAt())
                .build();
    }

    private long elapsed(long start) { return System.currentTimeMillis() - start; }
}
