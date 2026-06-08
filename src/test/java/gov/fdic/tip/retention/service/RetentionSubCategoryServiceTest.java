package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.audit.AuditService;
import gov.fdic.tip.retention.dto.request.*;
import gov.fdic.tip.retention.dto.response.SubCategoryResponse;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.enums.RetentionDurationUnit;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.exception.ConflictException;
import gov.fdic.tip.retention.exception.UnprocessableEntityException;
import gov.fdic.tip.retention.repository.RetentionCategoryRepository;
import gov.fdic.tip.retention.repository.RetentionSubCategoryRepository;
import gov.fdic.tip.retention.service.impl.RetentionSubCategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetentionSubCategoryService")
class RetentionSubCategoryServiceTest {

    @Mock RetentionSubCategoryRepository subCatRepo;
    @Mock RetentionCategoryRepository catRepo;
    @Mock AuditService auditService;

    @InjectMocks RetentionSubCategoryServiceImpl service;

    private static final String ACTOR = "test-admin@fdic.gov";

    private RetentionCategory activeCategory(UUID id) {
        return RetentionCategory.builder()
            .id(id).code("CAT_CODE").name("Parent Cat").status(RetentionStatus.active)
            .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
    }

    private RetentionSubCategory draftSubCat(UUID id, RetentionCategory parent) {
        return RetentionSubCategory.builder()
            .id(id).category(parent).code("SC_CODE").name("SubCat Name")
            .retentionDurationValue(7).retentionDurationUnit(RetentionDurationUnit.years)
            .status(RetentionStatus.draft).hasEverHeldContent(false)
            .createdBy(ACTOR).updatedBy(ACTOR).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
            .build();
    }

    // ── US-1.6: Create Sub-Category ───────────────────────────────────────────

    @Nested
    @DisplayName("US-1.6 Create Sub-Category")
    class CreateSubCategory {

        @Test
        @DisplayName("AC-4: Sub-Category starts in draft status")
        void create_startsInDraft() {
            UUID catId = UUID.randomUUID();
            RetentionCategory parent = activeCategory(catId);

            CreateSubCategoryRequest req = new CreateSubCategoryRequest();
            req.setCategoryId(catId);
            req.setCode("INTERNAL_FINDINGS");
            req.setName("Internal Findings");
            req.setRetentionDurationValue(7);
            req.setRetentionDurationUnit(RetentionDurationUnit.years);

            when(catRepo.findById(catId)).thenReturn(Optional.of(parent));
            when(subCatRepo.existsByCategoryIdAndCodeIgnoreCase(catId, "INTERNAL_FINDINGS")).thenReturn(false);
            when(subCatRepo.existsByCategoryIdAndNameIgnoreCase(catId, "Internal Findings")).thenReturn(false);

            RetentionSubCategory saved = draftSubCat(UUID.randomUUID(), parent);
            saved.setCode("INTERNAL_FINDINGS");
            saved.setName("Internal Findings");
            when(subCatRepo.save(any())).thenReturn(saved);
            when(subCatRepo.countClassifiedDocuments(any())).thenReturn(0L);

            SubCategoryResponse r = service.createSubCategory(req, ACTOR);

            assertThat(r.getStatus()).isEqualTo(RetentionStatus.draft);
            verify(auditService).emitSubCategoryCreated(any(), eq(catId), any(), any(), eq(7), eq("years"), eq(ACTOR));
        }

