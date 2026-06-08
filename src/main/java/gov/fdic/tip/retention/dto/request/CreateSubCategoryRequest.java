package gov.fdic.tip.retention.dto.request;

import gov.fdic.tip.retention.enums.RetentionDurationUnit;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for POST /api/v1/retention/sub-categories (US-1.6).
 */
@Data
public class CreateSubCategoryRequest {

    /** Parent Category must be Active. */
    @NotNull(message = "categoryId is required")
    private UUID categoryId;

    /**
     * Stable machine identifier. 1–64 chars, uppercase letters/digits/underscores.
     * Unique within the parent Category. Immutable after creation.
     */
    @NotBlank(message = "code is required")
    @Size(min = 1, max = 64, message = "code must be 1–64 characters")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$",
             message = "code must match ^[A-Z][A-Z0-9_]{0,63}$")
    private String code;

    /** Human-readable label. 1–200 chars, case-insensitively unique within parent. */
    @NotBlank(message = "name is required")
    @Size(min = 1, max = 200, message = "name must be 1–200 characters")
    private String name;

    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;

    /** Positive integer portion of the retention period. */
    @NotNull(message = "retentionDurationValue is required")
    @Positive(message = "retentionDurationValue must be a positive integer")
    private Integer retentionDurationValue;

    /** Unit: days | months | years. */
    @NotNull(message = "retentionDurationUnit is required (days | months | years)")
    private RetentionDurationUnit retentionDurationUnit;
}
