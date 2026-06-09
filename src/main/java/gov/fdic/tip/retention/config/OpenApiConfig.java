package gov.fdic.tip.retention.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tipRetentionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TIP CM Retention API")
                        .description("""
                            FDIC TIP Content Management – Retention Lean MVP
                            
                            **CONTROLLED // FDIC INTERNAL ONLY**
                            
                            Implements three user stories:
                            - **Story 1 (US-1.13-Lean)** – Promote a document into retention
                            - **Story 2 (US-1.24-Lean)** – Classify every new record automatically
                            - **Story 3 (US-1.Audit-Lean)** – Reconstruct retention history for any record
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TIP Team")
                                .email("tip-team@fdic.gov")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")));
    }
}
