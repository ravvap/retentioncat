-- =============================================================
-- TIP CM Retention – Lean MVP
-- V3: Example taxonomy seed + Pattern B registration
--     for examination_workflow.examination_findings
--
-- This migration illustrates Appendix A.1 and A.2 procedures.
-- Real taxonomy UUIDs should be generated and pinned before
-- running this migration in production.
-- CONTROLLED//FDIC INTERNAL ONLY
-- =============================================================

-- ---------------------------------------------------------------
-- A.1 — Taxonomy seed: Category
-- ---------------------------------------------------------------
INSERT INTO tip_retention_categories
    (id, code, name, description, status, created_by, updated_by)
VALUES
    ('8f9a2c3d-0000-0000-0000-000000000001'::uuid,
     'EXAMINATION_RECORDS',
     'Examination Records',
     'FDIC examination-related records requiring long-term retention per NARA schedule.',
     'active',
     'flyway', 'flyway');

-- ---------------------------------------------------------------
-- A.1 — Taxonomy seed: Sub-Category (25-year retention)
-- ---------------------------------------------------------------
INSERT INTO tip_retention_sub_categories
    (id, category_id, code, name, description,
     retention_duration_value, retention_duration_unit,
     status, created_by, updated_by)
VALUES
    ('8f9a2c3d-0000-0000-0000-000000000002'::uuid,
     '8f9a2c3d-0000-0000-0000-000000000001'::uuid,
     'EXAMINATION_FINDINGS',
     'Examination Findings',
     'Individual examination findings retained for 25 years per NARA schedule.',
     25, 'years',
     'active',
     'flyway', 'flyway');

-- ---------------------------------------------------------------
-- A.2 — Pattern B: onboard examination_workflow.examination_findings
-- Step (a): ALTER TABLE to add retention columns
-- ---------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS examination_workflow;

CREATE TABLE IF NOT EXISTS examination_workflow.examination_findings (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    examination_id          UUID        NOT NULL,
    finding_text            TEXT        NOT NULL,
    finding_closed_at       DATE,

    -- Retention columns (populated by the BEFORE INSERT trigger)
    sub_category_id         UUID        REFERENCES tip_retention_sub_categories(id),
    eligibility_date        DATE,
    retention_evaluated_at  TIMESTAMPTZ,

    -- Audit columns
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(255)
);

-- Step (b): INSERT into registry
INSERT INTO tip_operational_table_registrations
    (id, schema_name, table_name, classification_pattern,
     primary_key_columns, basis_date_column,
     default_sub_category_id, registered_by,
     created_by, updated_by)
VALUES
    (gen_random_uuid(),
     'examination_workflow',
     'examination_findings',
     'B',
     ARRAY['id'],
     'finding_closed_at',
     '8f9a2c3d-0000-0000-0000-000000000002'::uuid,
     'flyway',
     'flyway', 'flyway');

-- Step (c): Install BEFORE INSERT trigger on the operational table
CREATE TRIGGER trg_examination_findings_classify
    BEFORE INSERT ON examination_workflow.examination_findings
    FOR EACH ROW EXECUTE FUNCTION tip_retention_classify_pattern_b();
