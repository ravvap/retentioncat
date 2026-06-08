package gov.fdic.tip.retention.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for POST /api/v1/retention/categories (US-1.1).
 */
@Data
public class CreateCategoryRequest {

    /**
     * Stable machine identifier. 1–64 chars. Regex: ^[A-Z][A-Z0-9_]{0,63}$
     * Globally unique; immutable after creation.
     */
    @NotBlank(message = "code is required")
    @Size(min = 1, max = 64, message = "code must be 1–64 characters")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$",
             message = "code must match ^[A-Z][A-Z0-9_]{0,63}$ (uppercase letters, digits, underscores)")
    private String code;

    /** Human-readable label. 1–200 chars, case-insensitively unique. */
    @NotBlank(message = "name is required")
    @Size(min = 1, max = 200, message = "name must be 1–200 characters")
    private String name;

    /** Optional description. Up to 2000 chars. */
    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;
}
