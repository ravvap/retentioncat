package gov.fdic.tip.retention;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TipRetentionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TipRetentionApplication.class, args);
    }
}
