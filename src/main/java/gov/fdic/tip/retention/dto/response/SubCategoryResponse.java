package gov.fdic.tip.retention.dto.response;

import gov.fdic.tip.retention.enums.RetentionDurationUnit;
import gov.fdic.tip.retention.enums.RetentionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SubCategoryResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String code;
    private String name;
    private String description;
    private Integer retentionDurationValue;
    private RetentionDurationUnit retentionDurationUnit;
    private RetentionStatus status;
    private Boolean hasEverHeldContent;
    private Long classifiedItemCount;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
