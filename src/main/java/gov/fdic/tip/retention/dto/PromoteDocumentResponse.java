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
@Schema(description = "Confirmation returned after a successful document promotion")
public class PromoteDocumentResponse {

    @Schema(description = "TIP-generated retention record ID – store this for future lookups")
    UUID retentionRecordId;

    String upstreamReference;
    String retentionBucketCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate retentionDate;

    OffsetDateTime promotedAt;

    @Schema(description = "ID of the corresponding audit event")
    UUID auditEventId;
}
