package gov.fdic.tip.retention.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Extracts identity claims from the current Azure AD JWT.
 *
 * Azure AD service-principal tokens include:
 *   "appid" – client application ID of the calling service (module identity)
 *   "sub"   – subject (used as performed_by and @CreatedBy)
 *
 * Claim name is configurable via tip.security.module-claim in application.yml.
 */
@Component
@Slf4j
public class JwtClaimExtractor {

    @Value("${tip.security.module-claim:appid}")
    private String moduleClaim;

    /**
     * Returns the module_code to store in cm_documents and audit events.
     * Reads the "appid" claim from the current JWT; falls back to "sub".
     */
    public String getModuleCode() {
        Jwt jwt = currentJwt();
        if (jwt == null) {
            log.warn("No JWT in SecurityContext – returning UNKNOWN as module_code");
            return "UNKNOWN";
        }
        String appId = jwt.getClaimAsString(moduleClaim);
        return (appId != null && !appId.isBlank()) ? appId : jwt.getSubject();
    }

    /**
     * Returns the JWT subject – used as performed_by in audit events.
     */
    public String getSubject() {
        Jwt jwt = currentJwt();
        return jwt != null ? jwt.getSubject() : "UNKNOWN";
    }

    private Jwt currentJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken();
        }
        return null;
    }
}
