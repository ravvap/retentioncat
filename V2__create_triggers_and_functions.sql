-- =============================================================
-- TIP CM Retention – Lean MVP
-- V2: Retention functions and triggers
-- CONTROLLED//FDIC INTERNAL ONLY
-- =============================================================

-- ---------------------------------------------------------------
-- Helper: tip_compute_eligibility_date
--   Computes basis_date + retention_duration, returning NULL
--   when basis_date is NULL (NULL-basis-date short-circuit).
--   legal_hold_days and legal_hold_end_date reserved for R2.
-- ---------------------------------------------------------------
CREATE OR REPLACE FUNCTION tip_compute_eligibility_date(
    p_basis_date            DATE,
    p_retention_value       INTEGER,
    p_retention_unit        VARCHAR,
    p_legal_hold_days       INTEGER DEFAULT 0,
    p_legal_hold_end_date   DATE    DEFAULT NULL
)
RETURNS DATE
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF p_basis_date IS NULL THEN
        RETURN NULL;   -- NULL short-circuit; caller should monitor these rows
    END IF;

    RETURN p_basis_date +
        CASE p_retention_unit
            WHEN 'days'   THEN MAKE_INTERVAL(days   => p_retention_value)
            WHEN 'months' THEN MAKE_INTERVAL(months => p_retention_value)
            WHEN 'years'  THEN MAKE_INTERVAL(years  => p_retention_value)
            ELSE RAISE EXCEPTION 'Unknown retention unit: %', p_retention_unit
        END;
END;
$$;

