package gov.fdic.tip.retention.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * POST /v1/table-registrations  – OPS role only, one-time per upstream table.
 *
 * Registers an upstream operational table for Pattern B retention classification.
 * After this, the upstream module calls POST /v1/classify-record for each new row.
 */
@Value @Builder @Jacksonized
@Schema(description = "Register an upstream table for Pattern B retention (one-time setup)")
public class RegisterTableRequest {

    @NotBlank
    @Schema(description = "PostgreSQL schema of the upstream table", example = "examination")
    String schemaName;

    @NotBlank
    @Schema(description = "Upstream table name", example = "findings")
    String tableName;

    @NotBlank
    @Schema(description = "Column whose value will be passed as basis_date",
            example = "closed_date")
    String basisDateColumn;

    @NotBlank
    @Schema(description = "Default Sub-Category code applied when the module does not override",
            example = "EXAM_FINDINGS_25Y")
    String defaultSubCategoryCode;

    @NotBlank
    @Schema(description = "Human-readable name of the registering person or system",
            example = "jsmith (TIP operations)")
    String registeredBy;
}
