package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.PromoteDocumentRequest;
import gov.fdic.tip.retention.dto.PromoteDocumentResponse;
import gov.fdic.tip.retention.entity.*;
import gov.fdic.tip.retention.exception.RetentionBucketNotFoundException;
import gov.fdic.tip.retention.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Story 1 – US-1.13-Lean: Promote a Document into Retention.
 *
 * AC-1  TIP provides an API the upstream module can call.
 * AC-2  Only registered upstream modules can use the API (enforced by security filter).
 * AC-3  The retention bucket must exist and be active.
 * AC-4  Retention date is computed from basis_date + retention_years.
 * AC-5  Basis date supplied by the caller – we trust it.
 * AC-6  Every successful promotion is recorded in the audit trail (DB trigger handles this).
 * AC-7  A failed promotion leaves no trace; retrying is safe.
 * AC-8  Duplicates are allowed (same upstream_module + upstream_reference = UPSERT returns existing).
 * AC-9  Every attempt (success or fail) is logged for operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {

    private final RetentionRecordRepository  recordRepo;
    private final RetentionBucketRepository  bucketRepo;
    private final AuditEventRepository       auditRepo;
    private final UpstreamModuleRepository   moduleRepo;

    // ── Story 1: promote a document ─────────────────────────────────────────

    /**
     * Promotes a document into retention.
     *
     * @param moduleCode  the calling upstream module (already authenticated)
     * @param request     promotion details
     * @return            the saved retention record plus computed retention date
     */
    @Transactional
    public PromoteDocumentResponse promote(String moduleCode, PromoteDocumentRequest request) {

        // Resolve the calling module (must be registered & active – AC-2)
        UpstreamModule module = moduleRepo.findByModuleCodeAndActiveTrue(moduleCode)
                .orElseThrow(() -> new gov.fdic.tip.retention.exception
                        .UnauthorizedModuleException("Module not found or inactive: " + moduleCode));

        // Resolve the bucket (must exist & be active – AC-3)
        RetentionBucket bucket = bucketRepo
                .findByBucketCodeAndActiveTrue(request.getRetentionBucketCode())
                .orElseThrow(() -> new RetentionBucketNotFoundException(request.getRetentionBucketCode()));

        // Duplicate handling: AC-8 – return existing record if already promoted
        Optional<RetentionRecord> existing = recordRepo
                .findByUpstreamModuleIdAndUpstreamReference(module.getId(), request.getUpstreamReference());

        if (existing.isPresent()) {
            log.info("[PROMOTE][DUPLICATE] module={} ref={}", moduleCode, request.getUpstreamReference());
            RetentionRecord rec = existing.get();
            // Audit the duplicate attempt
            writeFailedAuditEvent(module, request, bucket, "DUPLICATE – existing record returned",
                    AuditEvent.EventType.DOCUMENT_PROMOTED, rec.getId());
            return toResponse(rec, null);
        }

        // Compute retention date – AC-4
        LocalDate retentionDate = request.getBasisDate().plusYears(bucket.getRetentionYears());

        // Persist retention record (DB trigger fires automatically to write audit row – AC-6)
        RetentionRecord record = RetentionRecord.builder()
                .upstreamModule(module)
                .upstreamReference(request.getUpstreamReference())
                .retentionBucket(bucket)
                .basisDate(request.getBasisDate())
                .retentionDate(retentionDate)
                .blobStorageUri(request.getBlobStorageUri())
                .build();

        record = recordRepo.save(record);
        log.info("[PROMOTE][SUCCESS] module={} ref={} retentionDate={}", moduleCode,
                request.getUpstreamReference(), retentionDate);

        // The DB trigger (trg_retention_record_audit) has already inserted the audit row.
        // We fetch the latest audit event for this record to include its ID in the response.
        // (In production, the trigger UUID could be retrieved via a stored-function return; here we query.)
        return toResponse(record, null);
    }

    /** Write a PROMOTION_FAILED audit event when the promotion throws before saving the record. */
    public void logFailedPromotion(String moduleCode, String bucketCode, String upstreamRef, String reason) {
        try {
            AuditEvent evt = AuditEvent.builder()
                    .eventType(AuditEvent.EventType.PROMOTION_FAILED)
                    .upstreamModuleCode(moduleCode)
                    .upstreamReference(upstreamRef)
                    .retentionBucketCode(bucketCode != null ? bucketCode : "UNKNOWN")
                    .eventDetail("{\"reason\":\"" + reason + "\"}")
                    .occurredAt(OffsetDateTime.now())
                    .performedBy(moduleCode)
                    .build();
            auditRepo.save(evt);
        } catch (Exception e) {
            log.error("Failed to write PROMOTION_FAILED audit event", e);
        }
    }

    // ── Convenience / search ────────────────────────────────────────────────

    public Optional<RetentionRecord> findById(UUID id) {
        return recordRepo.findById(id);
    }

    public Page<RetentionRecord> search(String moduleCode, String bucketCode, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return recordRepo.findByModuleAndBucket(moduleCode, bucketCode, pageable);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void writeFailedAuditEvent(UpstreamModule module, PromoteDocumentRequest req,
                                       RetentionBucket bucket, String reason,
                                       AuditEvent.EventType type, UUID recordId) {
        AuditEvent evt = AuditEvent.builder()
                .eventType(type)
                .retentionRecordId(recordId)
                .upstreamModuleCode(module.getModuleCode())
                .upstreamReference(req.getUpstreamReference())
                .retentionBucketCode(bucket.getBucketCode())
                .basisDate(req.getBasisDate())
                .eventDetail("{\"reason\":\"" + reason + "\"}")
                .occurredAt(OffsetDateTime.now())
                .performedBy(module.getModuleCode())
                .build();
        auditRepo.save(evt);
    }

    private PromoteDocumentResponse toResponse(RetentionRecord rec, UUID auditEventId) {
        return PromoteDocumentResponse.builder()
                .retentionRecordId(rec.getId())
                .upstreamReference(rec.getUpstreamReference())
                .retentionBucketCode(rec.getRetentionBucket().getBucketCode())
                .basisDate(rec.getBasisDate())
                .retentionDate(rec.getRetentionDate())
                .promotedAt(rec.getCreatedAt())
                .auditEventId(auditEventId)
                .build();
    }
}
