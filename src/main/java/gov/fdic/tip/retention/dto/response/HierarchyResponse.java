package gov.fdic.tip.retention.dto.response;

import gov.fdic.tip.retention.enums.RetentionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/v1/retention/hierarchy (US-1.12).
 * Returns the full Category → Sub-Category tree with retention durations,
 * statuses, and classified-item counts in one call.
 */
@Data
@Builder
public class HierarchyResponse {

    private List<CategoryNode> categories;
    private int totalCategories;
    private int totalSubCategories;
    private long totalClassifiedDocuments;
    private long totalClassifiedRecords;

    @Data
    @Builder
    public static class CategoryNode {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private RetentionStatus status;
        private Boolean hasEverHeldContent;
        private int subCategoryCount;
        private List<SubCategoryNode> subCategories;
    }

    @Data
    @Builder
    public static class SubCategoryNode {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private RetentionStatus status;
        private Integer retentionDurationValue;
        private String retentionDurationUnit;
        private Boolean hasEverHeldContent;
        private long classifiedItemCount;
    }
}
