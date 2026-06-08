package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.*;
import gov.fdic.tip.retention.enums.RetentionStatus;

import java.util.List;
import java.util.UUID;

/**
 * Retention Category management – US-1.1 through US-1.5, US-1.12.
 * All mutating operations require the TIP-CM-RETENTION-ADMIN role (enforced in controller).
 */
public interface RetentionCategoryService {

    /**
     * US-1.1: Create a new Category in 'draft' status.
     * AC-1: code required, 1–64 chars, regex validated.
     * AC-2: code globally unique → 409 on conflict.
     * AC-3: name case-insensitively unique → 409 on conflict.
     * AC-4: created in 'draft' status.
     * AC-5: audit event retention.category.created emitted.
     * AC-6: Admin role only.
     */
    CategoryResponse createCategory(CreateCategoryRequest request, String actorUserId);

    /**
     * US-1.2: Activate a Category (draft → active, or inactive → active / reactivate).
     * AC-1: draft or inactive only; active → 409; deleted → 409.
     * AC-2: status transitions atomically with audit emit.
     * AC-3: once active, Sub-Categories can be created.
     * AC-4: audit event retention.category.activated with prior_status and new_status.
     * AC-5: Admin only.
     */
    CategoryResponse activateCategory(UUID id, StatusChangeRequest request, String actorUserId);

    /**
     * US-1.3: Edit Category name and/or description.
     * AC-1: editable fields are name and description; same validation as create.
     * AC-2: edit allowed in any status.
     * AC-3: audit event with before/after values.
     * AC-4: Admin only.
     * AC-5: code is NOT editable; requests including 'code' rejected with 422.
     */
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request, String actorUserId);

    /**
     * US-1.4: Deactivate an active Category.
     * AC-1: only Active categories can be deactivated → 409 if already inactive.
     * AC-2: prevents creation of new Sub-Categories; existing Sub-Cats continue to function.
     * AC-3: existing classified items unaffected.
     * AC-4: audit event retention.category.deactivated.
     * AC-5: Admin only.
     */
    CategoryResponse deactivateCategory(UUID id, StatusChangeRequest request, String actorUserId);

    /**
     * US-1.5: Hard-delete a Category.
     * AC-1: current Sub-Categories must be zero → 409 with details.
     * AC-2: if has_ever_held_content=true, X-Confirmation-Code: DELETE CATEGORY header required
     *        (passed as confirmationCode parameter) → 409 without it.
     * AC-3: hard-removal (DELETE row).
     * AC-4: audit event retention.category.deleted.
     * AC-5: Admin only.
     * Note: Category must already be inactive (deactivate via US-1.4 first).
     */
    void deleteCategory(UUID id, String confirmationCode, String actorUserId);

    /** US-1.12: View full Category hierarchy with Sub-Category counts. */
    HierarchyResponse getHierarchy(RetentionStatus statusFilter, String nameSearch);

    /** Get single Category by ID. */
    CategoryResponse getCategoryById(UUID id);

    /** List all Categories, optionally filtered by status. */
    List<CategoryResponse> listCategories(RetentionStatus statusFilter);
}
