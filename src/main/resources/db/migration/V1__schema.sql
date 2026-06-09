-- ============================================================
-- TIP CM Retention Lean MVP  –  PostgreSQL Schema
-- CONTROLLED // FDIC INTERNAL ONLY
-- ============================================================

-- ── Extensions ───────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Schema ───────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS tip;
SET search_path = tip, public;

-- ============================================================
-- LOOKUP / REFERENCE TABLES
-- ============================================================

-- 1. Upstream Modules  (registered callers allowed to use the API)
-- Examples: Examination Workflow, Case Management, Request Manager
CREATE TABLE upstream_module (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    module_code         VARCHAR(50)  NOT NULL UNIQUE,   -- e.g. 'EW', 'CM', 'RM'
    module_name         VARCHAR(200) NOT NULL,
    api_key_hash        TEXT         NOT NULL,           -- bcrypt hash of the issued API key
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100)
);
COMMENT ON TABLE upstream_module IS
    'Registered FDIC systems authorised to call the TIP retention API.';

-- 2. Retention Buckets  (defined & owned by Records Management)
CREATE TABLE retention_bucket (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    bucket_code         VARCHAR(50)  NOT NULL UNIQUE,   -- e.g. 'EXAM_FINDINGS_25Y'
    bucket_name         VARCHAR(200) NOT NULL,           -- "Examination Findings"
    retention_years     SMALLINT     NOT NULL CHECK (retention_years > 0),
    description         TEXT,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100)
);
COMMENT ON TABLE retention_bucket IS
    'Named category of records sharing a single retention period. Defined by Records Management.';

-- ============================================================
-- CORE RETENTION TABLES
-- ============================================================

-- 3. Retention Record  (one row per document promoted via API)
--    Story 1 – US-1.13-Lean
CREATE TABLE retention_record (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    upstream_module_id      UUID        NOT NULL REFERENCES upstream_module(id),
    upstream_reference      VARCHAR(500) NOT NULL,  -- caller's own doc ID
    retention_bucket_id     UUID        NOT NULL REFERENCES retention_bucket(id),
    basis_date              DATE         NOT NULL,   -- business event date supplied by caller
    retention_date          DATE         NOT NULL,   -- computed: basis_date + retention_years
    blob_storage_uri        TEXT,                    -- Azure Blob pointer; content lives there
    -- audit columns
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(100),
    -- prevent true duplicates while allowing the caller to retry safely
    CONSTRAINT uq_upstream_doc UNIQUE (upstream_module_id, upstream_reference)
);
COMMENT ON TABLE retention_record IS
    'One row per document brought under retention via the promote-document API (Story 1).';

-- 4. Table Onboarding  (one row per upstream table enrolled for auto-classification)
--    Story 2 – US-1.24-Lean
CREATE TABLE table_onboarding (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    upstream_module_id      UUID        NOT NULL REFERENCES upstream_module(id),
    schema_name             VARCHAR(100) NOT NULL,
    table_name              VARCHAR(100) NOT NULL,
    basis_date_column       VARCHAR(100) NOT NULL,   -- column the trigger reads for basis date
    retention_bucket_id     UUID         NOT NULL REFERENCES retention_bucket(id),
    trigger_name            VARCHAR(200),            -- name of the DB trigger installed
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    onboarded_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    onboarded_by            VARCHAR(100) NOT NULL,
    -- audit columns
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL,
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(100),
    CONSTRAINT uq_onboarded_table UNIQUE (schema_name, table_name)
);
COMMENT ON TABLE table_onboarding IS
    'Records every upstream database table enrolled for automatic retention classification (Story 2).';

-- 5. Automatic Classification Record  (created by trigger for each new row in onboarded table)
CREATE TABLE auto_classification (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_onboarding_id     UUID        NOT NULL REFERENCES table_onboarding(id),
    upstream_row_id         TEXT         NOT NULL,   -- PK value of the upstream row (as text)
    retention_bucket_id     UUID         NOT NULL REFERENCES retention_bucket(id),
    basis_date              DATE         NOT NULL,
    retention_date          DATE         NOT NULL,
    -- audit columns
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(100) NOT NULL DEFAULT 'SYSTEM_TRIGGER',
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(100)
);
COMMENT ON TABLE auto_classification IS
    'One row per upstream table-row automatically classified by a DB trigger (Story 2).';

