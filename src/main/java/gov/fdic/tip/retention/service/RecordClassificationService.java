package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.ClassifyRecordRequest;
import gov.fdic.tip.retention.dto.response.ClassifyRecordResponse;
import gov.fdic.tip.retention.entity.*;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.exception.TableNotRegisteredException;
import gov.fdic.tip.retention.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Pattern B – US-1.24-Lean: Classify Operational Record at INSERT.
 *
 * No database trigger involved. Instead:
 *   1. The upstream table is registered once via POST /v1/table-registrations.
 *   2. After each INSERT the upstream module calls POST /v1/classify-record,
 *      passing schema, table, pk_value, and basis_date.
 *   3. This service computes eligibilityDate and writes the audit event.
 *   4. The computed eligibilityDate is returned to the upstream module,
 *      which is responsible for persisting it on its own row.
 *
 * Sub-category resolution:
 *   - If the request supplies subCategoryCode → use that (per-row override).
 *   - Otherwise → use the registry default.
 *
 * moduleCode comes from the caller's Azure AD JWT, not from a DB table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecordClassificationService {

    private final OperationalTableRegistryRepository registryRepo;
    private final RetentionSubCategoryRepository     subCategoryRepo;
    private final RetentionAuditEventRepository      auditRepo;
    private final RetentionCalculator                calculator;

    // ── Classify (Pattern B) ────────────────────────────────────────────────

    @Transactional
    public ClassifyRecordResponse classifyRecord(String moduleCode,
                                                  ClassifyRecordRequest req) {
        long start = System.currentTimeMillis();

        // Load registry for this table
        OperationalTableRegistry registry = registryRepo
                .findBySchemaNameAndTableNameAndActiveTrue(req.getSchemaName(), req.getTableName())
                .orElseThrow(() -> new TableNotRegisteredException(
                        req.getSchemaName() + "." + req.getTableName()));

        // Resolve sub-category: per-row override → registry default
        RetentionSubCategory subCat = resolveSubCategory(req.getSubCategoryCode(), registry);

        // Compute eligibility date in Java (no DB trigger)
        LocalDate eligibilityDate = calculator.compute(
                req.getBasisDate(),
                subCat.getRetentionDurationValue(),
                subCat.getRetentionDurationUnit());

        // Write audit event
        RetentionAuditEvent audit = auditRepo.save(RetentionAuditEvent.builder()
                .eventType(RetentionAuditEvent.EventType.RECORD_CLASSIFIED)
                .classificationPattern(RetentionAuditEvent.ClassificationPattern.B)
                .entityType("record")
                .entityId(req.getPkValue())
                .tableSchema(req.getSchemaName())
                .tableName(req.getTableName())
                .moduleCode(moduleCode)
                .categoryCode(subCat.getCategory().getCode())
                .subCategoryCode(subCat.getCode())
                .basisDate(req.getBasisDate())
                .eligibilityDate(eligibilityDate)
                .retentionDurationValue(subCat.getRetentionDurationValue())
                .retentionDurationUnit(subCat.getRetentionDurationUnit().name())
                .hasEverHeldContent(true)
                .occurredAt(OffsetDateTime.now())
                .performedBy(moduleCode)
                .build());

        log.info("[CLASSIFY-B][SUCCESS] module={} table={}.{} pk={} eligibility={} elapsed={}ms",
                moduleCode, req.getSchemaName(), req.getTableName(),
                req.getPkValue(), eligibilityDate, System.currentTimeMillis() - start);

        return ClassifyRecordResponse.builder()
                .auditEventId(audit.getId())
                .schemaName(req.getSchemaName())
                .tableName(req.getTableName())
                .pkValue(req.getPkValue())
                .categoryCode(subCat.getCategory().getCode())
                .subCategoryCode(subCat.getCode())
                .retentionDurationValue(subCat.getRetentionDurationValue())
                .retentionDurationUnit(subCat.getRetentionDurationUnit().name())
                .basisDate(req.getBasisDate())
                .eligibilityDate(eligibilityDate)
                .classifiedAt(OffsetDateTime.now())
                .build();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Per-row override wins if supplied and valid.
     * Falls back to the registry default sub-category.
     */
    private RetentionSubCategory resolveSubCategory(String overrideCode,
                                                     OperationalTableRegistry registry) {
        if (overrideCode != null && !overrideCode.isBlank()) {
            return subCategoryRepo
                    .findByCodeAndActiveTrueAndClassificationAllowedTrue(overrideCode)
                    .orElseThrow(() -> new SubCategoryNotFoundException(overrideCode));
        }
        // Registry default – already validated at registration time, but double-check
        RetentionSubCategory def = registry.getDefaultSubCategory();
        if (!def.isActive() || !def.isClassificationAllowed()) {
            throw new SubCategoryNotFoundException(
                    "Registry default sub-category is no longer active: " + def.getCode());
        }
        return def;
    }
}
