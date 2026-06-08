package gov.fdic.tip.retention.service.impl;

import gov.fdic.tip.retention.audit.AuditService;
import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.*;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.exception.ConflictException;
import gov.fdic.tip.retention.exception.ResourceNotFoundException;
import gov.fdic.tip.retention.exception.UnprocessableEntityException;
import gov.fdic.tip.retention.repository.RetentionCategoryRepository;
import gov.fdic.tip.retention.service.RetentionCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionCategoryServiceImpl implements RetentionCategoryService {

    private static final String DELETE_CONFIRMATION_CODE = "DELETE CATEGORY";

    private final RetentionCategoryRepository categoryRepo;
    private final AuditService auditService;

    // ── US-1.1: Create Category ────────────────────────────────────────────────

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request, String actorUserId) {
        // AC-2: code globally unique
        if (categoryRepo.existsByCodeIgnoreCase(request.getCode())) {
            throw new ConflictException(
                "A Category with code '" + request.getCode() + "' already exists (HTTP 409)");
        }
        // AC-3: name case-insensitively unique
        if (categoryRepo.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException(
                "A Category with name '" + request.getName() + "' already exists (HTTP 409)");
        }

        RetentionCategory category = RetentionCategory.builder()
            .code(request.getCode().toUpperCase())
            .name(request.getName().trim())
            .description(request.getDescription())
            .status(RetentionStatus.draft)   // AC-4: always starts draft
            .hasEverHeldContent(false)
            .createdBy(actorUserId)
            .updatedBy(actorUserId)
            .build();

        category = categoryRepo.save(category);

        // AC-5: audit event
        auditService.emitCategoryCreated(
            category.getId(), category.getCode(), category.getName(),
            category.getStatus().name(), actorUserId);

        log.info("Category created: id={} code={} by={}", category.getId(), category.getCode(), actorUserId);
        return toResponse(category);
    }

    // ── US-1.2: Activate Category ──────────────────────────────────────────────

    @Override
    @Transactional
    public CategoryResponse activateCategory(UUID id, StatusChangeRequest request, String actorUserId) {
        RetentionCategory category = findOrThrow(id);

        // AC-1: cannot activate if already active or if deleted
        if (RetentionStatus.active == category.getStatus()) {
            throw new ConflictException("Category is already active (HTTP 409)");
        }
        // Deleted categories (conceptually unreachable in R1 hard-delete, but guard anyway)
        if (category.getDeletedAt() != null) {
            throw new ConflictException("Deleted Categories cannot be activated (HTTP 409)");
        }

        String priorStatus = category.getStatus().name();
        category.setStatus(RetentionStatus.active);
        category.setUpdatedBy(actorUserId);
        category = categoryRepo.save(category);

        // AC-4: audit
        auditService.emitCategoryActivated(category.getId(), priorStatus, "active", actorUserId);

        log.info("Category activated: id={} by={}", id, actorUserId);
        return toResponse(category);
    }

    // ── US-1.3: Edit Category ──────────────────────────────────────────────────

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request, String actorUserId) {
        // AC-5: code immutability – reject if code field is present in request body
        if (request.getCode() != null) {
            throw new UnprocessableEntityException(
                "Category code is immutable after creation and cannot be changed (HTTP 422)");
        }

        RetentionCategory category = findOrThrow(id);

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after  = new LinkedHashMap<>();

        if (request.getName() != null) {
            // AC-1: same uniqueness rules as create
            if (categoryRepo.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
                throw new ConflictException(
                    "A Category with name '" + request.getName() + "' already exists (HTTP 409)");
            }
            before.put("name", category.getName());
            after.put("name", request.getName().trim());
            category.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            before.put("description", category.getDescription());
            after.put("description", request.getDescription());
            category.setDescription(request.getDescription());
        }

        category.setUpdatedBy(actorUserId);
        category = categoryRepo.save(category);

        // AC-3: audit with before/after
        if (!after.isEmpty()) {
            auditService.emitCategoryEdited(category.getId(), before, after, actorUserId);
        }

        return toResponse(category);
    }

    // ── US-1.4: Deactivate Category ───────────────────────────────────────────

    @Override
    @Transactional
    public CategoryResponse deactivateCategory(UUID id, StatusChangeRequest request, String actorUserId) {
        RetentionCategory category = findOrThrow(id);

        // AC-1: only Active can be deactivated
        if (!category.canBeDeactivated()) {
            throw new ConflictException(
                "Only Active Categories can be deactivated. Current status: " + category.getStatus() + " (HTTP 409)");
        }

        category.setStatus(RetentionStatus.inactive);
        category.setUpdatedBy(actorUserId);
        category = categoryRepo.save(category);

        String comment = request != null ? request.getComment() : null;
        auditService.emitCategoryDeactivated(category.getId(), actorUserId, comment);

        log.info("Category deactivated: id={} by={}", id, actorUserId);
        return toResponse(category);
    }

    // ── US-1.5: Delete Category ───────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteCategory(UUID id, String confirmationCode, String actorUserId) {
        RetentionCategory category = findOrThrow(id);

        // Must be inactive first
        if (RetentionStatus.inactive != category.getStatus()) {
            throw new ConflictException(
                "Deactivate the Category before deleting. Current status: " + category.getStatus() + " (HTTP 409)");
        }

        // AC-1: zero current Sub-Categories required
        long subCatCount = categoryRepo.countActiveSubCategories(id);
        if (subCatCount > 0) {
            throw new ConflictException(
                subCatCount + " Sub-Categories must be deleted or moved before this Category can be deleted (HTTP 409)");
        }

        // AC-2: if Category has ever held content, X-Confirmation-Code header required
        if (Boolean.TRUE.equals(category.getHasEverHeldContent())) {
            if (!DELETE_CONFIRMATION_CODE.equals(confirmationCode)) {
                throw new ConflictException(
                    "This Category has historically held classified content. " +
                    "Supply header X-Confirmation-Code: DELETE CATEGORY to proceed (HTTP 409)");
            }
        }

        String code = category.getCode();
        String name = category.getName();

        // AC-3: hard delete
        categoryRepo.delete(category);

        // AC-4: audit
        auditService.emitCategoryDeleted(id, code, name, actorUserId);

        log.info("Category hard-deleted: id={} code={} by={}", id, code, actorUserId);
    }

    // ── US-1.12: Hierarchy ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public HierarchyResponse getHierarchy(RetentionStatus statusFilter, String nameSearch) {
        List<RetentionCategory> categories;

        if (nameSearch != null && !nameSearch.isBlank()) {
            categories = categoryRepo.searchByName(nameSearch.trim());
        } else if (statusFilter != null) {
            categories = categoryRepo.findByStatusOrderByNameAsc(statusFilter);
        } else {
            categories = categoryRepo.findAllByOrderByNameAsc();
        }

        List<HierarchyResponse.CategoryNode> nodes = categories.stream()
            .map(this::toCategoryNode)
            .collect(Collectors.toList());

        int totalSubCats = nodes.stream().mapToInt(HierarchyResponse.CategoryNode::getSubCategoryCount).sum();
        long totalDocs = nodes.stream()
            .flatMap(c -> c.getSubCategories().stream())
            .mapToLong(HierarchyResponse.SubCategoryNode::getClassifiedItemCount)
            .sum();

        return HierarchyResponse.builder()
            .categories(nodes)
            .totalCategories(nodes.size())
            .totalSubCategories(totalSubCats)
            .totalClassifiedDocuments(totalDocs)
            .totalClassifiedRecords(0L)  // Records count added in R2
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(RetentionStatus statusFilter) {
        List<RetentionCategory> results = statusFilter != null
            ? categoryRepo.findByStatusOrderByNameAsc(statusFilter)
            : categoryRepo.findAllByOrderByNameAsc();
        return results.stream().map(this::toResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RetentionCategory findOrThrow(UUID id) {
        return categoryRepo.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.category(id));
    }

    private CategoryResponse toResponse(RetentionCategory cat) {
        return CategoryResponse.builder()
            .id(cat.getId())
            .code(cat.getCode())
            .name(cat.getName())
            .description(cat.getDescription())
            .status(cat.getStatus())
            .hasEverHeldContent(cat.getHasEverHeldContent())
            .createdAt(cat.getCreatedAt())
            .createdBy(cat.getCreatedBy())
            .updatedAt(cat.getUpdatedAt())
            .updatedBy(cat.getUpdatedBy())
            .build();
    }

    private HierarchyResponse.CategoryNode toCategoryNode(RetentionCategory cat) {
        List<HierarchyResponse.SubCategoryNode> subNodes = cat.getSubCategories().stream()
            .map(sc -> HierarchyResponse.SubCategoryNode.builder()
                .id(sc.getId())
                .code(sc.getCode())
                .name(sc.getName())
                .description(sc.getDescription())
                .status(sc.getStatus())
                .retentionDurationValue(sc.getRetentionDurationValue())
                .retentionDurationUnit(sc.getRetentionDurationUnit().name())
                .hasEverHeldContent(sc.getHasEverHeldContent())
                .classifiedItemCount(0L)  // Populated by count query in R2 enhancement
                .build())
            .collect(Collectors.toList());

        return HierarchyResponse.CategoryNode.builder()
            .id(cat.getId())
            .code(cat.getCode())
            .name(cat.getName())
            .description(cat.getDescription())
            .status(cat.getStatus())
            .hasEverHeldContent(cat.getHasEverHeldContent())
            .subCategoryCount(subNodes.size())
            .subCategories(subNodes)
            .build();
    }
}
