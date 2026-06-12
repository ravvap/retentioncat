package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.response.CategoryResponse;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.service.TaxonomyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Taxonomy", description = "Browse retention Categories and Sub-Categories (read-only)")
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    // ── Categories ──────────────────────────────────────────────────────────

    @GetMapping("/v1/categories")
    @Operation(
        summary  = "List all active retention Categories",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public Page<CategoryResponse> listCategories(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        return taxonomyService.listCategories(page, size);
    }

    @GetMapping("/v1/categories/{id}")
    @Operation(
        summary  = "Get a Category by ID",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return taxonomyService.getCategory(id);
    }

    // ── Sub-Categories ──────────────────────────────────────────────────────

    @GetMapping("/v1/sub-categories")
    @Operation(
        summary     = "List active Sub-Categories",
        description = "Filter by categoryCode to see sub-categories for one category only. " +
                      "Returns only is_active=true AND classification_allowed=true records.",
        security    = @SecurityRequirement(name = "bearerAuth")
    )
    public Page<SubCategoryResponse> listSubCategories(
            @Parameter(description = "Optional category code filter, e.g. EXAM_RECORDS")
            @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return taxonomyService.listSubCategories(categoryCode, page, size);
    }

    @GetMapping("/v1/sub-categories/{id}")
    @Operation(
        summary  = "Get a Sub-Category by ID",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public SubCategoryResponse getSubCategory(@PathVariable UUID id) {
        return taxonomyService.getSubCategory(id);
    }
}
