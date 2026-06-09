package gov.fdic.tip.retention.config;

import gov.fdic.tip.retention.entity.UpstreamModule;
import gov.fdic.tip.retention.repository.UpstreamModuleRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * API key-based authentication.
 * Upstream modules supply X-API-Key and X-Module-Code headers (AC-2).
 * Operations staff supply an admin API key for onboarding endpoints.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Onboarding restricted to OPS role
                .requestMatchers("/v1/onboarding/**").hasRole("OPS")
                // Audit trail read-only for AUDITOR and OPS
                .requestMatchers("/v1/audit-events/**").hasAnyRole("OPS", "AUDITOR", "MODULE")
                // Promotion endpoint for registered modules
                .requestMatchers("/v1/retention-records/**").hasAnyRole("MODULE", "OPS")
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// ── API Key Filter ────────────────────────────────────────────────────────────

@Component
@RequiredArgsConstructor
@Slf4j
class ApiKeyFilter extends OncePerRequestFilter {

    private final UpstreamModuleRepository moduleRepo;
    private final BCryptPasswordEncoder    encoder = new BCryptPasswordEncoder();

    /** Hard-coded ops key hash – in production, load from Vault / env. */
    private static final String OPS_KEY_HASH =
            "$2a$10$placeholder.ops.key.hash.replace.in.production.only";

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String apiKey    = req.getHeader("X-API-Key");
        String moduleCode = req.getHeader("X-Module-Code");

        if (apiKey == null) {
            chain.doFilter(req, res);
            return;
        }

        // Check OPS key first
        if (encoder.matches(apiKey, OPS_KEY_HASH)) {
            setAuthentication("OPS", "ROLE_OPS");
            chain.doFilter(req, res);
            return;
        }

        // Check registered module key (AC-2)
        if (moduleCode != null) {
            Optional<UpstreamModule> module = moduleRepo.findByModuleCodeAndActiveTrue(moduleCode);
            if (module.isPresent() && encoder.matches(apiKey, module.get().getApiKeyHash())) {
                setAuthentication(moduleCode, "ROLE_MODULE");
                chain.doFilter(req, res);
                return;
            }
        }

        // Reject unrecognised callers
        log.warn("[SECURITY] Rejected API call – invalid key or module code: module={}", moduleCode);
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
    }

    private void setAuthentication(String principal, String role) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
