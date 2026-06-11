package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.config.JwtClaimExtractor;
import gov.fdic.tip.retention.dto.request.ClassifyRecordRequest;
import gov.fdic.tip.retention.dto.response.ClassifyRecordResponse;
import gov.fdic.tip.retention.service.RecordClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Pattern B – US-1.24-Lean: Classify Operational Record at INSERT.
 *
 * Called by upstream modules immediately after they INSERT a row into their
 * operational table. The upstream module is responsible for persisting the
 * returned eligibilityDate on its own row.
 *
 * No database trigger; no changes to the upstream table from TIP's side.
 */
@RestController
@RequestMapping("/v1/classify-record")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Records (Pattern B)", description = "Classify an operational record for retention – US-1.24-Lean")
public class RecordController {

    private final RecordClassificationService recordService;
    private final JwtClaimExtractor           jwtClaims;

    @PostMapping
    @Operation(
        summary   = "Classify an operational record for retention",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "Called after each INSERT to the upstream table. " +
                      "Returns the computed eligibilityDate, which the upstream module MUST persist " +
                      "on its own row. TIP does not modify the upstream table.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Record classified; see eligibilityDate in response"),
            @ApiResponse(responseCode = "422", description = "Table not registered or sub-category invalid"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Azure AD token")
        }
    )
    public ResponseEntity<ClassifyRecordResponse> classifyRecord(
            @Valid @RequestBody ClassifyRecordRequest request) {

        String moduleCode = jwtClaims.getModuleCode();
        log.info("[API][CLASSIFY-B] module={} table={}.{} pk={}",
                moduleCode, request.getSchemaName(), request.getTableName(), request.getPkValue());

        ClassifyRecordResponse response = recordService.classifyRecord(moduleCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
