package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.config.JwtClaimExtractor;
import gov.fdic.tip.retention.dto.request.RegisterTableRequest;
import gov.fdic.tip.retention.dto.response.RegisterTableResponse;
import gov.fdic.tip.retention.service.TableRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Pattern B one-time table registration. OPS role only.
 */
@RestController
@RequestMapping("/v1/table-registrations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Table Registrations", description = "Register upstream tables for Pattern B – OPS only")
public class TableRegistrationController {

    private final TableRegistrationService registrationService;
    private final JwtClaimExtractor        jwtClaims;

    @PostMapping
    @Operation(
        summary  = "Register a table for Pattern B automatic classification",
        security = @SecurityRequirement(name = "bearerAuth"),
        description = "One-time setup per upstream table. After registration, " +
                      "the upstream module calls POST /v1/classify-record for each new row."
    )
    public ResponseEntity<RegisterTableResponse> register(
            @Valid @RequestBody RegisterTableRequest request) {

        String moduleCode = jwtClaims.getModuleCode();
        log.info("[API][REGISTER-B] module={} table={}.{}",
                moduleCode, request.getSchemaName(), request.getTableName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(moduleCode, request));
    }
}
