package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.config.JwtClaimExtractor;
import gov.fdic.tip.retention.dto.request.ClassifyDocumentRequest;
import gov.fdic.tip.retention.dto.response.ClassifyDocumentResponse;
import gov.fdic.tip.retention.service.ClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Pattern A – US-1.13-Lean: Classify Document at Creation.
 *
 * Module identity is extracted from the Azure AD JWT "appid" claim –
 * not from a request header or body field.
 *
 * AC-7: @Transactional in ClassificationService ensures atomicity.
 * AC-8: Idempotent – re-submitting the same sourceReference returns the existing record.
 * AC-9: Failure audit events are written in a separate new transaction.
 */
@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents (Pattern A)", description = "Promote a document into TIP retention – US-1.13-Lean")
public class DocumentController {

    private final ClassificationService classificationService;
    private final JwtClaimExtractor     jwtClaims;

    @PostMapping
    @Operation(
        summary   = "Classify a document into TIP retention",
        description = "Idempotent: re-submitting the same sourceReference returns the existing record. " +
                      "Module identity is taken from the Azure AD JWT appid claim.",
        security  = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @ApiResponse(responseCode = "201", description = "Classified"),
            @ApiResponse(responseCode = "422", description = "Sub-Category invalid"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
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
            classificationService.logFailure(
                    moduleCode, request.getSubCategoryCode(),
                    request.getSourceReference(), ex.getMessage());
            throw ex;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a classified document by TIP UUID",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ClassifyDocumentResponse> getById(@PathVariable UUID id) {
        return classificationService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
        summary   = "Search classified documents",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "All filters optional. Results are newest-first, paginated."
    )
    public Page<ClassifyDocumentResponse> search(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String subCategoryCode,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return classificationService.search(moduleCode, categoryCode, subCategoryCode, page, size);
    }
}
