package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.PromoteDocumentRequest;
import gov.fdic.tip.retention.dto.PromoteDocumentResponse;
import gov.fdic.tip.retention.entity.RetentionRecord;
import gov.fdic.tip.retention.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Story 1 – US-1.13-Lean: Promote a Document into Retention.
 * <p>
 * The X-Module-Code header identifies the calling upstream module (set by the API key filter).
 */
@RestController
@RequestMapping("/v1/retention-records")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Retention Records", description = "Story 1 – Promote documents into long-term retention")
public class RetentionController {

    private final RetentionService retentionService;

    /**
     * POST /v1/retention-records
     * AC-1: Provides the promotion API.
     * AC-2: Module authentication handled by security filter (X-API-Key header).
     * AC-3: Bucket validation inside service.
     * AC-4: Retention date computed inside service.
     * AC-7: If this throws, the DB transaction rolls back – no trace left.
     * AC-8: Duplicate calls return the existing record.
     */
    @PostMapping
    @Operation(
        summary     = "Promote a document into retention",
        description = "Registers a document under long-term retention. " +
                      "Idempotent: re-submitting the same upstream reference returns the existing record.",
        responses   = {
            @ApiResponse(responseCode = "201", description = "Document successfully promoted"),
            @ApiResponse(responseCode = "400", description = "Invalid bucket or validation failure"),
            @ApiResponse(responseCode = "401", description = "Caller is not a registered upstream module")
        }
    )
    public ResponseEntity<PromoteDocumentResponse> promote(
            @RequestHeader("X-Module-Code") String moduleCode,
            @Valid @RequestBody PromoteDocumentRequest request) {

        log.info("[API][PROMOTE] module={} ref={}", moduleCode, request.getUpstreamReference());

        PromoteDocumentResponse response;
        try {
            response = retentionService.promote(moduleCode, request);
        } catch (RuntimeException ex) {
            // AC-9: log the failure for operations, then rethrow so GlobalExceptionHandler responds
            retentionService.logFailedPromotion(moduleCode,
                    request.getRetentionBucketCode(),
                    request.getUpstreamReference(),
                    ex.getMessage());
            throw ex;
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/retention-records/{id}
     * Retrieve a single retention record by TIP-generated ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a retention record by ID")
    public ResponseEntity<RetentionRecord> getById(
            @PathVariable UUID id) {
        return retentionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /v1/retention-records?moduleCode=EW&bucketCode=EXAM_FINDINGS_25Y&page=0&size=20
     * Search / list retention records.
     */
    @GetMapping
    @Operation(summary = "Search retention records")
    public Page<RetentionRecord> search(
            @Parameter(description = "Filter by upstream module code") @RequestParam(required = false) String moduleCode,
            @Parameter(description = "Filter by retention bucket code") @RequestParam(required = false) String bucketCode,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return retentionService.search(moduleCode, bucketCode, page, size);
    }
}
