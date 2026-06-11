package gov.fdic.tip.retention.controller;

import gov.fdic.tip.retention.dto.response.CategoryResponse;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.service.TaxonomyService;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/v1/categories")
    @Operation(summary = "List all active retention Categories")
    public Page<CategoryResponse> listCategories(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return taxonomyService.listCategories(page, size);
    }

    @GetMapping("/v1/categories/{id}")
    @Operation(summary = "Get a Category by ID")
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return taxonomyService.getCategory(id);
    }

    @GetMapping("/v1/sub-categories")
    @Operation(
        summary     = "List active Sub-Categories",
        description = "Use categoryCode to filter to one category. " +
                      "Only returns sub-categories where is_active=true and classification_allowed=true."
    )
    public Page<SubCategoryResponse> listSubCategories(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return taxonomyService.listSubCategories(categoryCode, page, size);
    }

    @GetMapping("/v1/sub-categories/{id}")
    @Operation(summary = "Get a Sub-Category by ID")
    public SubCategoryResponse getSubCategory(@PathVariable UUID id) {
        return taxonomyService.getSubCategory(id);
    }
}
