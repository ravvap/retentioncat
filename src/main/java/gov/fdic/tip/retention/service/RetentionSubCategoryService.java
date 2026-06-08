package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.enums.RetentionStatus;

import java.util.List;
import java.util.UUID;

/**
 * Retention Sub-Category management – US-1.6 through US-1.11.
 */
public interface RetentionSubCategoryService {

    /**
     * US-1.6: Create a Sub-Category under an Active parent Category.
     * AC-1: required fields: categoryId (Active), code, name, retentionDurationValue, retentionDurationUnit.
     * AC-2: code unique within parent → 409.
     * AC-3: name case-insensitively unique within parent → 409.
     * AC-4: created in 'draft' status.
     * AC-5: audit retention.subcategory.created.
     * AC-6: Admin only.
     */
    SubCategoryResponse createSubCategory(CreateSubCategoryRequest request, String actorUserId);

    /**
     * US-1.7: Activate a Sub-Category (draft/inactive → active).
     * Parent Category must be Active.
     * AC-1: draft or inactive only; parent must be active.
     * AC-2: status → active.
     * AC-3: audit retention.subcategory.activated.
     * AC-4: Admin only.
     */
    SubCategoryResponse activateSubCategory(UUID id, StatusChangeRequest request, String actorUserId);

    /**
     * US-1.8: Edit Sub-Category name, description, and/or retention duration.
     * AC-1: editable fields: name, description, retentionDurationValue, retentionDurationUnit.
     *        Reason required (10–1000 chars) when retention duration changes.
     * AC-2: retention changes trigger async cascade re-evaluation; returns 202 with cascade_job_id.
     * AC-3: cascade recomputes eligibility_date per item via tip_compute_eligibility_date.
     * AC-4: no approval workflow; R1 has no Records Officer notification.
     * AC-5: poll GET /api/v1/retention/cascade-jobs/{jobId} for progress.
     * AC-6: audit retention.subcategory.edited synchronously; per-item events async.
     * AC-7: code immutable → 422 if code in body.
     * AC-8: Admin only.
     */
    SubCategoryResponse updateSubCategory(UUID id, UpdateSubCategoryRequest request, String actorUserId);

    /**
     * US-1.9: Move Sub-Category to a different parent Category.
     * AC-1: target Category must be Active.
     * AC-2: classified items remain classified; no re-stamping.
     * AC-3: code and name uniqueness re-evaluated in new parent → 409 on conflict.
     * AC-4: audit retention.subcategory.moved with prior and new parent IDs.
     * AC-5: Admin only.
     */
    SubCategoryResponse moveSubCategory(UUID id, MoveSubCategoryRequest request, String actorUserId);

    /**
     * US-1.10: Deactivate a Sub-Category.
     * AC-1: only Active Sub-Categories can be deactivated → 409 if already inactive.
     * AC-2: new classifications rejected after deactivation (trigger enforces).
     * AC-3: existing items continue to obey their retention.
     * AC-4: Sub-Category appears with '(inactive)' suffix in classification UIs.
     * AC-5: audit retention.subcategory.deactivated.
     * AC-6: Admin only.
     */
    SubCategoryResponse deactivateSubCategory(UUID id, StatusChangeRequest request, String actorUserId);

    /**
     * US-1.11: Hard-delete a Sub-Category.
     * AC-1: has_ever_held_content=true → 409, no escape (hard block).
     * AC-2: only Sub-Categories that have never held content can be deleted.
     * AC-3: hard-removal.
     * AC-4: audit retention.subcategory.deleted.
     * AC-5: Admin only.
     * Note: Sub-Category must be inactive first.
     */
    void deleteSubCategory(UUID id, String actorUserId);

    /** Get single Sub-Category by ID. */
    SubCategoryResponse getSubCategoryById(UUID id);

    /** List Sub-Categories under a Category. */
    List<SubCategoryResponse> listSubCategories(UUID categoryId, RetentionStatus statusFilter);
}
