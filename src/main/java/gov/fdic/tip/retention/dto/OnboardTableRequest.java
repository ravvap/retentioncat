package gov.fdic.tip.retention.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Request to onboard a table for automatic retention classification (Story 2 – US-1.24-Lean)")
public class OnboardTableRequest {

    @NotBlank
    @Schema(description = "Upstream module code", example = "EW")
    String moduleCode;

    @NotBlank
    @Schema(description = "Database schema containing the upstream table", example = "examination")
    String schemaName;

    @NotBlank
    @Schema(description = "Upstream table name", example = "findings")
    String tableName;

    @NotBlank
    @Schema(description = "Primary key column name in the upstream table", example = "id")
    String pkColumn;

    @NotBlank
    @Schema(description = "Column whose value will be used as the basis date", example = "closed_date")
    String basisDateColumn;

    @NotBlank
    @Schema(description = "Retention bucket code to assign to all new rows", example = "EXAM_FINDINGS_25Y")
    String retentionBucketCode;

    @NotBlank
    @Schema(description = "Person authorising the onboarding", example = "jsmith")
    String onboardedBy;
}
