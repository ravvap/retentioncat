package gov.fdic.tip.retention.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "TIP CM Retention API",
        version     = "2.0",
        description = "FDIC TIP Content Management Retention – Lean MVP v2.\n\n" +
                      "**Pattern A (US-1.13-Lean):** Classify documents into retention via REST.\n\n" +
                      "**Pattern B (US-1.24-Lean):** Operational records classified automatically " +
                      "via DB trigger at INSERT time.\n\n" +
                      "**Authentication:** Azure AD / Microsoft Entra Bearer token. " +
                      "Paste a JWT from your Azure AD tenant into the Authorize dialog below.",
        contact     = @Contact(name = "FDIC TIP Team")
    ),
    servers = {
        @Server(url = "/tip/api", description = "Default")
    }
)
@SecurityScheme(
    name   = "bearerAuth",
    type   = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description  = "Azure AD JWT. Obtain via: POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token"
)
public class OpenApiConfig {
    // All configuration via annotations above
}
