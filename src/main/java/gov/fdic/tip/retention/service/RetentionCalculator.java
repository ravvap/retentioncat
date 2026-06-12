package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.entity.RetentionSubCategory.DurationUnit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Pure utility: computes eligibility_date = basis_date + retention duration.
 *
 * Used by Pattern A (ClassificationService) only.
 * Pattern B uses the equivalent PL/pgSQL function fn_compute_eligibility_date().
 *
 * Extracted as a standalone component so it can be unit-tested in isolation.
 */
@Component
public class RetentionCalculator {

    public LocalDate compute(LocalDate basisDate, short value, DurationUnit unit) {
        return switch (unit) {
            case DAYS   -> basisDate.plusDays(value);
            case MONTHS -> basisDate.plusMonths(value);
            case YEARS  -> basisDate.plusYears(value);
        };
    }
}
