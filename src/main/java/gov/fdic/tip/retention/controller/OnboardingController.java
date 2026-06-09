package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.OnboardTableRequest;
import gov.fdic.tip.retention.dto.OnboardTableResponse;
import gov.fdic.tip.retention.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Story 2 – US-1.24-Lean: Classify every new record automatically.
 * <p>
 * Onboarding is a one-time, operations-coordinated event (AC-1).
 * This endpoint is restricted to TIP operations staff (ROLE_OPS in security config).
 */
@RestController
@RequestMapping("/v1/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Table Onboarding", description = "Story 2 – Bring a whole table under automatic retention")
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * POST /v1/onboarding
     * Registers an upstream table and installs the PostgreSQL trigger.
     */
    @PostMapping
    @Operation(
        summary     = "Onboard a table for automatic retention",
        description = "One-time setup that installs a DB trigger on the upstream table. " +
                      "After this call, every new row in that table receives a retention date automatically."
    )
    public ResponseEntity<OnboardTableResponse> onboard(@Valid @RequestBody OnboardTableRequest request) {
        log.info("[API][ONBOARD] module={} table={}.{}",
                request.getModuleCode(), request.getSchemaName(), request.getTableName());
        OnboardTableResponse response = onboardingService.onboard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
