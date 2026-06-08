package gov.fdic.tip.retention.dto.response;

import gov.fdic.tip.retention.enums.RetentionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class CategoryResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private RetentionStatus status;
    private Boolean hasEverHeldContent;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
