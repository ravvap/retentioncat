package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.response.CategoryResponse;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.exception.CategoryNotFoundException;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.repository.RetentionCategoryRepository;
import gov.fdic.tip.retention.repository.RetentionSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only taxonomy queries.
 * Categories and Sub-Categories are managed by Records Management
 * and seeded via Flyway; the lean MVP exposes them as read-only.
 */
@Service
@RequiredArgsConstructor
public class TaxonomyService {

    private final RetentionCategoryRepository    categoryRepo;
    private final RetentionSubCategoryRepository subCategoryRepo;

    // ── Categories ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategories(int page, int size) {
        return categoryRepo
                .findAllByActiveTrue(PageRequest.of(page, size, Sort.by("name")))
                .map(this::toCategory);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID id) {
        return categoryRepo.findById(id)
                .filter(c -> c.isActive())
                .map(this::toCategory)
                .orElseThrow(() -> new CategoryNotFoundException(id.toString()));
    }

    // ── Sub-Categories ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SubCategoryResponse> listSubCategories(String categoryCode, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        if (categoryCode != null && !categoryCode.isBlank()) {
            return subCategoryRepo
                    .findByCategoryCodeAndActiveTrue(categoryCode, pageable)
                    .map(this::toSubCategory);
        }
        return subCategoryRepo.findAllByActiveTrue(pageable).map(this::toSubCategory);
    }

    @Transactional(readOnly = true)
    public SubCategoryResponse getSubCategory(UUID id) {
        return subCategoryRepo.findById(id)
                .filter(sc -> sc.isActive())
                .map(this::toSubCategory)
                .orElseThrow(() -> new SubCategoryNotFoundException(id.toString()));
    }

    // ── Mappers ─────────────────────────────────────────────────────────────

    public CategoryResponse toCategory(RetentionCategory c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .createdBy(c.getCreatedBy())
                .build();
    }

    public SubCategoryResponse toSubCategory(RetentionSubCategory sc) {
        return SubCategoryResponse.builder()
                .id(sc.getId())
                .categoryCode(sc.getCategory().getCode())
                .categoryName(sc.getCategory().getName())
                .code(sc.getCode())
                .name(sc.getName())
                .description(sc.getDescription())
                .retentionDurationValue(sc.getRetentionDurationValue())
                .retentionDurationUnit(sc.getRetentionDurationUnit().name())
                .classificationAllowed(sc.isClassificationAllowed())
                .active(sc.isActive())
                .createdAt(sc.getCreatedAt())
                .createdBy(sc.getCreatedBy())
                .build();
    }
}
