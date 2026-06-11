package gov.fdic.tip.retention.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Retention Category")
public class CategoryResponse {
    UUID           id;
    String         code;
    String         name;
    String         description;
    boolean        active;
    OffsetDateTime createdAt;
    String         createdBy;
}
