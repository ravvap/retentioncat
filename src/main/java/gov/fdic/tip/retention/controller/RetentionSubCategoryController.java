package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.service.RetentionSubCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Retention Sub-Category management.
 *
 * US Coverage:
 *   POST   /api/v1/retention/sub-categories                   → US-1.6  Create Sub-Category
 *   PATCH  /api/v1/retention/sub-categories/{id}/activate     → US-1.7  Activate Sub-Category
 *   PATCH  /api/v1/retention/sub-categories/{id}              → US-1.8  Edit Sub-Category
 *   PATCH  /api/v1/retention/sub-categories/{id}/move         → US-1.9  Move Sub-Category
 *   PATCH  /api/v1/retention/sub-categories/{id}/deactivate   → US-1.10 Deactivate Sub-Category
 *   DELETE /api/v1/retention/sub-categories/{id}              → US-1.11 Delete Sub-Category
 *   GET    /api/v1/retention/sub-categories/{id}              → Get by ID
 *   GET    /api/v1/retention/categories/{categoryId}/sub-categories → List by Category
 */
@RestController
@RequestMapping("/api/v1/retention")
@RequiredArgsConstructor
@Tag(name = "Retention Sub-Categories", description = "Sub-Category lifecycle management (US-1.6 – US-1.11)")
@SecurityRequirement(name = "bearerAuth")
public class RetentionSubCategoryController {

    private static final String ADMIN_ROLE = "hasRole('TIP-CM-RETENTION-ADMIN')";

    private final RetentionSubCategoryService subCategoryService;

    // ── US-1.6: Create Sub-Category ───────────────────────────────────────────

