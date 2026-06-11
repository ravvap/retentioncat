package gov.fdic.tip.retention.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
@Schema(description = "Confirmation of Pattern B table registration")
public class RegisterTableResponse {
    UUID           registrationId;
    String         schemaName;
    String         tableName;
    String         basisDateColumn;
    String         defaultSubCategoryCode;
    String         defaultSubCategoryName;
    String         owningModuleCode;
    OffsetDateTime registeredAt;
}
