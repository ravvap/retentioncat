package gov.fdic.tip.retention.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Azure AD / Microsoft Entra JWT resource server security.
 *
 * Token claims used:
 *   "roles"  – array of Azure AD app roles assigned to the service principal
 *              e.g. ["TIP.Documents.Write", "TIP.Records.Write", "TIP.Audit.Read"]
 *   "appid"  – client application ID; used as the module_code stored in audit events
 *   "sub"    – subject; used as the performed_by / created_by in audit/JPA auditing
 *
 * Roles defined in the App Registration manifest:
 *   TIP.Documents.Write  – upstream modules calling Pattern A (POST /v1/documents)
 *   TIP.Records.Write    – upstream modules calling Pattern B (POST /v1/classify-record)
 *   TIP.Tables.Register  – OPS only (POST /v1/table-registrations)
 *   TIP.Audit.Read       – auditors (GET /v1/audit-events)
 *   TIP.Taxonomy.Read    – anyone browsing categories/sub-categories
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${tip.security.roles-claim:roles}")
    private String rolesClaim;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: Swagger UI and health
                .requestMatchers(
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Taxonomy – any authenticated caller
                .requestMatchers("GET", "/v1/categories/**").hasAuthority("SCOPE_TIP.Taxonomy.Read")
                .requestMatchers("GET", "/v1/sub-categories/**").hasAuthority("SCOPE_TIP.Taxonomy.Read")
                // Pattern A
                .requestMatchers("POST", "/v1/documents").hasAuthority("SCOPE_TIP.Documents.Write")
                .requestMatchers("GET",  "/v1/documents/**").hasAuthority("SCOPE_TIP.Audit.Read")
                // Pattern B – classify record (upstream modules)
                .requestMatchers("POST", "/v1/classify-record").hasAuthority("SCOPE_TIP.Records.Write")
                // Pattern B – register table (OPS only)
                .requestMatchers("POST", "/v1/table-registrations").hasAuthority("SCOPE_TIP.Tables.Register")
                // Audit archive
                .requestMatchers("GET", "/v1/audit-events/**").hasAuthority("SCOPE_TIP.Audit.Read")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            );

        return http.build();
    }

    /**
     * Converts Azure AD JWT roles → Spring Security GrantedAuthority.
     *
     * Azure AD encodes app roles in a "roles" array claim:
     *   { "roles": ["TIP.Documents.Write", "TIP.Audit.Read"] }
     *
     * We prefix each role with "SCOPE_" so Spring's hasAuthority() checks work
     * without requiring a separate ROLE_ prefix convention.
     */
    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter conv = new JwtGrantedAuthoritiesConverter();
        // Use the "roles" claim instead of the default "scope"/"scp"
        conv.setAuthoritiesClaimName(rolesClaim);
        conv.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter jwtConv = new JwtAuthenticationConverter();
        jwtConv.setJwtGrantedAuthoritiesConverter(conv);
        // Use "sub" claim as the principal name (used by JPA auditing @CreatedBy)
        jwtConv.setPrincipalClaimName("sub");
        return jwtConv;
    }

    /**
     * JPA auditing: @CreatedBy / @LastModifiedBy pull from the JWT "sub" claim.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return Optional.of("SYSTEM");
            return Optional.of(auth.getName());
        };
    }
}
