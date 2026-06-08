package gov.fdic.tip.retention.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.fdic.tip.retention.dto.request.CreateCategoryRequest;
import gov.fdic.tip.retention.dto.response.CategoryResponse;
import gov.fdic.tip.retention.enums.RetentionStatus;
import gov.fdic.tip.retention.exception.ConflictException;
import gov.fdic.tip.retention.service.RetentionCategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RetentionCategoryController.class)
@DisplayName("RetentionCategoryController – WebMvcTest slice")
class RetentionCategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RetentionCategoryService categoryService;

    // ── US-1.1: POST /categories ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("POST /categories – valid request → 201 Created")
    void createCategory_validRequest_returns201() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setCode("EXAM_RECORDS");
        req.setName("Examination Records");

        CategoryResponse response = CategoryResponse.builder()
            .id(UUID.randomUUID()).code("EXAM_RECORDS").name("Examination Records")
            .status(RetentionStatus.draft).hasEverHeldContent(false)
            .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
            .createdBy("admin").updatedBy("admin")
            .build();

        when(categoryService.createCategory(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/retention/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("EXAM_RECORDS"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("POST /categories – missing code → 422")
    void createCategory_missingCode_returns422() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("Examination Records");
        // code intentionally omitted

        mockMvc.perform(post("/api/v1/retention/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("POST /categories – invalid code format → 422")
    void createCategory_invalidCodeFormat_returns422() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setCode("invalid-lowercase-code");
        req.setName("Some Name");

        mockMvc.perform(post("/api/v1/retention/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("POST /categories – duplicate code → 409 Conflict")
    void createCategory_duplicateCode_returns409() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setCode("EXAM_RECORDS");
        req.setName("Examination Records");

        when(categoryService.createCategory(any(), any()))
            .thenThrow(new ConflictException("Duplicate code"));

        mockMvc.perform(post("/api/v1/retention/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

	/*
	 * @Test
	 * 
	 * @WithMockUser(roles = "AUDITOR")
	 * 
	 * @DisplayName("POST /categories – non-admin → 403 Forbidden") void
	 * createCategory_nonAdmin_returns403() throws Exception { CreateCategoryRequest
	 * req = new CreateCategoryRequest(); req.setCode("EXAM_RECORDS");
	 * req.setName("Examination Records");
	 * 
	 * mockMvc.perform(post("/api/v1/retention/categories") .with(csrf())
	 * .contentType(MediaType.APPLICATION_JSON)
	 * .content(objectMapper.writeValueAsString(req)))
	 * .andExpect(status().isForbidden()); }
	 */

    // ── DELETE /categories/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("DELETE /categories/{id} – no confirmation header when needed → 409")
    void deleteCategory_missingConfirmationHeader_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ConflictException("X-Confirmation-Code required"))
            .when(categoryService).deleteCategory(any(), isNull(), any());

        mockMvc.perform(delete("/api/v1/retention/categories/" + id)
                .with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "TIP-CM-RETENTION-ADMIN")
    @DisplayName("DELETE /categories/{id} – success → 204 No Content")
    void deleteCategory_success_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doNothing()
            .when(categoryService).deleteCategory(any(), any(), any());

        mockMvc.perform(delete("/api/v1/retention/categories/" + id)
                .with(csrf())
                .header("X-Confirmation-Code", "DELETE CATEGORY"))
            .andExpect(status().isNoContent());
    }
}
