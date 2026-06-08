package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.audit.AuditService;
import gov.fdic.tip.retention.dto.request.CreateCategoryRequest;
import gov.fdic.tip.retention.dto.request.StatusChangeRequest;
import gov.fdic.tip.retention.dto.request.UpdateCategoryRequest;
import gov.fdic.tip.retention.dto.response.CategoryResponse;
import gov.fdic.tip.retention.entity.RetentionCategory;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.exception.ConflictException;
import gov.fdic.tip.retention.exception.UnprocessableEntityException;
import gov.fdic.tip.retention.repository.RetentionCategoryRepository;
import gov.fdic.tip.retention.service.impl.RetentionCategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetentionCategoryService")
class RetentionCategoryServiceTest {

    @Mock RetentionCategoryRepository categoryRepo;
    @Mock AuditService auditService;

    @InjectMocks RetentionCategoryServiceImpl service;

    private static final String ACTOR = "test-admin@fdic.gov";

    // ── US-1.1: Create Category ────────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.1 Create Category")
    class CreateCategory {

        @Test
        @DisplayName("AC-4: Created category starts in draft status")
        void createCategory_startsInDraftStatus() {
            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setCode("EXAM_RECORDS");
            req.setName("Examination Records");

            when(categoryRepo.existsByCodeIgnoreCase("EXAM_RECORDS")).thenReturn(false);
            when(categoryRepo.existsByNameIgnoreCase("Examination Records")).thenReturn(false);

            RetentionCategory saved = RetentionCategory.builder()
                .id(UUID.randomUUID()).code("EXAM_RECORDS").name("Examination Records")
                .status(RetentionStatus.draft).hasEverHeldContent(false)
                .createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.save(any())).thenReturn(saved);

            CategoryResponse response = service.createCategory(req, ACTOR);

            assertThat(response.getStatus()).isEqualTo(RetentionStatus.draft);
            verify(auditService).emitCategoryCreated(any(), eq("EXAM_RECORDS"),
                eq("Examination Records"), eq("draft"), eq(ACTOR));
        }

        @Test
        @DisplayName("AC-2: Duplicate code → 409 ConflictException")
        void createCategory_duplicateCode_throws409() {
            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setCode("EXAM_RECORDS");
            req.setName("New Name");
            when(categoryRepo.existsByCodeIgnoreCase("EXAM_RECORDS")).thenReturn(true);

            assertThatThrownBy(() -> service.createCategory(req, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EXAM_RECORDS");
            verify(categoryRepo, never()).save(any());
        }

        @Test
        @DisplayName("AC-3: Duplicate name → 409 ConflictException")
        void createCategory_duplicateName_throws409() {
            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setCode("NEW_CODE");
            req.setName("Examination Records");
            when(categoryRepo.existsByCodeIgnoreCase("NEW_CODE")).thenReturn(false);
            when(categoryRepo.existsByNameIgnoreCase("Examination Records")).thenReturn(true);

            assertThatThrownBy(() -> service.createCategory(req, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Examination Records");
        }
    }

    // ── US-1.2: Activate Category ──────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.2 Activate Category")
    class ActivateCategory {

        @Test
        @DisplayName("AC-1: Draft category transitions to active")
        void activate_draftCategory_becomesActive() {
            UUID id = UUID.randomUUID();
            RetentionCategory draft = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.draft)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(draft));
            when(categoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse r = service.activateCategory(id, null, ACTOR);

            assertThat(r.getStatus()).isEqualTo(RetentionStatus.active);
            verify(auditService).emitCategoryActivated(id, "draft", "active", ACTOR);
        }

        @Test
        @DisplayName("AC-1: Already active → 409 ConflictException")
        void activate_alreadyActive_throws409() {
            UUID id = UUID.randomUUID();
            RetentionCategory active = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.active)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.activateCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already active");
        }

        @Test
        @DisplayName("Inactive category can be reactivated (US-1.2 plain-English)")
        void activate_inactiveCategory_becomesActive() {
            UUID id = UUID.randomUUID();
            RetentionCategory inactive = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.inactive)
                .hasEverHeldContent(true).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(inactive));
            when(categoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse r = service.activateCategory(id, null, ACTOR);

            assertThat(r.getStatus()).isEqualTo(RetentionStatus.active);
            verify(auditService).emitCategoryActivated(id, "inactive", "active", ACTOR);
        }
    }

    // ── US-1.3: Edit Category ──────────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.3 Edit Category")
    class EditCategory {

