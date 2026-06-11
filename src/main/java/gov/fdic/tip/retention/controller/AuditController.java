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
 * Unified archive covering both Pattern A and Pattern B events.
 */
@RestController
@RequestMapping("/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit Archive", description = "Permanent retention audit trail – US-1.Audit-Lean")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(
        summary   = "Search the permanent audit archive",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "All filters are optional and combinable. Results are newest-first. " +
                      "Querying by category or sub-category returns events from ALL modules."
    )
    public Page<AuditEventResponse> search(
            @Parameter(description = "Filter by Category code, e.g. EXAM_RECORDS")
            @RequestParam(required = false) String categoryCode,

            @Parameter(description = "Filter by Sub-Category code, e.g. EXAM_FINDINGS_25Y")
            @RequestParam(required = false) String subCategoryCode,

            @Parameter(description = "Filter by module code (Azure AD appid)")
            @RequestParam(required = false) String moduleCode,

            @Parameter(description = "DOCUMENT_CLASSIFIED | RECORD_CLASSIFIED | CLASSIFICATION_FAILED")
            @RequestParam(required = false) RetentionAuditEvent.EventType eventType,

            @Parameter(description = "A | B")
            @RequestParam(required = false) RetentionAuditEvent.ClassificationPattern classificationPattern,

            @Parameter(description = "Events on or after this timestamp (ISO-8601)")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,

            @Parameter(description = "Events on or before this timestamp (ISO-8601)")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to,

            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return auditService.search(
                categoryCode, subCategoryCode, moduleCode,
                eventType, classificationPattern, from, to, page, size);
    }
}
