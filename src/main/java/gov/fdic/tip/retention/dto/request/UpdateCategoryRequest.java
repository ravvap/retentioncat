package gov.fdic.tip.retention.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PATCH /api/v1/retention/categories/{id} (US-1.3).
 * Only name and description are editable. Code is immutable (rejected with 422 if supplied).
 */
@Data
public class UpdateCategoryRequest {

    /** If present, must be 1–200 chars and case-insensitively unique. */
    @Size(min = 1, max = 200, message = "name must be 1–200 characters")
    private String name;

    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;

    /**
     * If this field is non-null in the request body the service must reject with HTTP 422.
     * Declared here so Jackson can detect its presence during deserialization.
     */
    private String code;
}
