package gov.fdic.tip.retention.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder @Jacksonized
public class OnboardTableResponse {
    UUID onboardingId;
    String schemaName;
    String tableName;
    String triggerName;
    String retentionBucketCode;
    OffsetDateTime onboardedAt;
}
