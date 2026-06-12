-- =============================================================
-- TIP CM Retention Lean MVP v2  –  Functions & Triggers
-- CONTROLLED // FDIC INTERNAL ONLY
--
-- The DB trigger handles Pattern B atomically at INSERT time:
--   1. Read registry to get basis_date_column and default sub_category_id
--   2. Extract basis_date from the new row
--   3. Look up sub_category to get retention duration
--   4. Compute eligibility_date
--   5. Stamp three columns on NEW row (BEFORE INSERT)
--   6. Write audit event (AFTER INSERT)
--
-- Pattern A (documents): handled by Spring Boot service
-- =============================================================

SET search_path = tip, public;

-- ── Helper: compute eligibility_date ───────────────────────────────────────

CREATE OR REPLACE FUNCTION tip.fn_compute_eligibility_date(
    p_basis_date             DATE,
    p_retention_duration_value SMALLINT,
    p_retention_duration_unit  TEXT
)
RETURNS DATE
LANGUAGE sql
IMMUTABLE STRICT
AS $$
    SELECT CASE p_retention_duration_unit
        WHEN 'DAYS'   THEN p_basis_date + (p_retention_duration_value || ' days')::INTERVAL
        WHEN 'MONTHS' THEN p_basis_date + (p_retention_duration_value || ' months')::INTERVAL
        WHEN 'YEARS'  THEN p_basis_date + (p_retention_duration_value || ' years')::INTERVAL
    END::DATE;
$$;

-- ── Helper: set updated_at ────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION tip.fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END;
$$;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'retention_category','retention_sub_category',
        'operational_table_registry','cm_documents'
    ] LOOP
        BEGIN
            EXECUTE format(
                'CREATE TRIGGER trg_%s_updated_at
                 BEFORE UPDATE ON tip.%I
                 FOR EACH ROW EXECUTE FUNCTION tip.fn_set_updated_at()',
                t, t);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
    END LOOP;
END; $$;

-- =============================================================
-- PATTERN B TRIGGER  (fires at INSERT time on upstream table)
-- =============================================================

/**
 * Called dynamically by fn_install_pattern_b_trigger() to generate and install
 * a trigger function on an upstream operational table.
 *
 * Accepts dynamic parameters:
 *   p_registration_id   – UUID of the registry row (the trigger reads it at runtime)
 *   p_basis_date_column – name of the column in the upstream table that holds the date
 *   p_schema            – schema of the upstream table
 *   p_table             – name of the upstream table
 *
 * The trigger fires BEFORE INSERT and:
 *   1. Reads tip_operational_table_registry row for THIS table
 *   2. Extracts basis_date from NEW row using the configured column name
 *   3. Resolves sub_category (per-row override COALESCE default)
 *   4. Computes eligibility_date
 *   5. Stamps NEW.eligibility_date, NEW.has_ever_held_content, NEW.classification_pattern
 *   6. Returns NEW (BEFORE INSERT completes)
 *
 * After the INSERT succeeds, an AFTER INSERT trigger writes the audit event.
 */
