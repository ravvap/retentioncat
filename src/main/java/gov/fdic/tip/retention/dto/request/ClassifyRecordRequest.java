package gov.fdic.tip.retention.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

/**
 * POST /v1/classify-record  – Pattern B
 *
 * Called by an upstream module immediately after it INSERTs an operational record,
 * to register that record's retention classification with TIP.
 *
 * The upstream module is responsible for persisting the returned eligibility_date
 * on its own row. TIP does NOT update the upstream table.
 *
 * sub_category_code is optional: if omitted, the service looks up the default
 * from the operational_table_registry for this schema+table.
 */
@Value @Builder @Jacksonized
@Schema(description = "Classify an operational record for retention (Pattern B – US-1.24-Lean)")
public class ClassifyRecordRequest {

    @NotBlank
    @Schema(description = "PostgreSQL schema of the upstream table", example = "examination")
    String schemaName;

    @NotBlank
    @Schema(description = "Upstream table name", example = "findings")
    String tableName;

    @NotBlank
    @Schema(description = "Primary key value of the newly inserted row", example = "42")
    String pkValue;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "Basis date – value of the basis_date_column for this row",
            example = "2026-05-29")
    LocalDate basisDate;

    @Schema(description = "Override sub-category code for this row. " +
            "If omitted, the registry default is used.",
            example = "EXAM_FINDINGS_25Y")
    String subCategoryCode;
}
