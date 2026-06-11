-- =============================================================
-- TIP CM Retention Lean MVP v2
-- CONTROLLED // FDIC INTERNAL ONLY
--
-- Design principles:
--   1. NO database triggers  – all logic in Spring Boot
--   2. NO upstream_module table – identity from Azure AD JWT
--   3. Audit events store module_code from JWT claim (denormalized)
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA IF NOT EXISTS tip;
SET search_path = tip, public;

-- ── Enums ─────────────────────────────────────────────────────────────────────

CREATE TYPE tip.duration_unit AS ENUM ('DAYS', 'MONTHS', 'YEARS');
CREATE TYPE tip.classification_pattern AS ENUM ('A', 'B');
CREATE TYPE tip.audit_event_type AS ENUM (
    'DOCUMENT_CLASSIFIED',
    'RECORD_CLASSIFIED',
    'CLASSIFICATION_FAILED'
);

-- =============================================================
-- 1. RETENTION CATEGORY  (top-level grouping)
-- =============================================================
CREATE TABLE tip.retention_category (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(200),
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(200)
);

CREATE INDEX idx_rc_code   ON tip.retention_category (code);
CREATE INDEX idx_rc_active ON tip.retention_category (is_active) WHERE is_active;

-- =============================================================
-- 2. RETENTION SUB-CATEGORY  (leaf bucket – retention lives here)
-- =============================================================
CREATE TABLE tip.retention_sub_category (
    id                       UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id              UUID         NOT NULL REFERENCES tip.retention_category (id),
    code                     VARCHAR(50)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    retention_duration_value SMALLINT     NOT NULL CHECK (retention_duration_value > 0),
    retention_duration_unit  tip.duration_unit NOT NULL,
    fallback_sub_category_id UUID         REFERENCES tip.retention_sub_category (id),
    classification_allowed   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(200),
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(200)
);

CREATE INDEX idx_rsc_category ON tip.retention_sub_category (category_id);
CREATE INDEX idx_rsc_code     ON tip.retention_sub_category (code);
CREATE INDEX idx_rsc_active   ON tip.retention_sub_category (is_active, classification_allowed)
    WHERE is_active AND classification_allowed;

-- =============================================================
-- 3. PATTERN B – OPERATIONAL TABLE REGISTRY
--    One row per upstream table enrolled for retention.
--    No module FK – module identity stored as plain varchar from JWT.
-- =============================================================
CREATE TABLE tip.operational_table_registry (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    schema_name             VARCHAR(100) NOT NULL,
    table_name              VARCHAR(100) NOT NULL,
    basis_date_column       VARCHAR(100) NOT NULL,
    default_sub_category_id UUID         NOT NULL REFERENCES tip.retention_sub_category (id),
    owning_module_code      VARCHAR(100) NOT NULL,   -- from JWT claim at registration time
    classification_pattern  tip.classification_pattern NOT NULL DEFAULT 'B',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    registered_by           VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(200),
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(200),
    CONSTRAINT uq_schema_table UNIQUE (schema_name, table_name)
);

CREATE INDEX idx_otr_module ON tip.operational_table_registry (owning_module_code);
CREATE INDEX idx_otr_active ON tip.operational_table_registry (is_active) WHERE is_active;

-- =============================================================
-- 4. CM_DOCUMENTS  (Pattern A – promoted documents)
--    module_code stored as varchar from JWT – no FK to module table.
-- =============================================================
CREATE TABLE tip.cm_documents (
    id                    UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    module_code           VARCHAR(100) NOT NULL,     -- from JWT claim
    source_system         VARCHAR(100),
    source_reference      VARCHAR(500) NOT NULL,
    filename              VARCHAR(500),
    size_bytes            BIGINT,
    sha256                VARCHAR(64),
    sub_category_id       UUID         NOT NULL REFERENCES tip.retention_sub_category (id),
    basis_date            DATE         NOT NULL,
    eligibility_date      DATE         NOT NULL,
    has_ever_held_content BOOLEAN      NOT NULL DEFAULT FALSE,
    blob_storage_uri      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(200),
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(200),
    CONSTRAINT uq_module_source_ref UNIQUE (module_code, source_reference)
);

CREATE INDEX idx_cmd_module   ON tip.cm_documents (module_code);
CREATE INDEX idx_cmd_sub_cat  ON tip.cm_documents (sub_category_id);
CREATE INDEX idx_cmd_elig     ON tip.cm_documents (eligibility_date);
CREATE INDEX idx_cmd_basis    ON tip.cm_documents (basis_date);

-- =============================================================
-- 5. AUDIT ARCHIVE  (permanent, append-only)
--    DB RULES enforce immutability – no UPDATE or DELETE ever.
-- =============================================================
CREATE TABLE tip.retention_audit_archive (
    id                       UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type               tip.audit_event_type       NOT NULL,
    classification_pattern   tip.classification_pattern NOT NULL,
    -- Pattern A ref
    cm_document_id           UUID         REFERENCES tip.cm_documents (id),
    -- Pattern B ref
    entity_type              VARCHAR(100),
    entity_id                TEXT,
    table_schema             VARCHAR(100),
    table_name               VARCHAR(100),
    -- Who / what
    module_code              VARCHAR(100) NOT NULL,
    source_reference         VARCHAR(500),
    -- Taxonomy snapshot (denormalized – survives taxonomy changes)
    category_code            VARCHAR(50)  NOT NULL,
    sub_category_code        VARCHAR(50)  NOT NULL,
    -- Retention snapshot
    basis_date               DATE,
    eligibility_date         DATE,
    retention_duration_value SMALLINT,
    retention_duration_unit  TEXT,
    has_ever_held_content    BOOLEAN,
    -- Failure reason (CLASSIFICATION_FAILED only)
    reason                   TEXT,
    -- Extra context
    event_detail             JSONB,
    -- When / who
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    performed_by             VARCHAR(200) NOT NULL
);

-- Immutability rules – no UPDATE or DELETE ever
CREATE RULE audit_no_update AS ON UPDATE TO tip.retention_audit_archive DO INSTEAD NOTHING;
CREATE RULE audit_no_delete AS ON DELETE TO tip.retention_audit_archive DO INSTEAD NOTHING;

CREATE INDEX idx_aud_occurred  ON tip.retention_audit_archive (occurred_at DESC);
CREATE INDEX idx_aud_cat       ON tip.retention_audit_archive (category_code);
CREATE INDEX idx_aud_subcat    ON tip.retention_audit_archive (sub_category_code);
CREATE INDEX idx_aud_module    ON tip.retention_audit_archive (module_code);
CREATE INDEX idx_aud_type      ON tip.retention_audit_archive (event_type);
CREATE INDEX idx_aud_pattern   ON tip.retention_audit_archive (classification_pattern);
CREATE INDEX idx_aud_cm_doc    ON tip.retention_audit_archive (cm_document_id);