-- ---------------------------------------------------------------
-- Helper: tip_retention_emit_audit
--   Writes one row to tip_retention_audit_archive.
--   Called by triggers; actor/correlation come from GUC session vars.
-- ---------------------------------------------------------------
CREATE OR REPLACE FUNCTION tip_retention_emit_audit(
    p_event_type        VARCHAR,
    p_entity_type       VARCHAR,
    p_entity_id         JSONB,
    p_sub_category_id   UUID,
    p_eligibility_date  DATE,
    p_basis_date        DATE,
    p_source_system     VARCHAR    DEFAULT NULL,
    p_source_reference  VARCHAR    DEFAULT NULL,
    p_schema_name       VARCHAR    DEFAULT NULL,
    p_table_name        VARCHAR    DEFAULT NULL,
    p_payload           JSONB      DEFAULT '{}'
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_actor         VARCHAR := current_setting('tip.actor_user_id',   TRUE);
    v_correlation   UUID    := NULLIF(current_setting('tip.correlation_id', TRUE), '')::UUID;
BEGIN
    INSERT INTO tip_retention_audit_archive (
        event_type, entity_type, entity_id,
        schema_name, table_name,
        sub_category_id, eligibility_date, basis_date,
        source_system, source_reference,
        payload, actor_user_id, correlation_id,
        created_by, updated_by
    ) VALUES (
        p_event_type, p_entity_type, p_entity_id,
        p_schema_name, p_table_name,
        p_sub_category_id, p_eligibility_date, p_basis_date,
        p_source_system, p_source_reference,
        p_payload, v_actor, v_correlation,
        COALESCE(v_actor, 'trigger'), COALESCE(v_actor, 'trigger')
    );
END;
$$;

-- ---------------------------------------------------------------
-- Trigger function: tip_retention_classify_document
--   Fires BEFORE INSERT on content_manager.documents (US-1.13-Lean).
--   Validates sub-category, computes eligibility date, flips
--   has_ever_held_content flags, emits audit event.
-- ---------------------------------------------------------------
CREATE OR REPLACE FUNCTION tip_retention_classify_document()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_sub_cat   RECORD;
    v_cat       RECORD;
    v_elig_date DATE;
BEGIN
    -- Fetch sub-category and its parent (validates existence + status)
    SELECT sc.id, sc.status, sc.retention_duration_value, sc.retention_duration_unit,
           sc.has_ever_held_content, sc.category_id
      INTO v_sub_cat
      FROM tip_retention_sub_categories sc
     WHERE sc.id = NEW.sub_category_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION SQLSTATE '23503'
            USING MESSAGE = 'sub_category_id does not reference a known Sub-Category: ' || NEW.sub_category_id;
    END IF;

    SELECT c.id, c.status, c.has_ever_held_content
      INTO v_cat
      FROM tip_retention_categories c
     WHERE c.id = v_sub_cat.category_id;

    IF v_sub_cat.status <> 'active' THEN
        RAISE EXCEPTION SQLSTATE '23514'
            USING MESSAGE = 'Sub-Category is not active: ' || NEW.sub_category_id;
    END IF;

    IF v_cat.status <> 'active' THEN
        RAISE EXCEPTION SQLSTATE '23514'
            USING MESSAGE = 'Parent Category is not active for sub_category_id: ' || NEW.sub_category_id;
    END IF;

    -- Compute eligibility date
    v_elig_date := tip_compute_eligibility_date(
        NEW.basis_date,
        v_sub_cat.retention_duration_value,
        v_sub_cat.retention_duration_unit,
        0, NULL
    );

    NEW.eligibility_date       := v_elig_date;
    NEW.retention_evaluated_at := NOW();

    -- Flip has_ever_held_content on Sub-Category if this is the first document
    IF NOT v_sub_cat.has_ever_held_content THEN
        UPDATE tip_retention_sub_categories
           SET has_ever_held_content = TRUE,
               updated_at = NOW(),
               updated_by = COALESCE(current_setting('tip.actor_user_id', TRUE), 'trigger')
         WHERE id = v_sub_cat.id;
    END IF;

    -- Flip has_ever_held_content on Category if needed
    IF NOT v_cat.has_ever_held_content THEN
        UPDATE tip_retention_categories
           SET has_ever_held_content = TRUE,
               updated_at = NOW(),
               updated_by = COALESCE(current_setting('tip.actor_user_id', TRUE), 'trigger')
         WHERE id = v_cat.id;
    END IF;

    -- Emit audit event
    PERFORM tip_retention_emit_audit(
        'retention.document.classified',
        'document',
        jsonb_build_object('id', NEW.id),
        NEW.sub_category_id,
        v_elig_date,
        NEW.basis_date,
        NEW.source_system,
        NEW.source_reference,
        'content_manager',
        'documents',
        jsonb_build_object(
            'sub_category_id',   NEW.sub_category_id,
            'eligibility_date',  v_elig_date,
            'basis_date',        NEW.basis_date,
            'source_system',     NEW.source_system,
            'source_reference',  NEW.source_reference,
            'filename',          NEW.filename
        )
    );

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cm_documents_classify
    BEFORE INSERT ON content_manager.documents
    FOR EACH ROW EXECUTE FUNCTION tip_retention_classify_document();

-- ---------------------------------------------------------------
-- Trigger function: tip_retention_classify_pattern_b
--   Generic BEFORE INSERT trigger for Pattern B operational tables.
--   Reads its configuration from tip_operational_table_registrations
--   at runtime using TG_TABLE_SCHEMA and TG_TABLE_NAME.
-- ---------------------------------------------------------------
CREATE OR REPLACE FUNCTION tip_retention_classify_pattern_b()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_reg           RECORD;
    v_sub_cat       RECORD;
    v_cat           RECORD;
    v_effective_id  UUID;
    v_basis_date    DATE;
    v_elig_date     DATE;
    v_pk_json       JSONB;
BEGIN
    -- Load registry row for this table
    SELECT *
      INTO v_reg
      FROM tip_operational_table_registrations
     WHERE schema_name = TG_TABLE_SCHEMA
       AND table_name  = TG_TABLE_NAME;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Table %.% is not registered for Pattern B retention',
            TG_TABLE_SCHEMA, TG_TABLE_NAME;
    END IF;

    -- Effective sub-category: COALESCE(caller-supplied, registry default)
    -- NEW.sub_category_id may be NULL if the calling service didn't set it
    BEGIN
        v_effective_id := COALESCE(
            (NEW::text::jsonb)->>'sub_category_id',
            v_reg.default_sub_category_id::text
        )::UUID;
    EXCEPTION WHEN others THEN
        v_effective_id := v_reg.default_sub_category_id;
    END;

    IF v_effective_id IS NULL THEN
        RAISE EXCEPTION SQLSTATE '23502'
            USING MESSAGE = 'Could not resolve effective sub_category_id for ' ||
                            TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME ||
                            ' – registry default_sub_category_id is NULL';
    END IF;

    -- Validate sub-category and category
    SELECT sc.id, sc.status, sc.retention_duration_value, sc.retention_duration_unit,
           sc.has_ever_held_content, sc.category_id
      INTO v_sub_cat
      FROM tip_retention_sub_categories sc
     WHERE sc.id = v_effective_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION SQLSTATE '23503'
            USING MESSAGE = 'Effective sub_category_id does not exist: ' || v_effective_id;
    END IF;

    SELECT c.id, c.status, c.has_ever_held_content
      INTO v_cat
      FROM tip_retention_categories c
     WHERE c.id = v_sub_cat.category_id;

    IF v_sub_cat.status <> 'active' THEN
        RAISE EXCEPTION SQLSTATE '23514'
            USING MESSAGE = 'Sub-Category is not active: ' || v_effective_id;
    END IF;

    IF v_cat.status <> 'active' THEN
        RAISE EXCEPTION SQLSTATE '23514'
            USING MESSAGE = 'Parent Category is not active: sub_category_id=' || v_effective_id;
    END IF;

    -- Extract basis date from the row using the registered column name
    EXECUTE format('SELECT ($1).%I::date', v_reg.basis_date_column) INTO v_basis_date USING NEW;

    -- Compute eligibility date
    v_elig_date := tip_compute_eligibility_date(
        v_basis_date,
        v_sub_cat.retention_duration_value,
        v_sub_cat.retention_duration_unit,
        0, NULL
    );

    -- Stamp retention columns onto the new row
    NEW := NEW #= hstore(ARRAY[
        ['sub_category_id',        v_effective_id::text],
        ['eligibility_date',       v_elig_date::text],
        ['retention_evaluated_at', NOW()::text]
    ]);

    -- Flip has_ever_held_content
    IF NOT v_sub_cat.has_ever_held_content THEN
        UPDATE tip_retention_sub_categories
           SET has_ever_held_content = TRUE,
               updated_at = NOW(),
               updated_by = COALESCE(current_setting('tip.actor_user_id', TRUE), 'trigger')
         WHERE id = v_sub_cat.id;
    END IF;

    IF NOT v_cat.has_ever_held_content THEN
        UPDATE tip_retention_categories
           SET has_ever_held_content = TRUE,
               updated_at = NOW(),
               updated_by = COALESCE(current_setting('tip.actor_user_id', TRUE), 'trigger')
         WHERE id = v_cat.id;
    END IF;

    -- Build composite PK JSONB for audit
    SELECT jsonb_object_agg(col, (NEW::text::jsonb)->>col)
      INTO v_pk_json
      FROM unnest(v_reg.primary_key_columns) AS col;

    -- Emit audit event
    PERFORM tip_retention_emit_audit(
        'retention.record.classified',
        'record',
        v_pk_json,
        v_effective_id,
        v_elig_date,
        v_basis_date,
        NULL, NULL,
        TG_TABLE_SCHEMA,
        TG_TABLE_NAME,
        jsonb_build_object(
            'sub_category_id',  v_effective_id,
            'eligibility_date', v_elig_date,
            'basis_date',       v_basis_date
        )
    );

    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------
