-- ============================================================
-- TIP CM Retention Lean MVP  –  Functions & Triggers
-- CONTROLLED // FDIC INTERNAL ONLY
-- ============================================================
SET search_path = tip, public;

-- ============================================================
-- HELPER: compute retention date
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_compute_retention_date(
    p_basis_date        DATE,
    p_retention_years   SMALLINT
)
RETURNS DATE
LANGUAGE sql
IMMUTABLE STRICT
AS $$
    SELECT (p_basis_date + (p_retention_years || ' years')::INTERVAL)::DATE;
$$;

COMMENT ON FUNCTION tip.fn_compute_retention_date IS
    'Adds the bucket retention period (years) to the supplied basis date.';

-- ============================================================
-- AUDIT HELPER: write one event row
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_write_audit_event(
    p_event_type            tip.audit_event_type,
    p_retention_record_id   UUID,
    p_auto_class_id         UUID,
    p_module_code           VARCHAR,
    p_upstream_reference    VARCHAR,
    p_bucket_code           VARCHAR,
    p_basis_date            DATE,
    p_retention_date        DATE,
    p_detail                JSONB,
    p_performed_by          VARCHAR
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    v_id UUID := uuid_generate_v4();
BEGIN
    INSERT INTO tip.audit_event (
        id, event_type,
        retention_record_id, auto_classification_id,
        upstream_module_code, upstream_reference,
        retention_bucket_code,
        basis_date, retention_date,
        event_detail, occurred_at, performed_by
    ) VALUES (
        v_id, p_event_type,
        p_retention_record_id, p_auto_class_id,
        p_module_code, p_upstream_reference,
        p_bucket_code,
        p_basis_date, p_retention_date,
        p_detail, now(), p_performed_by
    );
    RETURN v_id;
END;
$$;

-- ============================================================
-- AUDIT TRAIL – generic updated_at setter (used by updatable tables)
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- Attach to every mutable table
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'upstream_module','retention_bucket',
        'retention_record','table_onboarding','auto_classification'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON tip.%I
             FOR EACH ROW EXECUTE FUNCTION tip.fn_set_updated_at()',
            t, t
        );
    END LOOP;
END;
$$;

-- ============================================================
-- STORY 1 TRIGGER: after a retention_record is inserted,
--   write a DOCUMENT_PROMOTED audit event automatically.
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_audit_promotion()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_module_code   VARCHAR(50);
    v_bucket_code   VARCHAR(50);
