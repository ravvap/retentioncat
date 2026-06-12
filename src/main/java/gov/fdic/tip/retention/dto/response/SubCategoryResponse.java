package gov.fdic.tip.retention.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Retention Sub-Category (leaf retention bucket)")
public class SubCategoryResponse {
    UUID id;
    String categoryCode;
    String categoryName;
    String code;
    String name;
    String description;
    short retentionDurationValue;
    String retentionDurationUnit;
    boolean classificationAllowed;
    boolean active;
    OffsetDateTime createdAt;
    String createdBy;
}