CREATE OR REPLACE FUNCTION tip.fn_install_pattern_b_trigger(
    p_registration_id    UUID,
    p_basis_date_column  TEXT,
    p_schema             TEXT,
    p_table              TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_fn_name   TEXT := 'tip.fn_ret_b_' || replace(p_schema, '-', '_') || '_' ||
                        replace(p_table, '-', '_');
    v_trg_name  TEXT := 'trg_ret_b_' || replace(p_schema, '-', '_') || '_' ||
                        replace(p_table, '-', '_');
    v_sql       TEXT;
BEGIN
    -- Generate the BEFORE INSERT trigger function
    v_sql := format($FN$
        CREATE OR REPLACE FUNCTION %s()
        RETURNS TRIGGER LANGUAGE plpgsql AS $BODY$
        DECLARE
            v_reg        tip.operational_table_registry%%ROWTYPE;
            v_sub        tip.retention_sub_category%%ROWTYPE;
            v_cat        tip.retention_category%%ROWTYPE;
            v_basis      DATE;
            v_elig       DATE;
            v_eff_sc     UUID;
        BEGIN
            -- Load registry row (at runtime – allows changes without reinstalling trigger)
            SELECT * INTO STRICT v_reg
            FROM   tip.operational_table_registry
            WHERE  id = %L AND is_active = TRUE;

            -- Extract basis_date from the new row using the configured column
            v_basis := (SELECT (to_jsonb(NEW)->>%L)::DATE);

            -- Effective sub_category: row-level override COALESCE default
            BEGIN
                v_eff_sc := COALESCE(
                    (to_jsonb(NEW)->>$field$sub_category_id$field$)::UUID,
                    v_reg.default_sub_category_id
                );
            EXCEPTION WHEN OTHERS THEN
                v_eff_sc := v_reg.default_sub_category_id;
            END;

            -- Validate sub_category (must be active AND classification_allowed)
            SELECT * INTO v_sub
            FROM   tip.retention_sub_category
            WHERE  id = v_eff_sc
              AND  is_active = TRUE
              AND  classification_allowed = TRUE;

            IF NOT FOUND THEN
                RAISE EXCEPTION
                    $msg$TIP retention: Sub-Category %% is inactive or classification_allowed=false (table %%.%%)$msg$,
                    v_eff_sc, %L, %L
                USING ERRCODE = '23514';
            END IF;

            -- Compute eligibility_date
            v_elig := tip.fn_compute_eligibility_date(
                v_basis,
                v_sub.retention_duration_value,
                v_sub.retention_duration_unit::TEXT
            );

            -- Look up category for audit
            SELECT * INTO v_cat FROM tip.retention_category WHERE id = v_sub.category_id;

            -- Stamp the three columns on NEW before INSERT
            NEW.eligibility_date          := v_elig;
            NEW.has_ever_held_content     := TRUE;
            NEW.classification_pattern    := 'B'::tip.classification_pattern;

            -- Store the context in session variables so the AFTER trigger can access it
            PERFORM set_config('tip.last_sub_cat_code',    v_sub.code, FALSE);
            PERFORM set_config('tip.last_category_code',   v_cat.code, FALSE);
            PERFORM set_config('tip.last_basis_date',      v_basis::TEXT, FALSE);
            PERFORM set_config('tip.last_eligibility_date', v_elig::TEXT, FALSE);
            PERFORM set_config('tip.last_duration_value',  v_sub.retention_duration_value::TEXT, FALSE);
            PERFORM set_config('tip.last_duration_unit',   v_sub.retention_duration_unit::TEXT, FALSE);

            RETURN NEW;
        END;
        $BODY$;
    $FN$,
        v_fn_name,
        p_registration_id,
        p_basis_date_column,
        p_schema, p_table
    );

    EXECUTE v_sql;

    -- Create the BEFORE INSERT trigger
    EXECUTE format(
        'CREATE TRIGGER %I BEFORE INSERT ON %I.%I
         FOR EACH ROW EXECUTE FUNCTION %s()',
        v_trg_name, p_schema, p_table, v_fn_name
    );

    RETURN v_trg_name;
END;
$$;

-- ── AFTER INSERT trigger: write audit event ────────────────────────────────

/**
 * Fires AFTER INSERT on the upstream table.
 * Reads context from session variables set by the BEFORE trigger.
 * Writes one row to tip_retention_audit_archive.
 */
CREATE OR REPLACE FUNCTION tip.fn_after_pattern_b_classify()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_pk_str TEXT;
BEGIN
    -- Extract PK value as a string (works for any PK type)
    v_pk_str := (to_jsonb(NEW) ->> (
        SELECT a.attname
        FROM   pg_index i
        JOIN   pg_attribute a ON a.attrelid = i.indrelid
                             AND a.attnum = ANY(i.indkey)
        WHERE  i.indrelid = (TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME)::regclass
          AND  i.indisprimary
        LIMIT 1
    ))::TEXT;

    -- Write audit event using context from session variables
    INSERT INTO tip.retention_audit_archive (
        event_type, classification_pattern,
        entity_type, entity_id, table_schema, table_name,
        module_code, category_code, sub_category_code,
        basis_date, eligibility_date,
        retention_duration_value, retention_duration_unit,
        has_ever_held_content, occurred_at, performed_by
    ) VALUES (
        'RECORD_CLASSIFIED'::tip.audit_event_type,
        'B'::tip.classification_pattern,
        'record', v_pk_str, TG_TABLE_SCHEMA, TG_TABLE_NAME,
        current_user,
        current_setting('tip.last_category_code'),
        current_setting('tip.last_sub_cat_code'),
        current_setting('tip.last_basis_date')::DATE,
        current_setting('tip.last_eligibility_date')::DATE,
        current_setting('tip.last_duration_value')::SMALLINT,
        current_setting('tip.last_duration_unit'),
        TRUE, now(), current_user
    );

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION tip.fn_compute_eligibility_date IS
    'Pure computation: basis_date + retention duration. No side effects.';

COMMENT ON FUNCTION tip.fn_install_pattern_b_trigger IS
    'Dynamically generates and installs BEFORE and AFTER INSERT triggers on an upstream table.
     Called once at table registration time.
     The BEFORE trigger computes and stamps eligibility_date; AFTER writes audit.
     All atomic with the upstream INSERT.';
