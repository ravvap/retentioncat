---
--- 1. CONFIGURATION & TAXONOMY TABLES
---
CREATE TABLE txn.tip_retention_configuration (
    key VARCHAR(255) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100)
);

CREATE TABLE txn.tip_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'draft', -- 'active' or 'draft'
    has_ever_held_content BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100)
);

CREATE TABLE txn.tip_retention_sub_categories (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES txn.tip_categories(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    retention_duration_value INT NOT NULL,
    retention_duration_unit VARCHAR(20) NOT NULL, -- 'days', 'months', 'years'
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    has_ever_held_content BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    CONSTRAINT chk_duration_val CHECK (retention_duration_value > 0),
    CONSTRAINT chk_duration_unit CHECK (retention_duration_unit IN ('days', 'months', 'years'))
);

CREATE TABLE txn.tip_operational_table_registrations (
    id UUID PRIMARY KEY,
    schema_name VARCHAR(100) NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    classification_pattern VARCHAR(1) NOT NULL DEFAULT 'B',
    primary_key_columns TEXT[] NOT NULL,
    basis_date_column VARCHAR(100) NOT NULL,
    default_sub_category_id UUID REFERENCES txn.tip_retention_sub_categories(id),
    registered_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    CONSTRAINT unique_table UNIQUE (schema_name, table_name)
);

---
--- 2. AUDIT TRAIL ARCHIVE
---
CREATE TABLE txn.tip_retention_audit_archive (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,     -- 'retention.document.classified', 'retention.record.reclassified'
    entity_type VARCHAR(50) NOT NULL,      -- 'document', 'record'
    entity_id JSONB NOT NULL,              -- Stores primary keys dynamically
    schema_name VARCHAR(100) NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,                -- Contains context metadata
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM'
);

---
--- 3. STORY SYSTEM CORE TARGET TABLES
---

-- US-1.13-Lean Document Targets
CREATE TABLE content_manager.documents (
    cm_document_id UUID PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    sub_category_id UUID NOT NULL REFERENCES txn.tip_retention_sub_categories(id),
    basis_date DATE NOT NULL,
    filename VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    eligibility_date DATE NOT NULL,
    retention_evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100)
);

-- US-1.24-Lean Sample Pattern B Operational Table
CREATE TABLE examination_workflow.examination_findings (
    id UUID PRIMARY KEY,
    examination_id VARCHAR(50) NOT NULL,
    finding_text TEXT NOT NULL,
    finding_closed_at DATE NOT NULL, -- Target Basis Date Column
    
    -- Mandatory Retention Pattern B Columns
    sub_category_id UUID REFERENCES txn.tip_retention_sub_categories(id),
    eligibility_date DATE,
    retention_evaluated_at TIMESTAMPTZ,
    
    -- standard audit tracking columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100)
);

---
--- 4. PL/pgSQL PLUGINS, CORE LOGIC AND TRIGGERS
---

-- Internal Date Calculation Helper
CREATE OR REPLACE FUNCTION txn.tip_compute_eligibility_date(
    basis_date DATE, 
    duration_val INT, 
    duration_unit VARCHAR
) RETURNS DATE AS $$
BEGIN
    IF basis_date IS NULL THEN
        RETURN NULL;
    END IF;
    
    CASE LOWER(duration_unit)
        WHEN 'days' THEN RETURN basis_date + (duration_val || ' days')::INTERVAL;
        WHEN 'months' THEN RETURN basis_date + (duration_val || ' months')::INTERVAL;
        WHEN 'years' THEN RETURN basis_date + (duration_val || ' years')::INTERVAL;
        ELSE RAISE EXCEPTION 'Invalid retention unit: %', duration_unit;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- Unified Auditing Pipeline Engine Function
CREATE OR REPLACE FUNCTION txn.tip_retention_emit_audit(
    p_event_type VARCHAR, p_entity_type VARCHAR, p_entity_id JSONB,
    p_schema_name VARCHAR, p_table_name VARCHAR, p_payload JSONB
) RETURNS VOID AS $$
DECLARE
    v_actor VARCHAR;
BEGIN
    -- Pull contextual GUC variables if manual correction overrides run, fallback to system
    BEGIN
        v_actor := COALESCE(current_setting('tip.actor_user_id', true), 'SYSTEM');
    EXCEPTION WHEN OTHERS THEN
        v_actor := 'SYSTEM';
    END;

    INSERT INTO txn.tip_retention_audit_archive (
        event_type, entity_type, entity_id, schema_name, table_name, payload, created_by
    ) VALUES (
        p_event_type, p_entity_type, p_entity_id, p_schema_name, p_table_name, p_payload, v_actor
    );
END;
$$ LANGUAGE plpgsql;

