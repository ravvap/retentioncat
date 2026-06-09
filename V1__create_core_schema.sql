-- =============================================================
-- TIP CM Retention – Lean MVP
-- V1: Core retention schema (Corrected)
-- CONTROLLED//FDIC INTERNAL ONLY
-- =============================================================

CREATE SCHEMA IF NOT EXISTS txn;

-- ---------------------------------------------------------------
-- 1. txn.retention_categories
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_categories (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(64)     NOT NULL UNIQUE,
    name                  VARCHAR(255)    NOT NULL,
    description           TEXT,
    status                VARCHAR(16)     NOT NULL DEFAULT 'draft'
                              CHECK (status IN ('active','draft','inactive')),
    has_ever_held_content BOOLEAN         NOT NULL DEFAULT FALSE,

    -- audit columns
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255)    NOT NULL,
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            VARCHAR(255)    NOT NULL,
    deleted_at            TIMESTAMPTZ,
    deleted_by            VARCHAR(255)
);

COMMENT ON TABLE txn.retention_categories IS
    'Top-level retention groupings (e.g. "Examination Records"). '
    'status must be explicitly set to active; default is draft.';

-- ---------------------------------------------------------------
-- 2. txn.retention_sub_categories
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_sub_categories (
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id              UUID            NOT NULL 
                                 REFERENCES txn.retention_categories(id), -- FIXED: Was pointing to self (retention_sub_categories)
    code                     VARCHAR(64)     NOT NULL,
    name                     VARCHAR(255)    NOT NULL,
    description              TEXT,
    retention_duration_value INTEGER         NOT NULL CHECK (retention_duration_value > 0),
    retention_duration_unit  VARCHAR(16)     NOT NULL
                                 CHECK (retention_duration_unit IN ('days','months','years')),
    status                   VARCHAR(16)     NOT NULL DEFAULT 'draft'
                                 CHECK (status IN ('active','draft','inactive')),
    has_ever_held_content    BOOLEAN         NOT NULL DEFAULT FALSE,

    -- audit columns
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255)    NOT NULL,
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            VARCHAR(255)    NOT NULL,
    deleted_at            TIMESTAMPTZ,
    deleted_by            VARCHAR(255),

    UNIQUE (category_id, code)
);

CREATE INDEX idx_sub_cat_category_id  ON txn.retention_sub_categories(category_id);
CREATE INDEX idx_sub_cat_status       ON txn.retention_sub_categories(status);

COMMENT ON TABLE txn.retention_sub_categories IS
    'Leaf-level retention buckets. retention_duration_value + unit define how long '
    'content must be kept. status must be active for new content to be classified here.';

-- ---------------------------------------------------------------
-- 3. txn.upstream_module
-- ---------------------------------------------------------------
CREATE TABLE txn.upstream_module ( -- FIXED: Standardized name to match downstream foreign key targets
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(), -- FIXED: Standardized to gen_random_uuid()
    module_code         VARCHAR(50)  NOT NULL UNIQUE,   -- e.g. 'EW', 'CM', 'RM'
    module_name         VARCHAR(200) NOT NULL,
    api_key_hash        TEXT         NOT NULL,           -- bcrypt hash of the issued API key
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    
    -- audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100)
);

COMMENT ON TABLE txn.upstream_module IS
    'Registered FDIC systems authorised to call the TIP retention API.';

-- ---------------------------------------------------------------
-- 4. txn.retention_table_onboarding  (Pattern B registry)
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_table_onboarding (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    upstream_module_id      UUID            NOT NULL REFERENCES txn.upstream_module(id), -- FIXED: Explicit schema mapping
    schema_name             VARCHAR(255)    NOT NULL,
    table_name              VARCHAR(255)    NOT NULL,
    classification_pattern  VARCHAR(8)      NOT NULL DEFAULT 'B'
                                CHECK (classification_pattern IN ('B')),
    primary_key_columns     TEXT[]          NOT NULL,   -- e.g. ARRAY['id']
    basis_date_column       VARCHAR(255)    NOT NULL,
    default_sub_category_id UUID            NOT NULL
                                REFERENCES txn.retention_sub_categories(id), -- FIXED: Corrected table reference name prefix
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

COMMENT ON TABLE txn.retention_table_onboarding IS
    'One row per operational table enrolled in Pattern B retention. '
    'The BEFORE INSERT trigger reads this registry at runtime.';

-- ---------------------------------------------------------------
-- 5. txn.retention_documents  (the promoted-document store)
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_documents (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_category_id         UUID            NOT NULL
                                REFERENCES txn.retention_sub_categories(id),
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

CREATE INDEX idx_cm_docs_sub_cat      ON txn.retention_documents(sub_category_id);
CREATE INDEX idx_cm_docs_source       ON txn.retention_documents(source_system, source_reference);
CREATE INDEX idx_cm_docs_eligibility  ON txn.retention_documents(eligibility_date);

COMMENT ON TABLE txn.retention_documents IS
    'Retention-managed documents promoted via US-1.13-Lean. '
    'The BEFORE INSERT trigger tip_retention_classify_document computes eligibility_date '
    'and writes the audit event atomically.';

-- ---------------------------------------------------------------
-- 6. txn.retention_bucket  (defined & owned by Records Management)
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_bucket (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_code         VARCHAR(50)  NOT NULL UNIQUE,   -- e.g. 'EXAM_FINDINGS_25Y'
    bucket_name         VARCHAR(200) NOT NULL,           -- "Examination Findings"
    retention_years     SMALLINT     NOT NULL CHECK (retention_years > 0),
    description         TEXT,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    
    -- audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100)
);

COMMENT ON TABLE txn.retention_bucket IS
    'Named category of records sharing a single retention period. Defined by Records Management.';

-- ---------------------------------------------------------------
-- 7. txn.auto_classification
-- ---------------------------------------------------------------
CREATE TABLE txn.auto_classification (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    table_onboarding_id     UUID        NOT NULL REFERENCES txn.retention_table_onboarding(id),
    upstream_row_id         TEXT         NOT NULL,   -- PK value of the upstream row (as text)
    retention_bucket_id     UUID         NOT NULL REFERENCES txn.retention_bucket(id),
    basis_date              DATE         NOT NULL,
    retention_date          DATE         NOT NULL,
    
    -- audit columns
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100) NOT NULL DEFAULT 'SYSTEM_TRIGGER',
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    deleted_at              TIMESTAMPTZ,
    deleted_by              VARCHAR(100)
);

COMMENT ON TABLE txn.auto_classification IS
    'One row per upstream table-row automatically classified by a DB trigger (Story 2).';

-- ---------------------------------------------------------------
-- 8. txn.retention_audit_event
-- ---------------------------------------------------------------
CREATE TABLE txn.retention_audit_event (
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

CREATE INDEX idx_audit_event_type     ON txn.retention_audit_event(event_type);
CREATE INDEX idx_audit_sub_cat        ON txn.retention_audit_event(sub_category_id);
CREATE INDEX idx_audit_occurred_at    ON txn.retention_audit_event(occurred_at);
CREATE INDEX idx_audit_source         ON txn.retention_audit_event(source_system);
CREATE INDEX idx_audit_schema_table   ON txn.retention_audit_event(schema_name, table_name);

COMMENT ON TABLE txn.retention_audit_event IS
    'Immutable audit log. Written by DB triggers (never by application code). '
    'Queried directly via SQL or Power BI in the lean MVP.';