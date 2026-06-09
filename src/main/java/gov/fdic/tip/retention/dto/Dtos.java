package gov.fdic.tip.retention.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ─── Story 1: Promote a Document ─────────────────────────────────────────────

/** Request body for POST /retention-records */
@Value @Builder
@Schema(description = "Request to promote a document into retention")
class PromoteDocumentRequest {

    @NotBlank
    @Schema(description = "Caller's own identifier for the document", example = "EW-FINDING-2026-00123")
    String upstreamReference;

    @NotBlank
    @Schema(description = "Retention bucket code (must be active)", example = "EXAM_FINDINGS_25Y")
    String retentionBucketCode;

    @NotNull
    @Schema(description = "Business event date – date the triggering event occurred", example = "2026-05-29")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @Schema(description = "Azure Blob Storage URI for the document content (optional)")
    String blobStorageUri;
}

/** Response body after successful promotion */
@Value @Builder 
@Schema(description = "Confirmation of a successful document promotion")

class PromoteDocumentResponse {
    UUID retentionRecordId;
    String upstreamReference;
    String retentionBucketCode;
    LocalDate basisDate;
    LocalDate retentionDate;
    OffsetDateTime promotedAt;
    UUID auditEventId;
}

// ─── Story 2: Table Onboarding ────────────────────────────────────────────────

@Value @Builder @Jacksonized
@Schema(description = "Request to onboard a table for automatic retention classification")

class OnboardTableRequest {

    @NotBlank String moduleCode;
    @NotBlank String schemaName;
    @NotBlank String tableName;
    @NotBlank String pkColumn;
    @NotBlank String basisDateColumn;
    @NotBlank String retentionBucketCode;
    @NotBlank String onboardedBy;
}

@Value @Builder @Jacksonized
@Schema(description = "Confirmation of successful table onboarding")
class OnboardTableResponse {
    UUID onboardingId;
    String schemaName;
    String tableName;
    String triggerName;
    String retentionBucketCode;
    OffsetDateTime onboardedAt;
}

// ─── Story 3: Audit Events ────────────────────────────────────────────────────

/*
 * @Value @Builder
 * 
 * @Schema(description = "Single audit trail entry") class AuditEventResponse {
 * UUID eventId; String eventType; String upstreamModuleCode; String
 * upstreamReference; String retentionBucketCode; LocalDate basisDate; LocalDate
 * retentionDate; OffsetDateTime occurredAt; String performedBy; String
 * eventDetail; }
 */

// Make all DTOs package-accessible for the controller/service layer
public final class Dtos {
    public static final Class<PromoteDocumentRequest>  PROMOTE_REQ  = PromoteDocumentRequest.class;
    public static final Class<PromoteDocumentResponse> PROMOTE_RESP = PromoteDocumentResponse.class;

    private Dtos() {}

    // Re-export types so other packages can use them
    public static PromoteDocumentRequest.PromoteDocumentRequestBuilder promoteRequest() {
        return PromoteDocumentRequest.builder();
    }
    public static PromoteDocumentResponse.PromoteDocumentResponseBuilder promoteResponse() {
        return PromoteDocumentResponse.builder();
    }
    public static OnboardTableRequest.OnboardTableRequestBuilder onboardRequest() {
        return OnboardTableRequest.builder();
    }
    public static OnboardTableResponse.OnboardTableResponseBuilder onboardResponse() {
        return OnboardTableResponse.builder();
    }
    public static AuditEventResponse.AuditEventResponseBuilder auditResponse() {
        return AuditEventResponse.builder();
    }
}
