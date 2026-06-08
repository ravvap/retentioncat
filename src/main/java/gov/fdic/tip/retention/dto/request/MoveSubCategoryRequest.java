package gov.fdic.tip.retention.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for PATCH /api/v1/retention/sub-categories/{id}/move (US-1.9).
 * Target Category must be Active.
 */
@Data
public class MoveSubCategoryRequest {

    @NotNull(message = "targetCategoryId is required")
    private UUID targetCategoryId;
}
