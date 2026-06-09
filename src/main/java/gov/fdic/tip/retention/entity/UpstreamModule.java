package gov.fdic.tip.retention.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "upstream_module", schema = "tip")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpstreamModule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "module_code", nullable = false, unique = true, length = 50)
    private String moduleCode;

    @Column(name = "module_name", nullable = false, length = 200)
    private String moduleName;

    /** bcrypt hash – never returned in API responses */
    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
