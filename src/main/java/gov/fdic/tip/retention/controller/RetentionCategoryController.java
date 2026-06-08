package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.*;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.service.RetentionCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Retention Category management.
 *
 * US Coverage:
 *   POST   /api/v1/retention/categories            → US-1.1 Create Category
 *   PATCH  /api/v1/retention/categories/{id}/activate  → US-1.2 Activate Category
 *   PATCH  /api/v1/retention/categories/{id}       → US-1.3 Edit Category
 *   PATCH  /api/v1/retention/categories/{id}/deactivate → US-1.4 Deactivate Category
 *   DELETE /api/v1/retention/categories/{id}       → US-1.5 Delete Category
 *   GET    /api/v1/retention/categories            → List Categories
 *   GET    /api/v1/retention/categories/{id}       → Get Category by ID
 *   GET    /api/v1/retention/hierarchy             → US-1.12 View Category Hierarchy
 */
@RestController
@RequestMapping("/api/v1/retention")
@RequiredArgsConstructor
@Tag(name = "Retention Categories", description = "Category lifecycle management (US-1.1 – US-1.5, US-1.12)")
@SecurityRequirement(name = "bearerAuth")
public class RetentionCategoryController {

    private static final String ADMIN_ROLE = "hasRole('TIP-CM-RETENTION-ADMIN')";

    private final RetentionCategoryService categoryService;

    // ── US-1.1: Create Category ────────────────────────────────────────────────

    @PostMapping("/categories")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Create Category (US-1.1)",
               description = "Creates a new top-level Category in 'draft' status. " +
                             "Code is immutable, 1–64 chars, uppercase/digits/underscore, globally unique. " +
                             "Name is case-insensitively unique (1–200 chars). " +
                             "Returns 201 Created with the new Category. 409 on code/name conflict.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Category created in draft status"),
        @ApiResponse(responseCode = "409", description = "Duplicate code or name"),
        @ApiResponse(responseCode = "422", description = "Validation failure"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        CategoryResponse response = categoryService.createCategory(request, principal.getUsername());
        URI location = URI.create("/api/v1/retention/categories/" + response.getId());
        return ResponseEntity.created(location).body(response);
    }

    // ── US-1.2: Activate Category ──────────────────────────────────────────────

    @PatchMapping("/categories/{id}/activate")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Activate Category (US-1.2)",
               description = "Transitions a Category from draft or inactive to active. " +
                             "Active categories cannot be re-activated (409). " +
                             "Audit event retention.category.activated emitted with prior/new status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category activated"),
        @ApiResponse(responseCode = "409", description = "Already active or deleted"),
        @ApiResponse(responseCode = "404", description = "Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<CategoryResponse> activateCategory(
            @PathVariable UUID id,
            @RequestBody(required = false) StatusChangeRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(categoryService.activateCategory(id, request, principal.getUsername()));
    }

    // ── US-1.3: Edit Category ──────────────────────────────────────────────────

    @PatchMapping("/categories/{id}")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Edit Category name/description (US-1.3)",
               description = "Updates name and/or description. Code is immutable – requests including " +
                             "'code' in the body are rejected with 422. Allowed in any status. " +
                             "Audit event with before/after values emitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category updated"),
        @ApiResponse(responseCode = "409", description = "Name conflict"),
        @ApiResponse(responseCode = "422", description = "Attempt to change code (immutable)"),
        @ApiResponse(responseCode = "404", description = "Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(categoryService.updateCategory(id, request, principal.getUsername()));
    }

    // ── US-1.4: Deactivate Category ───────────────────────────────────────────

    @PatchMapping("/categories/{id}/deactivate")
    @PreAuthorize(ADMIN_ROLE)
    @Operation(summary = "Deactivate Category (US-1.4)",
               description = "Retires an active Category. Existing Sub-Categories continue to function. " +
                             "No new Sub-Categories can be created until reactivated. " +
                             "Audit event retention.category.deactivated emitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category deactivated"),
        @ApiResponse(responseCode = "409", description = "Already inactive"),
        @ApiResponse(responseCode = "404", description = "Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<CategoryResponse> deactivateCategory(
            @PathVariable UUID id,
            @RequestBody(required = false) StatusChangeRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(categoryService.deactivateCategory(id, request, principal.getUsername()));
    }

    // ── US-1.5: Delete Category ───────────────────────────────────────────────

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(ADMIN_ROLE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hard-delete Category (US-1.5)",
               description = "Permanently removes an inactive Category with zero Sub-Categories. " +
                             "If the Category has_ever_held_content=true, the header " +
                             "'X-Confirmation-Code: DELETE CATEGORY' is required. " +
                             "Hard-removal – no soft-delete in R1. " +
                             "Audit event retention.category.deleted emitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Category deleted"),
        @ApiResponse(responseCode = "409", description = "Has Sub-Categories, not inactive, or missing confirmation header"),
        @ApiResponse(responseCode = "404", description = "Category not found"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            @Parameter(description = "Required header value 'DELETE CATEGORY' when Category has ever held content")
            @RequestHeader(value = "X-Confirmation-Code", required = false) String confirmationCode,
            @AuthenticationPrincipal UserDetails principal) {

        categoryService.deleteCategory(id, confirmationCode, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── US-1.12: Hierarchy ────────────────────────────────────────────────────

    @GetMapping("/hierarchy")
    @Operation(summary = "View Category Hierarchy (US-1.12)",
               description = "Returns the full Category → Sub-Category tree with retention durations, " +
                             "statuses, and classified-item counts. Available to any authenticated user. " +
                             "Supports filtering by status and case-insensitive name search.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hierarchy returned")
    })
    public ResponseEntity<HierarchyResponse> getHierarchy(
            @RequestParam(required = false) RetentionStatus status,
            @RequestParam(required = false) String search) {

        return ResponseEntity.ok(categoryService.getHierarchy(status, search));
    }

    // ── Read operations ────────────────────────────────────────────────────────

    @GetMapping("/categories")
    @Operation(summary = "List Categories",
               description = "Returns all Categories, optionally filtered by status. Available to all authenticated users.")
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @RequestParam(required = false) RetentionStatus status) {

        return ResponseEntity.ok(categoryService.listCategories(status));
    }

    @GetMapping("/categories/{id}")
    @Operation(summary = "Get Category by ID",
               description = "Returns a single Category. Available to all authenticated users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category found"),
        @ApiResponse(responseCode = "404", description = "Category not found")
    })
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }
}