    @PostMapping("/sub-categories")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Create Sub-Category (US-1.6)",
               description = "Creates a Sub-Category under an Active parent Category. " +
                             "Retention duration (value + unit) must be specified explicitly – no inheritance from Category. " +
                             "Created in 'draft' status; activate separately via US-1.7. " +
                             "Code is immutable after creation.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Sub-Category created in draft status"),
        @ApiResponse(responseCode = "409", description = "Duplicate code or name within parent"),
        @ApiResponse(responseCode = "422", description = "Parent Category not active or validation failure"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<SubCategoryResponse> createSubCategory(
            @Valid @RequestBody CreateSubCategoryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        SubCategoryResponse response = subCategoryService.createSubCategory(request, principal.getUsername());
        URI location = URI.create("/api/v1/retention/sub-categories/" + response.getId());
        return ResponseEntity.created(location).body(response);
    }

    // ── US-1.7: Activate Sub-Category ─────────────────────────────────────────

    @PatchMapping("/sub-categories/{id}/activate")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Activate Sub-Category (US-1.7)",
               description = "Transitions a Sub-Category from draft or inactive to active. " +
                             "Parent Category must be Active. " +
                             "Once active, content can be classified to this Sub-Category.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sub-Category activated"),
        @ApiResponse(responseCode = "409", description = "Already active, parent Category inactive, or deleted"),
        @ApiResponse(responseCode = "404", description = "Sub-Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<SubCategoryResponse> activateSubCategory(
            @PathVariable UUID id,
            @RequestBody(required = false) StatusChangeRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(subCategoryService.activateSubCategory(id, request, principal.getUsername()));
    }

    // ── US-1.8: Edit Sub-Category ─────────────────────────────────────────────

    @PatchMapping("/sub-categories/{id}")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Edit Sub-Category (US-1.8)",
               description = "Updates name, description, and/or retention duration. " +
                             "Code is immutable – 422 if supplied. " +
                             "Retention duration changes require a 'reason' field (10–1000 chars) and " +
                             "trigger an asynchronous cascade re-evaluation of all classified items. " +
                             "Returns 200 (name/description only) or 202 (retention changed) with cascade_job_id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sub-Category updated (no retention change)"),
        @ApiResponse(responseCode = "202", description = "Sub-Category updated; cascade job queued (retention changed)"),
        @ApiResponse(responseCode = "409", description = "Name conflict within parent"),
        @ApiResponse(responseCode = "422", description = "Code in body, missing/short reason, or validation failure"),
        @ApiResponse(responseCode = "404", description = "Sub-Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<SubCategoryResponse> updateSubCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubCategoryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        SubCategoryResponse response = subCategoryService.updateSubCategory(id, request, principal.getUsername());
        // Return 202 if retention changed (cascade was queued)
        if (request.isRetentionChanging()) {
            return ResponseEntity.accepted().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // ── US-1.9: Move Sub-Category ─────────────────────────────────────────────

    @PatchMapping("/sub-categories/{id}/move")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Move Sub-Category to different parent (US-1.9)",
               description = "Moves a Sub-Category from its current parent to a new Active Category. " +
                             "Classified items remain classified; no re-stamping occurs unless retention also changes. " +
                             "Code and name uniqueness re-evaluated in new parent. " +
                             "Audit event retention.subcategory.moved emitted with prior and new parent IDs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sub-Category moved"),
        @ApiResponse(responseCode = "409", description = "Code or name conflict in target Category"),
        @ApiResponse(responseCode = "422", description = "Target Category not active"),
        @ApiResponse(responseCode = "404", description = "Sub-Category or target Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<SubCategoryResponse> moveSubCategory(
            @PathVariable UUID id,
            @Valid @RequestBody MoveSubCategoryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(subCategoryService.moveSubCategory(id, request, principal.getUsername()));
    }

    // ── US-1.10: Deactivate Sub-Category ──────────────────────────────────────

    @PatchMapping("/sub-categories/{id}/deactivate")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Deactivate Sub-Category (US-1.10)",
               description = "Closes a Sub-Category to new classifications. " +
                             "Existing classified items continue to obey their retention. " +
                             "New INSERTs targeting this Sub-Category are rejected by the DB trigger (SQLSTATE 23514). " +
                             "Audit event retention.subcategory.deactivated emitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sub-Category deactivated"),
        @ApiResponse(responseCode = "409", description = "Already inactive"),
        @ApiResponse(responseCode = "404", description = "Sub-Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<SubCategoryResponse> deactivateSubCategory(
            @PathVariable UUID id,
            @RequestBody(required = false) StatusChangeRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(subCategoryService.deactivateSubCategory(id, request, principal.getUsername()));
    }

    // ── US-1.11: Delete Sub-Category ──────────────────────────────────────────

    @DeleteMapping("/sub-categories/{id}")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Hard-delete Sub-Category (US-1.11)",
               description = "Permanently removes an inactive Sub-Category that has NEVER held content. " +
                             "has_ever_held_content=true → 409 with NO override (unlike Category which has a header escape). " +
                             "Hard-removal; no soft-delete in R1. " +
                             "Audit event retention.subcategory.deleted emitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sub-Category deleted"),
        @ApiResponse(responseCode = "409", description = "Has ever held content (hard block) or not inactive"),
        @ApiResponse(responseCode = "404", description = "Sub-Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<Void> deleteSubCategory(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        subCategoryService.deleteSubCategory(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── Read operations ────────────────────────────────────────────────────────

    @GetMapping("/sub-categories/{id}")
    @Operation(summary = "Get Sub-Category by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sub-Category found"),
        @ApiResponse(responseCode = "404", description = "Sub-Category not found")
    })
    public ResponseEntity<SubCategoryResponse> getSubCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(subCategoryService.getSubCategoryById(id));
    }

    @GetMapping("/categories/{categoryId}/sub-categories")
    @Operation(summary = "List Sub-Categories by Category",
               description = "Returns all Sub-Categories under the specified Category. Filterable by status.")
    public ResponseEntity<List<SubCategoryResponse>> listSubCategories(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) RetentionStatus status) {

        return ResponseEntity.ok(subCategoryService.listSubCategories(categoryId, status));
    }
}
