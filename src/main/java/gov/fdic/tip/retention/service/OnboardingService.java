package gov.fdic.tip.retention.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gov.fdic.tip.retention.dto.OnboardTableRequest;
import gov.fdic.tip.retention.dto.OnboardTableResponse;
import gov.fdic.tip.retention.entity.RetentionBucket;
import gov.fdic.tip.retention.entity.TableOnboarding;
import gov.fdic.tip.retention.entity.UpstreamModule;
import gov.fdic.tip.retention.exception.RetentionBucketNotFoundException;
import gov.fdic.tip.retention.exception.UnauthorizedModuleException;
import gov.fdic.tip.retention.repository.RetentionBucketRepository;
import gov.fdic.tip.retention.repository.TableOnboardingRepository;
import gov.fdic.tip.retention.repository.UpstreamModuleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Story 2 – US-1.24-Lean: Classify every new record automatically.
 *
 * AC-1  Onboarding is a one-time event coordinated by operations, TIP, and upstream.
 * AC-2  After onboarding every new row gets a retention date automatically.
 * AC-3  Retention date uses the current period for the chosen bucket.
 * AC-4  The basis-date column is chosen and locked at onboarding.
 * AC-6  Misconfiguration causes loud failure, not silent wrong classification.
 * AC-9  Setup designed for future extensibility without re-onboarding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final TableOnboardingRepository onboardingRepo;
    private final UpstreamModuleRepository  moduleRepo;
    private final RetentionBucketRepository bucketRepo;
    private final EntityManager             em;

    /**
     * Onboard a table: record the configuration and install a PostgreSQL trigger
     * via the tip.fn_install_retention_trigger stored function.
     */
    @Transactional
    public OnboardTableResponse onboard(OnboardTableRequest req) {

        // Validate module
        UpstreamModule module = moduleRepo.findByModuleCodeAndActiveTrue(req.getModuleCode())
                .orElseThrow(() -> new UnauthorizedModuleException(
                        "Module not found or inactive: " + req.getModuleCode()));

        // Validate bucket (AC-6: fail loudly)
        RetentionBucket bucket = bucketRepo.findByBucketCodeAndActiveTrue(req.getRetentionBucketCode())
                .orElseThrow(() -> new RetentionBucketNotFoundException(req.getRetentionBucketCode()));

        // Idempotency: already onboarded?
        if (onboardingRepo.existsBySchemaNameAndTableName(req.getSchemaName(), req.getTableName())) {
            throw new IllegalArgumentException(
                    "Table already onboarded: " + req.getSchemaName() + "." + req.getTableName());
        }

        // Persist onboarding record (AC-1: one-time)
        OffsetDateTime now = OffsetDateTime.now();
        TableOnboarding onboarding = TableOnboarding.builder()
                .upstreamModule(module)
                .schemaName(req.getSchemaName())
                .tableName(req.getTableName())
                .basisDateColumn(req.getBasisDateColumn())
                .retentionBucket(bucket)
                .active(true)
                .onboardedAt(now)
                .onboardedBy(req.getOnboardedBy())
                .build();

        onboarding = onboardingRepo.save(onboarding);

        // Install the PostgreSQL trigger via the stored function
        // This dynamically creates a trigger function + trigger on the upstream table (AC-2)
        String triggerName = installTrigger(
                req.getSchemaName(), req.getTableName(),
                req.getPkColumn(), req.getBasisDateColumn(),
                onboarding.getId().toString()
        );

        onboarding.setTriggerName(triggerName);
        onboarding = onboardingRepo.save(onboarding);

        log.info("[ONBOARD][SUCCESS] module={} table={}.{} trigger={}",
                req.getModuleCode(), req.getSchemaName(), req.getTableName(), triggerName);

        return OnboardTableResponse.builder()
                .onboardingId(onboarding.getId())
                .schemaName(onboarding.getSchemaName())
                .tableName(onboarding.getTableName())
                .triggerName(triggerName)
                .retentionBucketCode(bucket.getBucketCode())
                .onboardedAt(onboarding.getOnboardedAt())
                .build();
    }

    /**
     * Calls tip.fn_install_retention_trigger(…) which dynamically creates the
     * per-table trigger function and attaches the trigger.
     */
    private String installTrigger(String schema, String table,
                                   String pkCol, String basisDateCol,
                                   String onboardingId) {
        try {
            String triggerName = (String) em.createNativeQuery(
                    "SELECT tip.fn_install_retention_trigger(:schema, :table, :pk, :bd, :oid::uuid)")
                    .setParameter("schema", schema)
                    .setParameter("table",  table)
                    .setParameter("pk",     pkCol)
                    .setParameter("bd",     basisDateCol)
                    .setParameter("oid",    onboardingId)
                    .getSingleResult();
            return triggerName;
        } catch (Exception e) {
            log.error("Failed to install DB trigger for {}.{}: {}", schema, table, e.getMessage());
            throw new RuntimeException("Trigger installation failed – check DB logs", e);
        }
    }
}
