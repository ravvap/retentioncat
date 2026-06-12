-- =============================================================
-- TIP CM Retention Lean MVP v2
-- CONTROLLED // FDIC INTERNAL ONLY
--
-- Design:
--   • NO upstream_module table  – identity from Azure AD JWT
--   • DB trigger handles Pattern B stamp (eligibility_date,
--     has_ever_held_content, classification_pattern) atomically
--     on every upstream INSERT
--   • Spring Boot handles Pattern A REST, taxonomy reads,
--     audit queries, table registration
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE SCHEMA  IF NOT EXISTS tip;
SET search_path = tip, public;

-- ── Enums ──────────────────────────────────────────────────────────────────

CREATE TYPE tip.duration_unit AS ENUM ('DAYS', 'MONTHS', 'YEARS');
CREATE TYPE tip.classification_pattern AS ENUM ('A', 'B');
CREATE TYPE tip.audit_event_type AS ENUM (
    'DOCUMENT_CLASSIFIED',
    'RECORD_CLASSIFIED',
    'CLASSIFICATION_FAILED'
);

-- =============================================================
-- 1. RETENTION CATEGORY
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
-- 2. RETENTION SUB-CATEGORY  (leaf – retention duration lives here)
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
-- 3. OPERATIONAL TABLE REGISTRY  (Pattern B metadata)
--    One row per upstream table enrolled for automatic retention.
--    The trigger reads this at INSERT time to resolve sub-category
--    and basis_date_column.
-- =============================================================
CREATE TABLE tip.operational_table_registry (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    schema_name             VARCHAR(100) NOT NULL,
    table_name              VARCHAR(100) NOT NULL,
    -- Column in the upstream table whose value becomes basis_date
    basis_date_column       VARCHAR(100) NOT NULL,
    -- Default sub-category when upstream row does not supply sub_category_id
    default_sub_category_id UUID         NOT NULL REFERENCES tip.retention_sub_category (id),
    -- Azure AD appid of the registering module (informational, no FK)
    owning_module_code      VARCHAR(100) NOT NULL,
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
CREATE INDEX idx_otr_active ON tip.operational_table_registry (is_active) WHERE is_active;

-- =============================================================
-- 4. CM_DOCUMENTS  (Pattern A – promoted documents)
--    module_code comes from Azure AD JWT; no FK to module table.
-- =============================================================
CREATE TABLE tip.cm_documents (
    id                    UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    module_code           VARCHAR(100) NOT NULL,
    source_system         VARCHAR(100),
    source_reference      VARCHAR(500) NOT NULL,
    filename              VARCHAR(500),
    size_bytes            BIGINT,
    sha256                VARCHAR(64),
    sub_category_id       UUID         NOT NULL REFERENCES tip.retention_sub_category (id),
    basis_date            DATE         NOT NULL,
    eligibility_date      DATE         NOT NULL,
    has_ever_held_content BOOLEAN      NOT NULL DEFAULT TRUE,
    blob_storage_uri      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(200),
    deleted_at   TIMESTAMPTZ,
    deleted_by   VARCHAR(200),
    CONSTRAINT uq_module_source_ref UNIQUE (module_code, source_reference)
);
CREATE INDEX idx_cmd_module  ON tip.cm_documents (module_code);
CREATE INDEX idx_cmd_sub_cat ON tip.cm_documents (sub_category_id);
CREATE INDEX idx_cmd_elig    ON tip.cm_documents (eligibility_date);

-- =============================================================
-- 5. RETENTION AUDIT ARCHIVE  (permanent, append-only)
--    Written by both the DB trigger (Pattern B) and Spring (Pattern A).
--    DB RULES prevent UPDATE and DELETE.
-- =============================================================
CREATE TABLE tip.retention_audit_archive (
    id                       UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type               tip.audit_event_type       NOT NULL,
    classification_pattern   tip.classification_pattern NOT NULL,
    -- Pattern A
    cm_document_id           UUID         REFERENCES tip.cm_documents (id),
    -- Pattern B
    entity_type              VARCHAR(100),
    entity_id                TEXT,
    table_schema             VARCHAR(100),
    table_name               VARCHAR(100),
    -- Caller identity
    module_code              VARCHAR(100) NOT NULL,
    source_reference         VARCHAR(500),
    -- Taxonomy snapshot (denormalised)
    category_code            VARCHAR(50)  NOT NULL,
    sub_category_code        VARCHAR(50)  NOT NULL,
    -- Retention snapshot
    basis_date               DATE,
    eligibility_date         DATE,
    retention_duration_value SMALLINT,
    retention_duration_unit  TEXT,
    has_ever_held_content    BOOLEAN,
    reason                   TEXT,
    event_detail             JSONB,
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    performed_by             VARCHAR(200) NOT NULL
);

-- Immutability: no UPDATE or DELETE ever
CREATE RULE audit_no_update AS ON UPDATE TO tip.retention_audit_archive DO INSTEAD NOTHING;
CREATE RULE audit_no_delete AS ON DELETE TO tip.retention_audit_archive DO INSTEAD NOTHING;

CREATE INDEX idx_aud_occurred ON tip.retention_audit_archive (occurred_at DESC);
CREATE INDEX idx_aud_cat      ON tip.retention_audit_archive (category_code);
CREATE INDEX idx_aud_subcat   ON tip.retention_audit_archive (sub_category_code);
CREATE INDEX idx_aud_module   ON tip.retention_audit_archive (module_code);
CREATE INDEX idx_aud_type     ON tip.retention_audit_archive (event_type);
CREATE INDEX idx_aud_pattern  ON tip.retention_audit_archive (classification_pattern);
CREATE INDEX idx_aud_cmd      ON tip.retention_audit_archive (cm_document_id);
