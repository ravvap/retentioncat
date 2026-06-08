package gov.fdic.tip.retention.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tipRetentionOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("TIP Content Manager – Category-Driven Retention API")
                .description("""
                    FDIC Treasury Integrated Platform – Retention Management (R1 Lean MVP).
                    
                    Covers Category and Sub-Category lifecycle (US-1.1 – US-1.12).
                    All mutating operations require the **TIP-CM-RETENTION-ADMIN** role.
                    Read operations are available to any authenticated user.
                    
                    **CONTROLLED // FDIC INTERNAL ONLY**
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("FDIC Division of Finance – TIP Program")
                    .email("tip-support@fdic.gov"))
                .license(new License()
                    .name("FDIC Internal Use Only")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local Dev"),
                new Server().url("https://tip-retention-dev.fdic.gov").description("Dev"),
                new Server().url("https://tip-retention.fdic.gov").description("Production")
            ))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Azure AD OAuth2 JWT token (Entra ID)")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
