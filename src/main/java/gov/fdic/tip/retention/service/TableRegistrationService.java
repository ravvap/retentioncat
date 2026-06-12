package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.RegisterTableRequest;
import gov.fdic.tip.retention.dto.response.RegisterTableResponse;
import gov.fdic.tip.retention.entity.OperationalTableRegistry;
import gov.fdic.tip.retention.entity.RetentionSubCategory;
import gov.fdic.tip.retention.exception.DuplicateRegistrationException;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.repository.OperationalTableRegistryRepository;
import gov.fdic.tip.retention.repository.RetentionSubCategoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Pattern B – one-time table registration (OPS role only).
 *
 * Registers an upstream operational table so that TIP's DB trigger will
 * automatically stamp eligibility_date + write audit on every INSERT.
 *
 * Flow:
 *  1. Validate default sub-category
 *  2. Guard against duplicate registration
 *  3. Persist OperationalTableRegistry row
 *  4. Call fn_install_pattern_b_trigger() to dynamically install
 *     BEFORE + AFTER INSERT triggers on the upstream table
 *
 * After this, the upstream module just does:
 *   INSERT INTO upstream_table (...) VALUES (...)
 * and the triggers handle everything automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TableRegistrationService {

    private final OperationalTableRegistryRepository registryRepo;
    private final RetentionSubCategoryRepository     subCategoryRepo;
    private final EntityManager                      em;

    @Transactional
    public RegisterTableResponse register(String moduleCode, RegisterTableRequest req) {

        // Guard: duplicate schema+table
        if (registryRepo.existsBySchemaNameAndTableName(req.getSchemaName(), req.getTableName())) {
            throw new DuplicateRegistrationException(
                    req.getSchemaName() + "." + req.getTableName());
        }

        // Validate default sub-category
        RetentionSubCategory defaultSubCat = subCategoryRepo
                .findByCodeAndActiveTrueAndClassificationAllowedTrue(req.getDefaultSubCategoryCode())
                .orElseThrow(() -> new SubCategoryNotFoundException(req.getDefaultSubCategoryCode()));

        OffsetDateTime now = OffsetDateTime.now();

        // Persist registry row
        OperationalTableRegistry reg = OperationalTableRegistry.builder()
                .schemaName(req.getSchemaName())
                .tableName(req.getTableName())
                .basisDateColumn(req.getBasisDateColumn())
                .defaultSubCategory(defaultSubCat)
                .owningModuleCode(moduleCode)
                .active(true)
                .registeredAt(now)
                .registeredBy(req.getRegisteredBy())
                .build();

        reg = registryRepo.save(reg);
        em.flush(); // ensure the registry row is persisted before trigger reads it

        // Dynamically install BEFORE + AFTER triggers on the upstream table
        String triggerName = installTrigger(reg.getId().toString(),
                req.getBasisDateColumn(), req.getSchemaName(), req.getTableName());

        log.info("[REGISTER-B][SUCCESS] module={} table={}.{} defaultSubCat={} trigger={}",
                moduleCode, req.getSchemaName(), req.getTableName(),
                req.getDefaultSubCategoryCode(), triggerName);

        return RegisterTableResponse.builder()
                .registrationId(reg.getId())
                .schemaName(reg.getSchemaName())
                .tableName(reg.getTableName())
                .basisDateColumn(reg.getBasisDateColumn())
                .defaultSubCategoryCode(defaultSubCat.getCode())
                .defaultSubCategoryName(defaultSubCat.getName())
                .owningModuleCode(moduleCode)
                .triggerName(triggerName)
                .registeredAt(reg.getRegisteredAt())
                .build();
    }

    /**
     * Calls the PL/pgSQL function that dynamically generates and installs
     * the BEFORE + AFTER INSERT triggers on the upstream table.
     *
     * The function is defined in V2__functions_triggers.sql:
     *   tip.fn_install_pattern_b_trigger(reg_id, basis_date_column, schema, table)
     *
     * Returns the trigger name installed.
     */
    private String installTrigger(String registrationId, String basisDateColumn,
                                   String schemaName, String tableName) {
        try {
            String triggerName = (String) em.createNativeQuery(
                    "SELECT tip.fn_install_pattern_b_trigger(?::uuid, ?, ?, ?)"
            )
            .setParameter(1, registrationId)
            .setParameter(2, basisDateColumn)
            .setParameter(3, schemaName)
            .setParameter(4, tableName)
            .getSingleResult();

            log.info("[REGISTER-B][TRIGGER_INSTALLED] trigger={}", triggerName);
            return triggerName;
        } catch (Exception e) {
            log.error("[REGISTER-B][TRIGGER_FAIL] Could not install trigger on {}.{}: {}",
                    schemaName, tableName, e.getMessage());
            throw new RuntimeException(
                    "Failed to install retention trigger on " + schemaName + "." + tableName
                    + ": " + e.getMessage(), e);
        }
    }
}