-- Helper: tip_admin_correct_classification
--   Manual correction procedure (Appendix A.3).
--   Wraps the three-step UPDATE + audit emit into one call.
-- ---------------------------------------------------------------
CREATE OR REPLACE PROCEDURE tip_admin_correct_classification(
    p_schema            VARCHAR,
    p_table             VARCHAR,
    p_pk_column         VARCHAR,
    p_pk_value          TEXT,
    p_new_sub_cat_uuid  UUID,
    p_reason            TEXT,
    p_actor_user_id     VARCHAR DEFAULT NULL,
    p_correlation_id    UUID    DEFAULT gen_random_uuid()
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_sub_cat_id    UUID;
    v_old_elig_date     DATE;
    v_old_basis_date    DATE;
    v_new_sub_cat       RECORD;
    v_new_elig_date     DATE;
BEGIN
    -- Set GUC session variables
    PERFORM set_config('tip.actor_user_id',          COALESCE(p_actor_user_id, 'dba'), TRUE);
    PERFORM set_config('tip.correlation_id',         p_correlation_id::text, TRUE);
    PERFORM set_config('tip.reclassification_reason', p_reason, TRUE);

    -- Read current state
    EXECUTE format(
        'SELECT sub_category_id, eligibility_date, basis_date FROM %I.%I WHERE %I = $1',
        p_schema, p_table, p_pk_column
    ) INTO v_old_sub_cat_id, v_old_elig_date, v_old_basis_date
    USING p_pk_value;

    -- Lookup new sub-category
    SELECT id, retention_duration_value, retention_duration_unit
      INTO v_new_sub_cat
      FROM tip_retention_sub_categories
     WHERE id = p_new_sub_cat_uuid AND status = 'active';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'New sub_category_id % is not active or does not exist', p_new_sub_cat_uuid;
    END IF;

    v_new_elig_date := tip_compute_eligibility_date(
        v_old_basis_date,
        v_new_sub_cat.retention_duration_value,
        v_new_sub_cat.retention_duration_unit,
        0, NULL
    );

    -- UPDATE source row
    EXECUTE format(
        'UPDATE %I.%I SET sub_category_id = $1, eligibility_date = $2, '
        'retention_evaluated_at = NOW(), updated_at = NOW(), updated_by = $3 '
        'WHERE %I = $4',
        p_schema, p_table, p_pk_column
    ) USING p_new_sub_cat_uuid, v_new_elig_date,
            COALESCE(p_actor_user_id, 'dba'), p_pk_value;

    -- Emit audit event
    PERFORM tip_retention_emit_audit(
        'retention.record.reclassified',
        'record',
        jsonb_build_object(p_pk_column, p_pk_value),
        p_new_sub_cat_uuid,
        v_new_elig_date,
        v_old_basis_date,
        NULL, NULL,
        p_schema,
        p_table,
        jsonb_build_object(
            'prior_sub_category_id',  v_old_sub_cat_id,
            'new_sub_category_id',    p_new_sub_cat_uuid,
            'prior_eligibility_date', v_old_elig_date,
            'new_eligibility_date',   v_new_elig_date,
            'reason',                 p_reason,
            'cause',                  'manual_correction'
        )
    );
END;
$$;
