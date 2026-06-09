package gov.fdic.tip.retention.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Single entry in the permanent audit trail (Story 3)")
public class AuditEventResponse {

    UUID eventId;
    String eventType;
    String upstreamModuleCode;
    String upstreamReference;
    String retentionBucketCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate retentionDate;

    OffsetDateTime occurredAt;
    String performedBy;

    @Schema(description = "Additional JSON context for this event")
    String eventDetail;
}
