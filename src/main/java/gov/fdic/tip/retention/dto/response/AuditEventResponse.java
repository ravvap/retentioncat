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
@Schema(description = "Single entry in the permanent retention audit archive")
public class AuditEventResponse {
    UUID           eventId;
    String         eventType;
    String         classificationPattern;
    String         moduleCode;
    String         sourceReference;
    String         categoryCode;
    String         subCategoryCode;
    short          retentionDurationValue;
    String         retentionDurationUnit;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate eligibilityDate;

    Boolean        hasEverHeldContent;
    UUID           cmDocumentId;
    String         entityType;
    String         entityId;
    String         tableSchema;
    String         tableName;
    String         reason;
    OffsetDateTime occurredAt;
    String         performedBy;
}
