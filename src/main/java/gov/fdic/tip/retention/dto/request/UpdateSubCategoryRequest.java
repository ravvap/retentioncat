package gov.fdic.tip.retention.dto.request;

import gov.fdic.tip.retention.enums.RetentionDurationUnit;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for PATCH /api/v1/retention/sub-categories/{id} (US-1.8).
 * Editable: name, description, retentionDurationValue, retentionDurationUnit.
 * Code is immutable; if present in body → 422.
 * Reason is REQUIRED when retentionDurationValue or retentionDurationUnit changes (AC-1).
 */
@Data
public class UpdateSubCategoryRequest {

    @Size(min = 1, max = 200, message = "name must be 1–200 characters")
    private String name;

    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;

    @Positive(message = "retentionDurationValue must be a positive integer")
    private Integer retentionDurationValue;

    private RetentionDurationUnit retentionDurationUnit;

    /**
     * Free-text reason (10–1000 chars) required when retention duration changes.
     * Captured in the audit payload.
     */
    @Size(min = 10, max = 1000, message = "reason must be 10–1000 characters when changing retention duration")
    private String reason;

    /** If non-null in the body the service rejects with HTTP 422 (code immutability). */
    private String code;

    /** True if either retentionDurationValue or retentionDurationUnit is being changed. */
    public boolean isRetentionChanging() {
        return retentionDurationValue != null || retentionDurationUnit != null;
    }
}
