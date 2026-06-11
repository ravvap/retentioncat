package gov.fdic.tip.retention;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class TipRetentionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TipRetentionApplication.class, args);
    }
}
