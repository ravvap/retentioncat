package gov.fdic.tip.retention.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

/**
 * POST /v1/documents  – Pattern A
 *
 * Called by an upstream module (Examination Workflow, Case Management, etc.)
 * at the moment a business milestone triggers document retention.
 * The caller's module identity comes from the Azure AD JWT – not from this body.
 */
@Value @Builder @Jacksonized
@Schema(description = "Promote a document into TIP retention (Pattern A – US-1.13-Lean)")
public class ClassifyDocumentRequest {

    @NotBlank
    @Schema(description = "Caller's own document identifier – used for idempotency",
            example = "EW-2026-0314-finding-1")
    String sourceReference;

    @Schema(description = "Caller system label", example = "examination_workflow")
    String sourceSystem;

    @Schema(description = "Original filename", example = "EW-2026-0314-finding-1.pdf")
    String filename;

    @Positive
    @Schema(description = "File size in bytes", example = "204800")
    Long sizeBytes;

    @Size(min = 64, max = 64)
    @Schema(description = "SHA-256 hex digest of the file")
    String sha256;

    @NotBlank
    @Schema(description = "Sub-Category code – must be active and classification_allowed=true",
            example = "EXAM_FINDINGS_25Y")
    String subCategoryCode;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "Business event date (e.g. date examination closed)",
            example = "2026-05-29")
    LocalDate basisDate;

    @Schema(description = "Azure Blob Storage URI of the document content")
    String blobStorageUri;
}
