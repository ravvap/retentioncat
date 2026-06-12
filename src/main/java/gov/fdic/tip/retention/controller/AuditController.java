package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.response.AuditEventResponse;
import gov.fdic.tip.retention.entity.RetentionAuditEvent;
import gov.fdic.tip.retention.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * US-1.Audit-Lean: Reconstruct retention history.
 *
 * Unified archive covering both Pattern A (DOCUMENT_CLASSIFIED) and
 * Pattern B (RECORD_CLASSIFIED) events. Results are newest-first.
 *
 * Archive is permanent and immutable – DB RULES prevent UPDATE/DELETE.
 */
@RestController
@RequestMapping("/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit Archive",
     description = "Permanent, immutable retention audit trail – US-1.Audit-Lean")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(
        summary   = "Search the permanent audit archive",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "All filters are optional and combinable. Results are newest-first, paginated.\n\n" +
                      "**Pattern A events** have `classificationPattern=A`, populated `cmDocumentId`.\n\n" +
                      "**Pattern B events** have `classificationPattern=B`, populated `tableSchema`, " +
                      "`tableName`, `entityId`."
    )
    public Page<AuditEventResponse> search(
            @Parameter(description = "Filter by Category code, e.g. EXAM_RECORDS")
            @RequestParam(required = false) String categoryCode,

            @Parameter(description = "Filter by Sub-Category code, e.g. EXAM_FINDINGS_25Y")
            @RequestParam(required = false) String subCategoryCode,

            @Parameter(description = "Filter by module code (Azure AD appid claim)")
            @RequestParam(required = false) String moduleCode,

            @Parameter(description = "DOCUMENT_CLASSIFIED | RECORD_CLASSIFIED | CLASSIFICATION_FAILED")
            @RequestParam(required = false) RetentionAuditEvent.EventType eventType,

            @Parameter(description = "Classification pattern: A | B")
            @RequestParam(required = false) RetentionAuditEvent.ClassificationPattern classificationPattern,

            @Parameter(description = "Events on or after this ISO-8601 timestamp, e.g. 2026-01-01T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,

            @Parameter(description = "Events on or before this ISO-8601 timestamp")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to,

            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return auditService.search(
                categoryCode, subCategoryCode, moduleCode,
                eventType, classificationPattern,
                from, to, page, size);
    }
}
