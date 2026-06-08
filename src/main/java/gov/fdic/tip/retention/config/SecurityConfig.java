package gov.fdic.tip.retention.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the TIP Retention service.
 *
 * Production profile: expects Microsoft Entra (Azure AD) OAuth2 JWT bearer tokens.
 * Dev profile: in-memory users for local testing without an IdP.
 *
 * Role mapping:
 *   TIP-CM-RETENTION-ADMIN → mutating operations (POST/PATCH/DELETE on categories and sub-categories)
 *   Any authenticated user  → read operations (GET hierarchy, GET categories, etc.)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**",
        "/actuator/health"
    };

    // ── Production profile (Azure AD OAuth2 JWT) ──────────────────────────────

    @Bean
    @Profile("!dev")
    public SecurityFilterChain productionFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/retention/**").authenticated()
                .anyRequest().hasRole("TIP-CM-RETENTION-ADMIN")
            )
            // Wire in Azure AD OAuth2 resource server in real deployment:
            // .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(azureConverter())))
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ── Dev profile: in-memory users, no IdP needed ───────────────────────────

    @Bean
    @Profile("dev")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Profile("dev")
    public UserDetailsService devUsers() {
        // Dev-only in-memory credentials – never use in production
        var admin = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("admin")
            .roles("TIP-CM-RETENTION-ADMIN")
            .build();

        var auditor = User.withDefaultPasswordEncoder()
            .username("auditor")
            .password("auditor")
            .roles("AUDITOR")
            .build();

        return new InMemoryUserDetailsManager(admin, auditor);
    }
}
