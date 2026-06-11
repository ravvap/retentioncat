package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.dto.request.RegisterTableRequest;
import gov.fdic.tip.retention.dto.response.RegisterTableResponse;
import gov.fdic.tip.retention.entity.*;
import gov.fdic.tip.retention.exception.DuplicateRegistrationException;
import gov.fdic.tip.retention.exception.SubCategoryNotFoundException;
import gov.fdic.tip.retention.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Pattern B one-time table registration (OPS role only).
 *
 * Creates an operational_table_registry row so that upstream modules can
 * call POST /v1/classify-record for rows in that table.
 *
 * No database trigger is installed – the upstream module is responsible
 * for calling the API after each INSERT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TableRegistrationService {

    private final OperationalTableRegistryRepository registryRepo;
    private final RetentionSubCategoryRepository     subCategoryRepo;

    @Transactional
    public RegisterTableResponse register(String moduleCode, RegisterTableRequest req) {

        // Guard: duplicate schema+table
        if (registryRepo.existsBySchemaNameAndTableName(req.getSchemaName(), req.getTableName())) {
            throw new DuplicateRegistrationException(
                    req.getSchemaName() + "." + req.getTableName());
        }

        // Validate default sub-category (must be active + classification_allowed)
        RetentionSubCategory defaultSubCat = subCategoryRepo
                .findByCodeAndActiveTrueAndClassificationAllowedTrue(req.getDefaultSubCategoryCode())
                .orElseThrow(() -> new SubCategoryNotFoundException(req.getDefaultSubCategoryCode()));

        OffsetDateTime now = OffsetDateTime.now();
        OperationalTableRegistry reg = OperationalTableRegistry.builder()
                .schemaName(req.getSchemaName())
                .tableName(req.getTableName())
                .basisDateColumn(req.getBasisDateColumn())
                .defaultSubCategory(defaultSubCat)
                .owningModuleCode(moduleCode)          // from JWT, not from request body
                .active(true)
                .registeredAt(now)
                .registeredBy(req.getRegisteredBy())
                .build();

        reg = registryRepo.save(reg);

        log.info("[REGISTER-B][SUCCESS] module={} table={}.{} defaultSubCat={}",
                moduleCode, req.getSchemaName(), req.getTableName(),
                req.getDefaultSubCategoryCode());

        return RegisterTableResponse.builder()
                .registrationId(reg.getId())
                .schemaName(reg.getSchemaName())
                .tableName(reg.getTableName())
                .basisDateColumn(reg.getBasisDateColumn())
                .defaultSubCategoryCode(defaultSubCat.getCode())
                .defaultSubCategoryName(defaultSubCat.getName())
                .owningModuleCode(moduleCode)
                .registeredAt(reg.getRegisteredAt())
                .build();
    }
}
