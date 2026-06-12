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
@Schema(description = "Result of a Pattern A document classification")
public class ClassifyDocumentResponse {

    @Schema(description = "TIP UUID – store this to look up the record later")
    UUID retentionRecordId;

    String sourceReference;
    String moduleCode;
    String categoryCode;
    String categoryName;
    String subCategoryCode;
    String subCategoryName;
    short retentionDurationValue;
    String retentionDurationUnit;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate eligibilityDate;

    OffsetDateTime classifiedAt;
    UUID auditEventId;
}
