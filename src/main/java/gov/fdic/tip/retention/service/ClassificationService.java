package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.ClassifyDocumentRequest;
import gov.fdic.tip.retention.dto.response.ClassifyDocumentResponse;
import gov.fdic.tip.retention.entity.CmDocument;
import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.repository.CmDocumentRepository;
import gov.fdic.tip.retention.repository.RetentionAuditEventRepository;
import gov.fdic.tip.retention.repository.RetentionSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Pattern A – US-1.13-Lean: Classify Document at Creation.
 *
 * Responsibilities:
 *  1. Validate sub-category (active + classification_allowed)
 *  2. Idempotency check (moduleCode + sourceReference must be unique)
 *  3. Compute eligibility_date = basisDate + retention duration (Java)
 *  4. Persist CmDocument
 *  5. Write RetentionAuditEvent(DOCUMENT_CLASSIFIED)
 *  Steps 3-5 run inside a single @Transactional – atomic rollback on failure.
 *
 * moduleCode is extracted from the Azure AD JWT by the controller
 * and passed in; it is NOT fetched from a module table.
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

        // Validate sub-category: must be active AND classification_allowed
        RetentionSubCategory subCat = subCategoryRepo
                .findByCodeAndActiveTrueAndClassificationAllowedTrue(req.getSubCategoryCode())
                .orElseThrow(() -> {
                    log.warn("[CLASSIFY-A][INVALID_SUBCAT] module={} subCat={}",
                            moduleCode, req.getSubCategoryCode());
                    return new SubCategoryNotFoundException(req.getSubCategoryCode());
                });

        // Idempotency: same module + sourceReference → return the existing record
        Optional<CmDocument> existing =
                documentRepo.findByModuleCodeAndSourceReference(moduleCode, req.getSourceReference());
        if (existing.isPresent()) {
            log.info("[CLASSIFY-A][IDEMPOTENT] module={} ref={} elapsed={}ms",
                    moduleCode, req.getSourceReference(), elapsed(start));
            return toResponse(existing.get());
        }

        // Compute eligibility date in Java (mirrors DB fn_compute_eligibility_date)
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
                .hasEverHeldContent(true)
                .blobStorageUri(req.getBlobStorageUri())
                .build();

        doc = documentRepo.save(doc);

        // Write audit event – atomic with the document persist above
        RetentionAuditEvent audit = writeAuditEvent(doc, subCat);

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
        return documentRepo
                .search(moduleCode, categoryCode, subCatCode, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    // ── Failure audit ───────────────────────────────────────────────────────

    /**
     * Writes a CLASSIFICATION_FAILED audit event in a NEW transaction so the
     * failure is recorded even when the main transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            log.error("[CLASSIFY-A][AUDIT_FAIL] Could not write failure audit event", e);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RetentionAuditEvent writeAuditEvent(CmDocument doc, RetentionSubCategory sc) {
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

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
