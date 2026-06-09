package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.AuditEventResponse;
import gov.fdic.tip.retention.entity.AuditEvent;
import gov.fdic.tip.retention.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * Story 3 – US-1.Audit-Lean: Reconstruct retention history for any record.
 *
 * AC-4  Pre-built dashboards / query endpoints cover common questions.
 * AC-6  Querying by bucket returns events from ALL upstream modules.
 */
@RestController
@RequestMapping("/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Story 3 – Query the permanent retention audit trail")
public class AuditController {

    private final AuditService auditService;

    /**
     * GET /v1/audit-events
     * Flexible search supporting all common auditor queries.
     *
     * Examples:
     *   ?bucketCode=EXAM_FINDINGS_25Y                            → all events for that bucket (AC-6)
     *   ?bucketCode=EXAM_FINDINGS_25Y&from=2026-05-01T00:00:00Z → last month in that bucket
     *   ?moduleCode=EW                                           → all events from Exam Workflow
     *   ?eventType=DOCUMENT_PROMOTED                            → only promotions
     */
    @GetMapping
    @Operation(
        summary     = "Search the audit trail",
        description = "Returns a paginated, permanent record of all retention decisions. " +
                      "A query by bucket returns events from all upstream modules (AC-6). " +
                      "Results are always newest-first."
    )
    public Page<AuditEventResponse> search(
            @Parameter(description = "Filter by retention bucket code")
            @RequestParam(required = false) String bucketCode,

            @Parameter(description = "Filter by upstream module code")
            @RequestParam(required = false) String moduleCode,

            @Parameter(description = "Filter by event type")
            @RequestParam(required = false) AuditEvent.EventType eventType,

            @Parameter(description = "Events on or after this timestamp (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @Parameter(description = "Events on or before this timestamp (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,

            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return auditService.search(bucketCode, moduleCode, eventType, from, to, page, size);
    }
}
