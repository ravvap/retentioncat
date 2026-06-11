package gov.fdic.tip.retention.service;

import gov.fdic.tip.retention.entity.RetentionSubCategory.DurationUnit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Pure utility – no Spring dependencies beyond @Component.
 * Computes the eligibility date from a basis date and retention duration.
 * Extracted so it can be unit-tested in isolation without mocking.
 */
@Component
public class RetentionCalculator {

    /**
     * eligibility_date = basis_date + retention_duration
     *
     * @param basisDate  the business event date (non-null)
     * @param value      numeric portion of the retention period (e.g. 25)
     * @param unit       DAYS / MONTHS / YEARS
     * @return computed eligibility date
     */
    public LocalDate compute(LocalDate basisDate, short value, DurationUnit unit) {
        return switch (unit) {
            case DAYS   -> basisDate.plusDays(value);
            case MONTHS -> basisDate.plusMonths(value);
            case YEARS  -> basisDate.plusYears(value);
        };
    }
}
