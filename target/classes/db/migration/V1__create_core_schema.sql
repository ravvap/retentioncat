-- =============================================================
-- TIP CM Retention – Lean MVP
-- V1: Core retention schema
-- CONTROLLED//FDIC INTERNAL ONLY
-- =============================================================

-- ---------------------------------------------------------------
-- 1. tip_retention_categories
-- ---------------------------------------------------------------
CREATE TABLE tip_retention_categories (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64)     NOT NULL UNIQUE,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft'
                            CHECK (status IN ('active','draft','inactive')),
    has_ever_held_content BOOLEAN       NOT NULL DEFAULT FALSE,

    -- audit columns
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255)    NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(255)    NOT NULL,
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(255)
);

COMMENT ON TABLE tip_retention_categories IS
    'Top-level retention groupings (e.g. "Examination Records"). '
    'status must be explicitly set to active; default is draft.';

-- ---------------------------------------------------------------
-- 2. tip_retention_sub_categories
-- ---------------------------------------------------------------
CREATE TABLE tip_retention_sub_categories (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id             UUID            NOT NULL
                                REFERENCES tip_retention_categories(id),
    code                    VARCHAR(64)     NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    description             TEXT,
    retention_duration_value INTEGER        NOT NULL CHECK (retention_duration_value > 0),
    retention_duration_unit  VARCHAR(16)    NOT NULL
                                CHECK (retention_duration_unit IN ('days','months','years')),
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft'
                                CHECK (status IN ('active','draft','inactive')),
    has_ever_held_content   BOOLEAN         NOT NULL DEFAULT FALSE,

    -- audit columns
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255)    NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(255)    NOT NULL,
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(255),

    UNIQUE (category_id, code)
);

CREATE INDEX idx_sub_cat_category_id  ON tip_retention_sub_categories(category_id);
CREATE INDEX idx_sub_cat_status       ON tip_retention_sub_categories(status);

COMMENT ON TABLE tip_retention_sub_categories IS
    'Leaf-level retention buckets. retention_duration_value + unit define how long '
    'content must be kept. status must be active for new content to be classified here.';

-- ---------------------------------------------------------------
-- 3. tip_retention_configuration
-- ---------------------------------------------------------------
CREATE TABLE tip_retention_configuration (
    key         VARCHAR(255)    PRIMARY KEY,
    value       JSONB           NOT NULL,
    description TEXT,

    -- audit columns
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted_at  TIMESTAMPTZ,
    deleted_by  VARCHAR(255)
);

COMMENT ON TABLE tip_retention_configuration IS
    'Key-value configuration store. '
    'cm_documents.authorized_promotion_services holds the JSONB array of Entra OIDs '
    'permitted to call POST /api/v1/cm/documents.';

-- Seed: authorized service principals allowlist (empty – populated at deploy)
INSERT INTO tip_retention_configuration (key, value, description, created_by, updated_by)
VALUES (
    'cm_documents.authorized_promotion_services',
    '[]'::jsonb,
    'JSONB array of Entra service-principal object IDs authorized to call '
    'POST /api/v1/cm/documents. Empty array disables the endpoint for all callers.',
    'flyway',
    'flyway'
);

-- ---------------------------------------------------------------
-- 4. content_manager.documents  (the promoted-document store)
-- ---------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS content_manager;

CREATE TABLE content_manager.documents (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_category_id         UUID            NOT NULL
                                REFERENCES tip_retention_sub_categories(id),
    source_system           VARCHAR(64)     NOT NULL,
    source_reference        VARCHAR(255)    NOT NULL,
    filename                VARCHAR(512)    NOT NULL,
    size_bytes              BIGINT          NOT NULL CHECK (size_bytes > 0),
    sha256                  CHAR(64)        NOT NULL,
    uploaded_by_user_id     VARCHAR(255)    NOT NULL,
    basis_date              DATE            NOT NULL,
    eligibility_date        DATE,
    retention_evaluated_at  TIMESTAMPTZ,

    -- audit columns
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255)    NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(255)    NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(255)
);

CREATE INDEX idx_cm_docs_sub_cat      ON content_manager.documents(sub_category_id);
CREATE INDEX idx_cm_docs_source       ON content_manager.documents(source_system, source_reference);
CREATE INDEX idx_cm_docs_eligibility  ON content_manager.documents(eligibility_date);

COMMENT ON TABLE content_manager.documents IS
    'Retention-managed documents promoted via US-1.13-Lean. '
    'The BEFORE INSERT trigger tip_retention_classify_document computes eligibility_date '
    'and writes the audit event atomically.';

-- ---------------------------------------------------------------
-- 5. tip_operational_table_registrations  (Pattern B registry)
-- ---------------------------------------------------------------
CREATE TABLE tip_operational_table_registrations (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_name             VARCHAR(255)    NOT NULL,
    table_name              VARCHAR(255)    NOT NULL,
    classification_pattern  VARCHAR(8)      NOT NULL DEFAULT 'B'
                                CHECK (classification_pattern IN ('B')),
    primary_key_columns     TEXT[]          NOT NULL,   -- e.g. ARRAY['id']
    basis_date_column       VARCHAR(255)    NOT NULL,
    default_sub_category_id UUID            NOT NULL
                                REFERENCES tip_retention_sub_categories(id),
    registered_by           VARCHAR(255)    NOT NULL,
    registered_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- audit columns
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255)    NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(255)    NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(255),

    UNIQUE (schema_name, table_name)
);

COMMENT ON TABLE tip_operational_table_registrations IS
    'One row per operational table enrolled in Pattern B retention. '
    'The BEFORE INSERT trigger reads this registry at runtime.';

-- ---------------------------------------------------------------
-- 6. tip_retention_audit_archive
-- ---------------------------------------------------------------
CREATE TABLE tip_retention_audit_archive (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type          VARCHAR(128)    NOT NULL,   -- e.g. retention.document.classified
    entity_type         VARCHAR(64)     NOT NULL,   -- document | record
    entity_id           JSONB           NOT NULL,   -- {"id": "<uuid>"} or composite PK
    schema_name         VARCHAR(255),
    table_name          VARCHAR(255),
    sub_category_id     UUID,
    eligibility_date    DATE,
    basis_date          DATE,
    source_system       VARCHAR(64),
    source_reference    VARCHAR(255),
    payload             JSONB           NOT NULL DEFAULT '{}',
    actor_user_id       VARCHAR(255),
    correlation_id      UUID,
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- audit columns (who wrote the audit row itself)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255)    NOT NULL DEFAULT 'trigger',
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(255)    NOT NULL DEFAULT 'trigger',
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(255)
);

CREATE INDEX idx_audit_event_type     ON tip_retention_audit_archive(event_type);
CREATE INDEX idx_audit_sub_cat        ON tip_retention_audit_archive(sub_category_id);
CREATE INDEX idx_audit_occurred_at    ON tip_retention_audit_archive(occurred_at);
CREATE INDEX idx_audit_source         ON tip_retention_audit_archive(source_system);
CREATE INDEX idx_audit_schema_table   ON tip_retention_audit_archive(schema_name, table_name);

COMMENT ON TABLE tip_retention_audit_archive IS
    'Immutable audit log. Written by DB triggers (never by application code). '
    'Queried directly via SQL or Power BI in the lean MVP.';
