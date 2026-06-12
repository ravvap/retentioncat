package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.config.JwtClaimExtractor;
import gov.fdic.tip.retention.dto.request.RegisterTableRequest;
import gov.fdic.tip.retention.dto.response.RegisterTableResponse;
import gov.fdic.tip.retention.service.TableRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Pattern B – one-time table registration (OPS role only).
 *
 * After this call, TIP's DB trigger fires automatically on every INSERT
 * into the registered upstream table – the upstream module does not need
 * to make any API call per row.
 */
@RestController
@RequestMapping("/v1/table-registrations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Table Registrations",
     description = "Register upstream tables for Pattern B automatic retention – OPS only")
public class TableRegistrationController {

    private final TableRegistrationService registrationService;
    private final JwtClaimExtractor        jwtClaims;

    @PostMapping
    @Operation(
        summary   = "Register an upstream table for Pattern B automatic classification",
        security  = @SecurityRequirement(name = "bearerAuth"),
        description = "One-time OPS call per upstream table.\n\n" +
                      "After registration, TIP installs a BEFORE + AFTER INSERT trigger on the " +
                      "upstream table. Every subsequent INSERT will automatically receive " +
                      "eligibility_date stamped by the trigger – no per-row API call needed.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Table registered and triggers installed"),
            @ApiResponse(responseCode = "409", description = "Table already registered"),
            @ApiResponse(responseCode = "422", description = "Default sub-category invalid"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Azure AD token")
        }
    )
    public ResponseEntity<RegisterTableResponse> register(
            @Valid @RequestBody RegisterTableRequest request) {

        String moduleCode = jwtClaims.getModuleCode();
        log.info("[API][REGISTER-B] module={} table={}.{}",
                moduleCode, request.getSchemaName(), request.getTableName());

        RegisterTableResponse response = registrationService.register(moduleCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
