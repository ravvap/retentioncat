-- ============================================================
-- TIP CM Retention Lean MVP  –  Reference / Seed Data
-- CONTROLLED // FDIC INTERNAL ONLY
-- ============================================================
SET search_path = tip, public;

-- Upstream modules (api_key_hash = bcrypt of a placeholder key — replace in prod)
INSERT INTO tip.upstream_module (module_code, module_name, api_key_hash, is_active, created_by)
VALUES
  ('EW',  'Examination Workflow',  crypt('ew-api-key-placeholder',  gen_salt('bf')), TRUE, 'SEED'),
  ('CM',  'Case Management',       crypt('cm-api-key-placeholder',  gen_salt('bf')), TRUE, 'SEED'),
  ('RM',  'Request Manager',       crypt('rm-api-key-placeholder',  gen_salt('bf')), TRUE, 'SEED');

-- Retention buckets (defined by Records Management)
INSERT INTO tip.retention_bucket (bucket_code, bucket_name, retention_years, description, is_active, created_by)
VALUES
  ('EXAM_FINDINGS_25Y',     'Examination Findings',            25, 'Examination findings – 25-year retention per FDIC schedule', TRUE, 'SEED'),
  ('CASE_EVENTS_10Y',       'Case Events',                     10, 'Case management events – 10-year retention',                 TRUE, 'SEED'),
  ('TRANSACTION_LOGS_7Y',   'Transaction Logs',                 7, 'Transaction audit logs – 7-year retention',                  TRUE, 'SEED'),
  ('CORRESPONDENCE_5Y',     'Correspondence',                   5, 'General correspondence – 5-year retention',                  TRUE, 'SEED');