BEGIN
    SELECT module_code INTO v_module_code
    FROM   tip.upstream_module
    WHERE  id = NEW.upstream_module_id;

    SELECT bucket_code INTO v_bucket_code
    FROM   tip.retention_bucket
    WHERE  id = NEW.retention_bucket_id;

    PERFORM tip.fn_write_audit_event(
        'DOCUMENT_PROMOTED'::tip.audit_event_type,
        NEW.id,
        NULL,
        v_module_code,
        NEW.upstream_reference,
        v_bucket_code,
        NEW.basis_date,
        NEW.retention_date,
        jsonb_build_object('blob_storage_uri', NEW.blob_storage_uri),
        NEW.created_by
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_retention_record_audit
    AFTER INSERT ON tip.retention_record
    FOR EACH ROW EXECUTE FUNCTION tip.fn_audit_promotion();

-- ============================================================
-- STORY 2 TRIGGER: after an auto_classification is inserted,
--   write an AUTO_CLASSIFIED audit event automatically.
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_audit_auto_classification()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_module_code   VARCHAR(50);
    v_bucket_code   VARCHAR(50);
    v_schema        VARCHAR(100);
    v_table         VARCHAR(100);
BEGIN
    SELECT um.module_code, to2.schema_name, to2.table_name
    INTO   v_module_code, v_schema, v_table
    FROM   tip.table_onboarding to2
    JOIN   tip.upstream_module   um ON um.id = to2.upstream_module_id
    WHERE  to2.id = NEW.table_onboarding_id;

    SELECT bucket_code INTO v_bucket_code
    FROM   tip.retention_bucket
    WHERE  id = NEW.retention_bucket_id;

    PERFORM tip.fn_write_audit_event(
        'AUTO_CLASSIFIED'::tip.audit_event_type,
        NULL,
        NEW.id,
        v_module_code,
        NEW.upstream_row_id,
        v_bucket_code,
        NEW.basis_date,
        NEW.retention_date,
        jsonb_build_object(
            'schema_name', v_schema,
            'table_name',  v_table
        ),
        NEW.created_by
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auto_classification_audit
    AFTER INSERT ON tip.auto_classification
    FOR EACH ROW EXECUTE FUNCTION tip.fn_audit_auto_classification();

-- ============================================================
-- STORY 2 – GENERIC ONBOARDED-TABLE TRIGGER TEMPLATE
-- This function is dynamically installed per onboarded table
-- by the Spring Boot onboarding service at runtime.
-- The template below shows how it is generated; the Spring
-- service calls tip.fn_install_retention_trigger(…).
-- ============================================================
CREATE OR REPLACE FUNCTION tip.fn_install_retention_trigger(
    p_schema            TEXT,
    p_table             TEXT,
    p_pk_column         TEXT,
    p_basis_date_col    TEXT,
    p_onboarding_id     UUID
)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_fn_name   TEXT := format('tip.fn_ret_%s_%s', p_schema, p_table);
    v_trg_name  TEXT := format('trg_ret_%s_%s', p_schema, p_table);
    v_sql       TEXT;
BEGIN
    -- Create the per-table trigger function
    v_sql := format($BODY$
        CREATE OR REPLACE FUNCTION %s()
        RETURNS TRIGGER
        LANGUAGE plpgsql
        AS $F$
        DECLARE
            v_bucket        tip.retention_bucket%%ROWTYPE;
            v_basis_date    DATE;
            v_ret_date      DATE;
        BEGIN
            -- Fail loudly if onboarding config is broken
            SELECT rb.* INTO STRICT v_bucket
            FROM   tip.table_onboarding  tob
            JOIN   tip.retention_bucket  rb  ON rb.id = tob.retention_bucket_id
            WHERE  tob.id = %L
              AND  tob.is_active = TRUE
              AND  rb.is_active  = TRUE;

            v_basis_date := (NEW.%I)::DATE;
            v_ret_date   := tip.fn_compute_retention_date(v_basis_date, v_bucket.retention_years);

            INSERT INTO tip.auto_classification (
                table_onboarding_id, upstream_row_id,
                retention_bucket_id, basis_date, retention_date,
                created_by
            ) VALUES (
                %L, (NEW.%I)::TEXT,
                v_bucket.id, v_basis_date, v_ret_date,
                'SYSTEM_TRIGGER'
            );
            RETURN NEW;
        END;
        $F$
    $BODY$,
        v_fn_name,
        p_onboarding_id,
        p_basis_date_col,
        p_onboarding_id,
        p_pk_column
    );
    EXECUTE v_sql;

    -- Install the trigger on the upstream table
    EXECUTE format(
        'CREATE TRIGGER %I AFTER INSERT ON %I.%I
         FOR EACH ROW EXECUTE FUNCTION %s()',
        v_trg_name, p_schema, p_table, v_fn_name
    );

    -- Record the trigger name back on the onboarding row
    UPDATE tip.table_onboarding
    SET    trigger_name = v_trg_name,
           updated_at   = now()
    WHERE  id = p_onboarding_id;

    RETURN v_trg_name;
END;
$$;

COMMENT ON FUNCTION tip.fn_install_retention_trigger IS
    'Called once at onboarding time to attach automatic retention classification to an upstream table.';

-- ============================================================
-- VIEWS for auditors (Story 3)
-- ============================================================

-- Dashboard view: all events with human-readable labels
CREATE OR REPLACE VIEW tip.vw_audit_dashboard AS
SELECT
    ae.id               AS event_id,
    ae.event_type,
    ae.upstream_module_code,
    ae.upstream_reference,
    ae.retention_bucket_code,
    rb.retention_years,
    ae.basis_date,
    ae.retention_date,
    ae.occurred_at,
    ae.performed_by,
    ae.event_detail
FROM   tip.audit_event    ae
LEFT   JOIN tip.retention_bucket rb ON rb.bucket_code = ae.retention_bucket_code
ORDER  BY ae.occurred_at DESC;

-- Bucket summary: count of events per bucket per month
CREATE OR REPLACE VIEW tip.vw_bucket_monthly_summary AS
SELECT
    retention_bucket_code,
    date_trunc('month', occurred_at)::DATE  AS month,
    event_type,
    count(*)                                AS event_count
FROM   tip.audit_event
GROUP  BY retention_bucket_code, date_trunc('month', occurred_at), event_type
ORDER  BY month DESC, retention_bucket_code;
