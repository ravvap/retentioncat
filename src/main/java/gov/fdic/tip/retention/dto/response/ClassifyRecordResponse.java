package gov.fdic.tip.retention.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Result of a Pattern B record classification. " +
        "The upstream module MUST persist eligibilityDate on its own row.")
public class ClassifyRecordResponse {

    UUID   auditEventId;
    String schemaName;
    String tableName;
    String pkValue;
    String categoryCode;
    String subCategoryCode;
    short  retentionDurationValue;
    String retentionDurationUnit;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    /**
     * The computed eligibility date.
     * The upstream module is responsible for storing this on its own row.
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate eligibilityDate;

    OffsetDateTime classifiedAt;
}