        @Test
        @DisplayName("AC-1: Inactive parent category → 422")
        void create_inactiveParent_throws422() {
            UUID catId = UUID.randomUUID();
            RetentionCategory inactive = activeCategory(catId);
            inactive.setStatus(RetentionStatus.inactive);

            CreateSubCategoryRequest req = new CreateSubCategoryRequest();
            req.setCategoryId(catId);
            req.setCode("CODE");
            req.setName("Name");
            req.setRetentionDurationValue(5);
            req.setRetentionDurationUnit(RetentionDurationUnit.years);

            when(catRepo.findById(catId)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> service.createSubCategory(req, ACTOR))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("AC-2: Duplicate code within parent → 409")
        void create_duplicateCode_throws409() {
            UUID catId = UUID.randomUUID();
            when(catRepo.findById(catId)).thenReturn(Optional.of(activeCategory(catId)));
            when(subCatRepo.existsByCategoryIdAndCodeIgnoreCase(catId, "CODE")).thenReturn(true);

            CreateSubCategoryRequest req = new CreateSubCategoryRequest();
            req.setCategoryId(catId);
            req.setCode("CODE");
            req.setName("New Name");
            req.setRetentionDurationValue(5);
            req.setRetentionDurationUnit(RetentionDurationUnit.years);

            assertThatThrownBy(() -> service.createSubCategory(req, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CODE");
        }
    }

    // ── US-1.7: Activate Sub-Category ─────────────────────────────────────────

    @Nested
    @DisplayName("US-1.7 Activate Sub-Category")
    class ActivateSubCategory {

        @Test
        @DisplayName("Draft sub-category under active parent → active")
        void activate_draft_becomesActive() {
            UUID id = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            RetentionCategory parent = activeCategory(catId);
            RetentionSubCategory draft = draftSubCat(id, parent);

            when(subCatRepo.findById(id)).thenReturn(Optional.of(draft));
            when(subCatRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subCatRepo.countClassifiedDocuments(id)).thenReturn(0L);

            SubCategoryResponse r = service.activateSubCategory(id, null, ACTOR);

            assertThat(r.getStatus()).isEqualTo(RetentionStatus.active);
            verify(auditService).emitSubCategoryActivated(id, "draft", ACTOR);
        }

        @Test
        @DisplayName("Parent category inactive → 409")
        void activate_inactiveParent_throws409() {
            UUID id = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            RetentionCategory inactiveCat = activeCategory(catId);
            inactiveCat.setStatus(RetentionStatus.inactive);
            RetentionSubCategory draft = draftSubCat(id, inactiveCat);

            when(subCatRepo.findById(id)).thenReturn(Optional.of(draft));

            assertThatThrownBy(() -> service.activateSubCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Parent Category is not active");
        }
    }

    // ── US-1.8: Edit Sub-Category ─────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.8 Edit Sub-Category")
    class EditSubCategory {

        @Test
        @DisplayName("AC-7: Code in body → 422")
        void edit_codeInBody_throws422() {
            UpdateSubCategoryRequest req = new UpdateSubCategoryRequest();
            req.setCode("IMMUTABLE");

            assertThatThrownBy(() -> service.updateSubCategory(UUID.randomUUID(), req, ACTOR))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("AC-1: Retention change without reason → 422")
        void edit_retentionChangeWithoutReason_throws422() {
            UpdateSubCategoryRequest req = new UpdateSubCategoryRequest();
            req.setRetentionDurationValue(10);
            // reason not set

            assertThatThrownBy(() -> service.updateSubCategory(UUID.randomUUID(), req, ACTOR))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("reason");
        }

        @Test
        @DisplayName("AC-1: Retention change with valid reason → proceeds")
        void edit_retentionChangeWithReason_queuesCascade() {
            UUID id = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            RetentionCategory parent = activeCategory(catId);
            RetentionSubCategory sc = draftSubCat(id, parent);
            sc.setStatus(RetentionStatus.active);

            when(subCatRepo.findById(id)).thenReturn(Optional.of(sc));
            when(subCatRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subCatRepo.countClassifiedDocuments(id)).thenReturn(0L);

            UpdateSubCategoryRequest req = new UpdateSubCategoryRequest();
            req.setRetentionDurationValue(10);
            req.setRetentionDurationUnit(RetentionDurationUnit.years);
            req.setReason("FDIC schedule update FY26-Q2 – extended from 7y to 10y per regulatory memo.");

            assertThatCode(() -> service.updateSubCategory(id, req, ACTOR))
                .doesNotThrowAnyException();
            verify(auditService).emitSubCategoryCascadeQueued(eq(id), any(), eq(-1), eq(ACTOR));
        }
    }

    // ── US-1.9: Move Sub-Category ─────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.9 Move Sub-Category")
    class MoveSubCategory {

        @Test
        @DisplayName("AC-1: Target Category inactive → 422")
        void move_inactiveTarget_throws422() {
            UUID id = UUID.randomUUID();
            UUID targetCatId = UUID.randomUUID();
            RetentionCategory inactiveTarget = activeCategory(targetCatId);
            inactiveTarget.setStatus(RetentionStatus.inactive);

            RetentionSubCategory sc = draftSubCat(id, activeCategory(UUID.randomUUID()));
            when(subCatRepo.findById(id)).thenReturn(Optional.of(sc));
            when(catRepo.findById(targetCatId)).thenReturn(Optional.of(inactiveTarget));

            MoveSubCategoryRequest req = new MoveSubCategoryRequest();
            req.setTargetCategoryId(targetCatId);

            assertThatThrownBy(() -> service.moveSubCategory(id, req, ACTOR))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("AC-3: Code conflict in target → 409")
        void move_codeConflictInTarget_throws409() {
            UUID id = UUID.randomUUID();
            UUID targetCatId = UUID.randomUUID();
            RetentionCategory target = activeCategory(targetCatId);

            RetentionSubCategory sc = draftSubCat(id, activeCategory(UUID.randomUUID()));
            sc.setCode("SC_CODE");

            when(subCatRepo.findById(id)).thenReturn(Optional.of(sc));
            when(catRepo.findById(targetCatId)).thenReturn(Optional.of(target));
            when(subCatRepo.existsByCategoryIdAndCodeIgnoreCase(targetCatId, "SC_CODE")).thenReturn(true);

            MoveSubCategoryRequest req = new MoveSubCategoryRequest();
            req.setTargetCategoryId(targetCatId);

            assertThatThrownBy(() -> service.moveSubCategory(id, req, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SC_CODE");
        }
    }

    // ── US-1.11: Delete Sub-Category ──────────────────────────────────────────

    @Nested
    @DisplayName("US-1.11 Delete Sub-Category")
    class DeleteSubCategory {

        @Test
        @DisplayName("AC-1: has_ever_held_content=true → hard 409 with NO escape")
        void delete_hasEverHeldContent_throws409_noEscape() {
            UUID id = UUID.randomUUID();
            RetentionSubCategory sc = draftSubCat(id, activeCategory(UUID.randomUUID()));
            sc.setStatus(RetentionStatus.inactive);
            sc.setHasEverHeldContent(true);

            when(subCatRepo.findById(id)).thenReturn(Optional.of(sc));

            assertThatThrownBy(() -> service.deleteSubCategory(id, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no confirmation-header escape");
            verify(subCatRepo, never()).delete(any());
        }

        @Test
        @DisplayName("AC-2: Never held content, inactive → deletes successfully")
        void delete_neverHeldContent_inactive_succeeds() {
            UUID id = UUID.randomUUID();
            RetentionSubCategory sc = draftSubCat(id, activeCategory(UUID.randomUUID()));
            sc.setStatus(RetentionStatus.inactive);
            sc.setHasEverHeldContent(false);

            when(subCatRepo.findById(id)).thenReturn(Optional.of(sc));

            assertThatCode(() -> service.deleteSubCategory(id, ACTOR))
                .doesNotThrowAnyException();
            verify(subCatRepo).delete(sc);
            verify(auditService).emitSubCategoryDeleted(eq(id), any(), any(), eq(ACTOR));
        }
    }

    // ── eligibilityDate computation ────────────────────────────────────────────

    @Nested
    @DisplayName("Entity: eligibility date computation (ADR-RET-001)")
    class EligibilityDateComputation {

        @Test
        @DisplayName("years unit computes correctly")
        void computeEligibility_years() {
            RetentionSubCategory sc = RetentionSubCategory.builder()
                .retentionDurationValue(7).retentionDurationUnit(RetentionDurationUnit.years)
                .createdBy("sys").updatedBy("sys").build();

            var basis = java.time.LocalDate.of(2019, 4, 12);
            var expected = java.time.LocalDate.of(2026, 4, 12);
            assertThat(sc.computeEligibilityDate(basis)).isEqualTo(expected);
        }

        @Test
        @DisplayName("months unit computes correctly")
        void computeEligibility_months() {
            RetentionSubCategory sc = RetentionSubCategory.builder()
                .retentionDurationValue(18).retentionDurationUnit(RetentionDurationUnit.months)
                .createdBy("sys").updatedBy("sys").build();

            var basis = java.time.LocalDate.of(2024, 1, 1);
            var expected = java.time.LocalDate.of(2025, 7, 1);
            assertThat(sc.computeEligibilityDate(basis)).isEqualTo(expected);
        }

        @Test
        @DisplayName("null basis date returns null (NULL short-circuit)")
        void computeEligibility_nullBasis_returnsNull() {
            RetentionSubCategory sc = RetentionSubCategory.builder()
                .retentionDurationValue(5).retentionDurationUnit(RetentionDurationUnit.years)
                .createdBy("sys").updatedBy("sys").build();

            assertThat(sc.computeEligibilityDate(null)).isNull();
        }
    }
}
