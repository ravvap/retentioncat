package gov.fdic.tip.retention.service.impl;

import gov.fdic.tip.retention.audit.AuditService;
import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.exception.ConflictException;
import gov.fdic.tip.retention.exception.ResourceNotFoundException;
import gov.fdic.tip.retention.exception.UnprocessableEntityException;
import gov.fdic.tip.retention.repository.RetentionCategoryRepository;
import gov.fdic.tip.retention.repository.RetentionSubCategoryRepository;
import gov.fdic.tip.retention.service.RetentionSubCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionSubCategoryServiceImpl implements RetentionSubCategoryService {

    private final RetentionSubCategoryRepository subCatRepo;
    private final RetentionCategoryRepository catRepo;
    private final AuditService auditService;

    // ── US-1.6: Create Sub-Category ───────────────────────────────────────────

    @Override
    @Transactional
    public SubCategoryResponse createSubCategory(CreateSubCategoryRequest request, String actorUserId) {
        RetentionCategory parent = findCategoryOrThrow(request.getCategoryId());

        // AC-1: parent Category must be Active
        if (!parent.isActive()) {
            throw new UnprocessableEntityException(
                "Parent Category is not active. Status: " + parent.getStatus() +
                ". Activate the Category before adding Sub-Categories (HTTP 422)");
        }
        // AC-2: code unique within parent
        if (subCatRepo.existsByCategoryIdAndCodeIgnoreCase(request.getCategoryId(), request.getCode())) {
            throw new ConflictException(
                "A Sub-Category with code '" + request.getCode() + "' already exists in this Category (HTTP 409)");
        }
        // AC-3: name unique within parent
        if (subCatRepo.existsByCategoryIdAndNameIgnoreCase(request.getCategoryId(), request.getName())) {
            throw new ConflictException(
                "A Sub-Category with name '" + request.getName() + "' already exists in this Category (HTTP 409)");
        }

        RetentionSubCategory subCat = RetentionSubCategory.builder()
            .category(parent)
            .code(request.getCode().toUpperCase())
            .name(request.getName().trim())
            .description(request.getDescription())
            .retentionDurationValue(request.getRetentionDurationValue())
            .retentionDurationUnit(request.getRetentionDurationUnit())
            .status(RetentionStatus.draft)   // AC-4: starts in draft
            .hasEverHeldContent(false)
            .createdBy(actorUserId)
            .updatedBy(actorUserId)
            .build();

        subCat = subCatRepo.save(subCat);

        // AC-5: audit
        auditService.emitSubCategoryCreated(
            subCat.getId(), parent.getId(), subCat.getCode(), subCat.getName(),
            subCat.getRetentionDurationValue(), subCat.getRetentionDurationUnit().name(),
            actorUserId);

        log.info("SubCategory created: id={} code={} parentId={} by={}",
            subCat.getId(), subCat.getCode(), parent.getId(), actorUserId);
        return toResponse(subCat);
    }

    // ── US-1.7: Activate Sub-Category ─────────────────────────────────────────

    @Override
    @Transactional
    public SubCategoryResponse activateSubCategory(UUID id, StatusChangeRequest request, String actorUserId) {
        RetentionSubCategory subCat = findOrThrow(id);

        // AC-1: draft or inactive only; parent must be active
        if (RetentionStatus.active == subCat.getStatus()) {
            throw new ConflictException("Sub-Category is already active (HTTP 409)");
        }
        if (subCat.getDeletedAt() != null) {
            throw new ConflictException("Deleted Sub-Categories cannot be activated (HTTP 409)");
        }
        if (!subCat.getCategory().isActive()) {
            throw new ConflictException(
                "Parent Category is not active. Activate the parent Category first (HTTP 409)");
        }

        String priorStatus = subCat.getStatus().name();
        subCat.setStatus(RetentionStatus.active);
        subCat.setUpdatedBy(actorUserId);
        subCat = subCatRepo.save(subCat);

        // AC-3: audit
        auditService.emitSubCategoryActivated(subCat.getId(), priorStatus, actorUserId);

        log.info("SubCategory activated: id={} by={}", id, actorUserId);
        return toResponse(subCat);
    }

    // ── US-1.8: Edit Sub-Category ─────────────────────────────────────────────

    @Override
    @Transactional
    public SubCategoryResponse updateSubCategory(UUID id, UpdateSubCategoryRequest request, String actorUserId) {
        // AC-7: code immutability
        if (request.getCode() != null) {
            throw new UnprocessableEntityException(
                "Sub-Category code is immutable after creation and cannot be changed (HTTP 422)");
        }

        // AC-1: reason required when retention changes
        if (request.isRetentionChanging() &&
            (request.getReason() == null || request.getReason().length() < 10)) {
            throw new UnprocessableEntityException(
                "A reason (10–1000 characters) is required when changing retention duration (HTTP 422)");
        }

        RetentionSubCategory subCat = findOrThrow(id);
        UUID categoryId = subCat.getCategory().getId();

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after  = new LinkedHashMap<>();
        boolean retentionChanged = false;

        if (request.getName() != null) {
            if (subCatRepo.existsByCategoryIdAndNameIgnoreCaseAndIdNot(categoryId, request.getName(), id)) {
                throw new ConflictException(
                    "A Sub-Category with name '" + request.getName() + "' already exists in this Category (HTTP 409)");
            }
            before.put("name", subCat.getName());
            after.put("name", request.getName().trim());
            subCat.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            before.put("description", subCat.getDescription());
            after.put("description", request.getDescription());
            subCat.setDescription(request.getDescription());
        }

        if (request.getRetentionDurationValue() != null) {
            before.put("retention_duration_value", subCat.getRetentionDurationValue());
            after.put("retention_duration_value", request.getRetentionDurationValue());
            subCat.setRetentionDurationValue(request.getRetentionDurationValue());
            retentionChanged = true;
        }

        if (request.getRetentionDurationUnit() != null) {
            before.put("retention_duration_unit", subCat.getRetentionDurationUnit().name());
            after.put("retention_duration_unit", request.getRetentionDurationUnit().name());
            subCat.setRetentionDurationUnit(request.getRetentionDurationUnit());
            retentionChanged = true;
        }

        subCat.setUpdatedBy(actorUserId);
        subCat = subCatRepo.save(subCat);

        // AC-6: emit sync audit event
        UUID correlationId = retentionChanged ? UUID.randomUUID() : null;
        if (!after.isEmpty()) {
            auditService.emitSubCategoryEdited(
                subCat.getId(), before, after, request.getReason(), actorUserId, correlationId);
        }

        // AC-2: if retention changed, queue async cascade (placeholder – cascade worker in DIG §9.2)
        if (retentionChanged) {
            log.info("Retention changed for SubCategory id={}; cascade job queued correlationId={} by={}",
                id, correlationId, actorUserId);
            auditService.emitSubCategoryCascadeQueued(subCat.getId(), correlationId, -1, actorUserId);
            // @Async / a queue. The API returns 202 with the cascade_job_id.
        }

        return toResponse(subCat);
    }

    // ── US-1.9: Move Sub-Category ─────────────────────────────────────────────

    @Override
    @Transactional
    public SubCategoryResponse moveSubCategory(UUID id, MoveSubCategoryRequest request, String actorUserId) {
    	
        RetentionSubCategory subCat = findOrThrow(id);
        RetentionCategory targetCategory = findCategoryOrThrow(request.getTargetCategoryId());

        // AC-1: target must be Active
        if (!targetCategory.isActive()) {
            throw new UnprocessableEntityException(
                "Target Category is not active. Status: " + targetCategory.getStatus() + " (HTTP 422)");
        }

        // AC-3: code uniqueness in new parent
        if (subCatRepo.existsByCategoryIdAndCodeIgnoreCase(request.getTargetCategoryId(), subCat.getCode())) {
            throw new ConflictException(
                "Code '" + subCat.getCode() + "' conflicts with an existing Sub-Category in the target Category (HTTP 409)");
        }

        // AC-3: name uniqueness in new parent
        if (subCatRepo.existsByCategoryIdAndNameIgnoreCase(request.getTargetCategoryId(), subCat.getName())) {
            throw new ConflictException(
                "Name '" + subCat.getName() + "' conflicts with an existing Sub-Category in the target Category (HTTP 409)");
        }

        UUID priorCategoryId = subCat.getCategory().getId();
        subCat.setCategory(targetCategory);
        subCat.setUpdatedBy(actorUserId);
        subCat = subCatRepo.save(subCat);

        // AC-4: audit
        auditService.emitSubCategoryMoved(subCat.getId(), priorCategoryId,
            targetCategory.getId(), actorUserId);

        log.info("SubCategory moved: id={} from={} to={} by={}", id, priorCategoryId,
            targetCategory.getId(), actorUserId);
        return toResponse(subCat);
    }

    // ── US-1.10: Deactivate Sub-Category ──────────────────────────────────────

    @Override
    @Transactional
    public SubCategoryResponse deactivateSubCategory(UUID id, StatusChangeRequest request, String actorUserId) {
        RetentionSubCategory subCat = findOrThrow(id);

        // AC-1: only Active can be deactivated
        if (!subCat.canBeDeactivated()) {
            throw new ConflictException(
                "Only Active Sub-Categories can be deactivated. Current status: " + subCat.getStatus() + " (HTTP 409)");
        }

        subCat.setStatus(RetentionStatus.inactive);
        subCat.setUpdatedBy(actorUserId);
        subCat = subCatRepo.save(subCat);

        String comment = request != null ? request.getComment() : null;
        auditService.emitSubCategoryDeactivated(subCat.getId(), actorUserId, comment);

        log.info("SubCategory deactivated: id={} by={}", id, actorUserId);
        return toResponse(subCat);
    }

    // ── US-1.11: Delete Sub-Category ──────────────────────────────────────────

    @Override
    @Transactional
    public void deleteSubCategory(UUID id, String actorUserId) {
        RetentionSubCategory subCat = findOrThrow(id);

        // Must be inactive first
        if (RetentionStatus.inactive != subCat.getStatus()) {
            throw new ConflictException(
                "Deactivate the Sub-Category before deleting. Current status: " + subCat.getStatus() + " (HTTP 409)");
        }

        // AC-1: HARD BLOCK – has_ever_held_content, no escape whatsoever
        if (Boolean.TRUE.equals(subCat.getHasEverHeldContent())) {
            throw new ConflictException(
                "This Sub-Category has historically held classified content and cannot be deleted (HTTP 409). " +
                "Unlike Category delete, there is no confirmation-header escape for Sub-Categories.");
        }

        String code = subCat.getCode();
        String name = subCat.getName();

        // AC-3: hard-remove
        subCatRepo.delete(subCat);

        // AC-4: audit
        auditService.emitSubCategoryDeleted(id, code, name, actorUserId);

        log.info("SubCategory hard-deleted: id={} code={} by={}", id, code, actorUserId);
    }

    // ── Read operations ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SubCategoryResponse getSubCategoryById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubCategoryResponse> listSubCategories(UUID categoryId, RetentionStatus statusFilter) {
        List<RetentionSubCategory> results = statusFilter != null
            ? subCatRepo.findByCategoryIdAndStatusOrderByNameAsc(categoryId, statusFilter)
            : subCatRepo.findByCategoryIdOrderByNameAsc(categoryId);
        return results.stream().map(this::toResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RetentionSubCategory findOrThrow(UUID id) {
        return subCatRepo.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.subCategory(id));
    }

    private RetentionCategory findCategoryOrThrow(UUID id) {
        return catRepo.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.category(id));
    }

    private SubCategoryResponse toResponse(RetentionSubCategory sc) {
        return SubCategoryResponse.builder()
            .id(sc.getId())
            .categoryId(sc.getCategory().getId())
            .categoryName(sc.getCategory().getName())
            .code(sc.getCode())
            .name(sc.getName())
            .description(sc.getDescription())
            .retentionDurationValue(sc.getRetentionDurationValue())
            .retentionDurationUnit(sc.getRetentionDurationUnit())
            .status(sc.getStatus())
            .hasEverHeldContent(sc.getHasEverHeldContent())
            .classifiedItemCount(subCatRepo.countClassifiedDocuments(sc.getId()))
            .createdAt(sc.getCreatedAt())
            .createdBy(sc.getCreatedBy())
            .updatedAt(sc.getUpdatedAt())
            .updatedBy(sc.getUpdatedBy())
            .build();
    }
}