        @Test
        @DisplayName("AC-5: Code in body → 422 UnprocessableEntityException")
        void editCategory_codeInBody_throws422() {
            UpdateCategoryRequest req = new UpdateCategoryRequest();
            req.setCode("CANNOT_CHANGE");
            req.setName("New Name");

            assertThatThrownBy(() -> service.updateCategory(UUID.randomUUID(), req, ACTOR))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("AC-2: Edit allowed in any status (active category)")
        void editCategory_activeCategory_updatesSuccessfully() {
            UUID id = UUID.randomUUID();
            RetentionCategory active = RetentionCategory.builder()
                .id(id).code("CODE").name("Old Name").status(RetentionStatus.active)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(active));
            when(categoryRepo.existsByNameIgnoreCaseAndIdNot("New Name", id)).thenReturn(false);
            when(categoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategoryRequest req = new UpdateCategoryRequest();
            req.setName("New Name");

            CategoryResponse r = service.updateCategory(id, req, ACTOR);

            assertThat(r.getName()).isEqualTo("New Name");
            verify(auditService).emitCategoryEdited(eq(id), any(), any(), eq(ACTOR));
        }
    }

    // ── US-1.4: Deactivate Category ───────────────────────────────────────────

    @Nested
    @DisplayName("US-1.4 Deactivate Category")
    class DeactivateCategory {

        @Test
        @DisplayName("AC-1: Active → inactive successfully")
        void deactivate_activeCategory_becomesInactive() {
            UUID id = UUID.randomUUID();
            RetentionCategory active = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.active)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(active));
            when(categoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse r = service.deactivateCategory(id, null, ACTOR);

            assertThat(r.getStatus()).isEqualTo(RetentionStatus.inactive);
            verify(auditService).emitCategoryDeactivated(id, ACTOR, null);
        }

        @Test
        @DisplayName("AC-1: Draft category cannot be deactivated → 409")
        void deactivate_draftCategory_throws409() {
            UUID id = UUID.randomUUID();
            RetentionCategory draft = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.draft)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(draft));

            assertThatThrownBy(() -> service.deactivateCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class);
        }
    }

    // ── US-1.5: Delete Category ───────────────────────────────────────────────

    @Nested
    @DisplayName("US-1.5 Delete Category")
    class DeleteCategory {

        @Test
        @DisplayName("AC-1: Has Sub-Categories → 409")
        void delete_withSubCategories_throws409() {
            UUID id = UUID.randomUUID();
            RetentionCategory inactive = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.inactive)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(inactive));
            when(categoryRepo.countActiveSubCategories(id)).thenReturn(3L);

            assertThatThrownBy(() -> service.deleteCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 Sub-Categories");
        }

        @Test
        @DisplayName("AC-2: has_ever_held_content without confirmation header → 409")
        void delete_hasEverHeldContent_missingHeader_throws409() {
            UUID id = UUID.randomUUID();
            RetentionCategory inactive = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.inactive)
                .hasEverHeldContent(true).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(inactive));
            when(categoryRepo.countActiveSubCategories(id)).thenReturn(0L);

            assertThatThrownBy(() -> service.deleteCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("X-Confirmation-Code");
        }

        @Test
        @DisplayName("AC-2: has_ever_held_content WITH correct confirmation header → deletes")
        void delete_hasEverHeldContent_withCorrectHeader_succeeds() {
            UUID id = UUID.randomUUID();
            RetentionCategory inactive = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.inactive)
                .hasEverHeldContent(true).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(inactive));
            when(categoryRepo.countActiveSubCategories(id)).thenReturn(0L);

            assertThatCode(() -> service.deleteCategory(id, "DELETE CATEGORY", ACTOR))
                .doesNotThrowAnyException();
            verify(categoryRepo).delete(inactive);
            verify(auditService).emitCategoryDeleted(id, "CODE", "Name", ACTOR);
        }

        @Test
        @DisplayName("AC-3: Not inactive → 409 before any other check")
        void delete_notInactive_throws409() {
            UUID id = UUID.randomUUID();
            RetentionCategory active = RetentionCategory.builder()
                .id(id).code("CODE").name("Name").status(RetentionStatus.active)
                .hasEverHeldContent(false).createdBy(ACTOR).updatedBy(ACTOR).build();
            when(categoryRepo.findById(id)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.deleteCategory(id, null, ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Deactivate");
        }
    }
}
