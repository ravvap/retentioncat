package gov.fdic.tip.retention.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Helper used by services to extract the module identity from the current JWT.
 *
 * Azure AD tokens for service principals include an "appid" claim containing
 * the client application ID of the calling service. We use this as module_code
 * in audit events and idempotency checks – no lookup in a module table required.
 *
 * Claim used (configurable via tip.security.module-claim):
 *   "appid"  – the client ID of the OAuth2 application (service-to-service)
 *   "sub"    – subject; used as fallback and for created_by auditing
 */
@Component
@Slf4j
public class JwtClaimExtractor {

    @Value("${tip.security.module-claim:appid}")
    private String moduleClaim;

    /** Returns the module_code to store in audit rows and cm_documents. */
    public String getModuleCode() {
        Jwt jwt = currentJwt();
        if (jwt == null) return "UNKNOWN";
        String appId = jwt.getClaimAsString(moduleClaim);
        return appId != null ? appId : jwt.getSubject();
    }

    /** Returns the JWT subject (used for performed_by in audit events). */
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
