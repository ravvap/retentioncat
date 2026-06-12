package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.config.JwtClaimExtractor;
import gov.fdic.tip.retention.dto.request.ClassifyDocumentRequest;
import gov.fdic.tip.retention.dto.response.ClassifyDocumentResponse;
import gov.fdic.tip.retention.service.ClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Pattern A – US-1.13-Lean: Classify Document at Creation.
 *
 * Module identity is extracted from the Azure AD JWT "appid" claim – never
 * from the request body or a database table.
 *
 * Idempotent: re-submitting the same sourceReference returns the existing record (200).
 * New classification returns 201.
 *
 * Failure audit events (CLASSIFICATION_FAILED) are written in a separate
 * transaction so they survive the rollback of a failed classification.
 */
@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents (Pattern A)",
     description = "Classify a document into TIP retention at creation – US-1.13-Lean")
public class DocumentController {

    private final ClassificationService classificationService;
    private final JwtClaimExtractor     jwtClaims;

    @PostMapping
    @Operation(
        summary   = "Classify a document into TIP retention (Pattern A)",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "Idempotent – re-submitting the same sourceReference returns the existing record. " +
                      "Caller module identity comes from the Azure AD JWT appid claim.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Document classified"),
            @ApiResponse(responseCode = "200", description = "Already classified (idempotent)"),
            @ApiResponse(responseCode = "422", description = "Sub-Category invalid/inactive"),
            @ApiResponse(responseCode = "400", description = "Request validation failure"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Azure AD token")
        }
    )
    public ResponseEntity<ClassifyDocumentResponse> classify(
            @Valid @RequestBody ClassifyDocumentRequest request) {

        String moduleCode = jwtClaims.getModuleCode();
        log.info("[API][CLASSIFY-A] module={} ref={}", moduleCode, request.getSourceReference());

        ClassifyDocumentResponse response;
        try {
            response = classificationService.classifyDocument(moduleCode, request);
        } catch (RuntimeException ex) {
            // Write failure audit in a separate transaction (survives rollback)
            classificationService.logFailure(
                    moduleCode,
                    request.getSubCategoryCode(),
                    request.getSourceReference(),
                    ex.getMessage());
            throw ex;
        }

        // If auditEventId is null the record already existed (idempotent hit)
        HttpStatus status = response.getAuditEventId() != null
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary  = "Get a classified document by TIP UUID",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ClassifyDocumentResponse> getById(@PathVariable UUID id) {
        return classificationService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
        summary     = "Search classified documents",
        security    = @SecurityRequirement(name = "bearerAuth"),
        description = "All filters optional. Results are newest-first, paginated."
    )
    public Page<ClassifyDocumentResponse> search(
            @Parameter(description = "Filter by Azure AD appid (module code)")
            @RequestParam(required = false) String moduleCode,
            @Parameter(description = "Filter by category code, e.g. EXAM_RECORDS")
            @RequestParam(required = false) String categoryCode,
            @Parameter(description = "Filter by sub-category code, e.g. EXAM_FINDINGS_25Y")
            @RequestParam(required = false) String subCategoryCode,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return classificationService.search(moduleCode, categoryCode, subCategoryCode, page, size);
    }
}
