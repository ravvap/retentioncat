package gov.fdic.tip.retention.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

/**
 * Azure AD / Microsoft Entra JWT Resource Server security.
 *
 * Token claims used:
 *   "roles"  – Azure AD app roles assigned to the service principal
 *   "appid"  – client application ID; stored as module_code in audit events
 *   "sub"    – subject; used as created_by / performed_by in JPA auditing
 *
 * App roles defined in the Azure AD App Registration manifest:
 *   TIP.Taxonomy.Read     – browse categories / sub-categories
 *   TIP.Documents.Write   – Pattern A: POST /v1/documents
 *   TIP.Tables.Register   – OPS: POST /v1/table-registrations
 *   TIP.Audit.Read        – GET /v1/audit-events + GET /v1/documents
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${tip.security.roles-claim:roles}")
    private String rolesClaim;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: health + Swagger
                .requestMatchers(
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Taxonomy – any authenticated JWT
                .requestMatchers("GET", "/v1/categories/**")
                    .hasAuthority("SCOPE_TIP.Taxonomy.Read")
                .requestMatchers("GET", "/v1/sub-categories/**")
                    .hasAuthority("SCOPE_TIP.Taxonomy.Read")
                // Pattern A
                .requestMatchers("POST", "/v1/documents")
                    .hasAuthority("SCOPE_TIP.Documents.Write")
                .requestMatchers("GET", "/v1/documents/**")
                    .hasAuthority("SCOPE_TIP.Audit.Read")
                // Pattern B – register table (OPS only)
                .requestMatchers("POST", "/v1/table-registrations")
                    .hasAuthority("SCOPE_TIP.Tables.Register")
                // Audit archive
                .requestMatchers("GET", "/v1/audit-events/**")
                    .hasAuthority("SCOPE_TIP.Audit.Read")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Converts Azure AD "roles" claim → Spring GrantedAuthority.
     * Prefix "SCOPE_" matches hasAuthority("SCOPE_TIP.Documents.Write") checks.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter conv = new JwtGrantedAuthoritiesConverter();
        conv.setAuthoritiesClaimName(rolesClaim);
        conv.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter jwtConv = new JwtAuthenticationConverter();
        jwtConv.setJwtGrantedAuthoritiesConverter(conv);
        jwtConv.setPrincipalClaimName("sub");
        return jwtConv;
    }

    /**
     * JPA auditing: @CreatedBy / @LastModifiedBy populated from JWT "sub" claim.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }
            return Optional.of(auth.getName());
        };
    }
}