-- ============================================================
-- AUDIT TRAIL  (Story 3 – US-1.Audit-Lean)
-- Single immutable table covering BOTH promotions and auto-classifications
-- ============================================================

CREATE TYPE tip.audit_event_type AS ENUM (
    'DOCUMENT_PROMOTED',
    'AUTO_CLASSIFIED',
    'PROMOTION_FAILED',
    'AUTO_CLASSIFICATION_FAILED',
    'RECORD_CORRECTED'
);

CREATE TABLE audit_event (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type              tip.audit_event_type NOT NULL,
    -- polymorphic source references (one will be populated)
    retention_record_id     UUID            REFERENCES retention_record(id),
    auto_classification_id  UUID            REFERENCES auto_classification(id),
    -- denormalised snapshot (never changes even if upstream data changes)
    upstream_module_code    VARCHAR(50)     NOT NULL,
    upstream_reference      VARCHAR(500),
    retention_bucket_code   VARCHAR(50)     NOT NULL,
    basis_date              DATE,
    retention_date          DATE,
    event_detail            JSONB,          -- free-form context (error messages, etc.)
    -- who / when
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    performed_by            VARCHAR(100)    NOT NULL,
    -- Audit events are PERMANENT – no update / delete columns intentionally
    CONSTRAINT chk_audit_source CHECK (
        retention_record_id IS NOT NULL OR auto_classification_id IS NOT NULL
        OR event_type IN ('PROMOTION_FAILED','AUTO_CLASSIFICATION_FAILED')
    )
);
COMMENT ON TABLE audit_event IS
    'Permanent, append-only audit trail for all retention decisions (Story 3). Never updated or deleted.';

-- Prevent any UPDATE or DELETE on audit_event
CREATE RULE audit_event_no_update AS ON UPDATE TO audit_event DO INSTEAD NOTHING;
CREATE RULE audit_event_no_delete AS ON DELETE TO audit_event DO INSTEAD NOTHING;

-- ============================================================
-- INDEXES
-- ============================================================

-- upstream_module
CREATE INDEX idx_um_module_code     ON upstream_module(module_code);
CREATE INDEX idx_um_is_active       ON upstream_module(is_active) WHERE is_active = TRUE;

-- retention_bucket
CREATE INDEX idx_rb_bucket_code     ON retention_bucket(bucket_code);
CREATE INDEX idx_rb_is_active       ON retention_bucket(is_active) WHERE is_active = TRUE;

-- retention_record
CREATE INDEX idx_rr_upstream_module ON retention_record(upstream_module_id);
CREATE INDEX idx_rr_bucket          ON retention_record(retention_bucket_id);
CREATE INDEX idx_rr_basis_date      ON retention_record(basis_date);
CREATE INDEX idx_rr_retention_date  ON retention_record(retention_date);

-- auto_classification
CREATE INDEX idx_ac_onboarding      ON auto_classification(table_onboarding_id);
CREATE INDEX idx_ac_bucket          ON auto_classification(retention_bucket_id);
CREATE INDEX idx_ac_retention_date  ON auto_classification(retention_date);

-- audit_event  (supports auditor queries by bucket, date range, module)
CREATE INDEX idx_ae_event_type      ON audit_event(event_type);
CREATE INDEX idx_ae_occurred_at     ON audit_event(occurred_at);
CREATE INDEX idx_ae_bucket_code     ON audit_event(retention_bucket_code);
CREATE INDEX idx_ae_module_code     ON audit_event(upstream_module_code);
CREATE INDEX idx_ae_rr_id           ON audit_event(retention_record_id);
CREATE INDEX idx_ae_ac_id           ON audit_event(auto_classification_id);
