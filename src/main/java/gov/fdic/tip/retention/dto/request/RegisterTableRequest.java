package gov.fdic.tip.retention.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value @Builder @Jacksonized
@Schema(description = "Register an upstream table for Pattern B automatic retention (OPS, one-time)")
public class RegisterTableRequest {

    @NotBlank
    @Schema(description = "PostgreSQL schema of the upstream table", example = "examination")
    String schemaName;

    @NotBlank
    @Schema(description = "Upstream table name", example = "findings")
    String tableName;

    @NotBlank
    @Schema(description = "Column whose value becomes basis_date for each row",
            example = "closed_date")
    String basisDateColumn;

    @NotBlank
    @Schema(description = "Default Sub-Category code when a row does not supply one",
            example = "EXAM_FINDINGS_25Y")
    String defaultSubCategoryCode;

    @NotBlank
    @Schema(description = "Name of the person or system performing this registration",
            example = "jsmith")
    String registeredBy;
}