-- Pattern B Shared Database Trigger Function
CREATE OR REPLACE FUNCTION txn.tip_retention_classify_pattern_b()
RETURNS TRIGGER AS $$
DECLARE
    v_reg RECORD;
    v_sub RECORD;
    v_effective_sc_id UUID;
    v_basis_date DATE;
    v_computed_eligibility DATE;
    v_pk_json JSONB;
    v_pk_col VARCHAR;
    v_rec_reason TEXT;
BEGIN
    -- 1. Fetch registry configuration profile dynamically based on metadata execution context
    SELECT * INTO v_reg 
    FROM txn.tip_operational_table_registrations
    WHERE schema_name = TG_TABLE_SCHEMA AND table_name = TG_TABLE_NAME;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Table %.% is not registered for Pattern B Retention.', TG_TABLE_SCHEMA, TG_TABLE_NAME;
    END IF;

    -- 2. Resolve Effective Sub-Category using COALESCE rule (Explicit Input vs Registry Default Configuration)
    v_effective_sc_id := COALESCE(NEW.sub_category_id, v_reg.default_sub_category_id);
    IF v_effective_sc_id IS NULL THEN
        RAISE EXCEPTION 'No sub-category provided and no default registry configuration exists.' USING ERRCODE = '23502';
    END IF;

    -- 3. Run Hard Validation Assertions (Taxonomy Existence, Activation status safeguards)
    SELECT sc.*, c.status AS cat_status INTO v_sub
    FROM txn.tip_retention_sub_categories sc
    JOIN txn.tip_categories c ON sc.category_id = c.id
    WHERE sc.id = v_effective_sc_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Sub-Category UUID % does not exist.', v_effective_sc_id USING ERRCODE = '23503';
    END IF;

    IF v_sub.status != 'active' OR v_sub.cat_status != 'active' THEN
        RAISE EXCEPTION 'Sub-Category or its parent category is inactive/draft.' USING ERRCODE = '23514';
    END IF;

    -- 4. Dynamic Extractions of Basis Date Column
    EXECUTE format('SELECT ($1).%I', v_reg.basis_date_column) USING NEW INTO v_basis_date;

    -- Handle edge case: short-circuit logic if incoming row contains NULL basis date values
    IF v_basis_date IS NULL THEN
        NEW.sub_category_id := v_effective_sc_id;
        NEW.eligibility_date := NULL;
        NEW.retention_evaluated_at := NOW();
        RETURN NEW;
    END IF;

    -- 5. Complete Date Computation Framework and Mutate Payload Object Rows
    v_computed_eligibility := txn.tip_compute_eligibility_date(
        v_basis_date, v_sub.retention_duration_value, v_sub.retention_duration_unit
    );

    NEW.sub_category_id := v_effective_sc_id;
    NEW.eligibility_date := v_computed_eligibility;
    NEW.retention_evaluated_at := NOW();

    -- 6. Capture Composite/Single Primary Keys dynamically into JSONB Block via execution mappings
    v_pk_json := '{}'::jsonb;
    FOREACH v_pk_col IN ARRAY v_reg.primary_key_columns LOOP
        DECLARE
            v_val_text TEXT;
        BEGIN
            EXECUTE format('SELECT (($1).%I)::text', v_pk_col) USING NEW INTO v_val_text;
            v_pk_json := v_pk_json || jsonb_build_object(v_pk_col, v_val_text);
        END;
    END LOOP;

    -- Update Taxonomy lineage stateful parameters safely inside runtime transaction boundaries
    UPDATE txn.tip_retention_sub_categories SET has_ever_held_content = TRUE WHERE id = v_effective_sc_id;
    UPDATE txn.tip_categories SET has_ever_held_content = TRUE WHERE id = v_sub.category_id;

    -- 7. Fire Audit Archive Engine pipelines safely
    BEGIN
        v_rec_reason := current_setting('tip.reclassification_reason', true);
    EXCEPTION WHEN OTHERS THEN
        v_rec_reason := NULL;
    END;

    PERFORM txn.tip_retention_emit_audit(
        'retention.document.classified',
        'record',
        v_pk_json,
        TG_TABLE_SCHEMA,
        TG_TABLE_NAME,
        jsonb_build_object(
            'sub_category_id', v_effective_sc_id,
            'basis_date', v_basis_date,
            'eligibility_date', v_computed_eligibility,
            'reclassification_reason', v_rec_reason
        )
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Bind Pattern B Trigger Hook definitions directly to active entity targets
CREATE TRIGGER trg_examination_findings_classify
BEFORE INSERT ON examination_workflow.examination_findings
FOR EACH ROW EXECUTE FUNCTION txn.tip_retention_classify_pattern_b();