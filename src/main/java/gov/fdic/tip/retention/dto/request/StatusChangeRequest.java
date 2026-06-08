package gov.fdic.tip.retention.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Optional body for status-change endpoints (US-1.2, US-1.4, US-1.7, US-1.10).
 * The comment field is captured in the audit payload.
 */
@Data
public class StatusChangeRequest {

    @Size(max = 1000, message = "comment must not exceed 1000 characters")
    private String comment;
}
