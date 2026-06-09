package gov.fdic.tip.retention.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Enables Spring Data JPA auditing.
 * The currentAuditorProvider supplies the authenticated principal's name
 * as the value for @CreatedBy / @LastModifiedBy on every save.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> currentAuditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return Optional.of(auth.getName());
            }
            return Optional.of("SYSTEM");
        };
    }
}
