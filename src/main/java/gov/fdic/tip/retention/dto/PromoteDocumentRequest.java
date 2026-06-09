package gov.fdic.tip.retention.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value @Builder @Jacksonized
@Schema(description = "Request to promote a document into retention (Story 1 – US-1.13-Lean)")
public class PromoteDocumentRequest {

    @NotBlank
    @Schema(description = "Caller's own identifier for the document", example = "EW-FINDING-2026-00123")
    String upstreamReference;

    @NotBlank
    @Schema(description = "Retention bucket code – must exist and be active", example = "EXAM_FINDINGS_25Y")
    String retentionBucketCode;

    @NotNull
    @Schema(description = "Business event date (e.g. date examination closed)", example = "2026-05-29")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate basisDate;

    @Schema(description = "Azure Blob Storage URI for the document file (optional – content lives in Blob)")
    String blobStorageUri;
}
