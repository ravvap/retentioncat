-- =============================================================
-- TIP CM Retention Lean MVP v2  –  Seed Data
-- =============================================================
SET search_path = tip, public;

-- Retention Categories
INSERT INTO tip.retention_category (code, name, description, is_active, created_by) VALUES
  ('EXAM_RECORDS',    'Examination Records',       'FDIC examination activities',           TRUE, 'SEED'),
  ('CASE_RECORDS',    'Case Records',              'Case management and enforcement',        TRUE, 'SEED'),
  ('FINANCIAL_TRANS', 'Financial Transactions',    'Wires, payroll, cash reconciliations',  TRUE, 'SEED'),
  ('CORRESPONDENCE',  'Correspondence',            'Internal and external correspondence',  TRUE, 'SEED'),
  ('INVESTMENT',      'Investment Records',        'NLF investments and reporting',         TRUE, 'SEED');

-- Sub-Categories: Examination Records
INSERT INTO tip.retention_sub_category
    (category_id, code, name, retention_duration_value, retention_duration_unit,
     classification_allowed, is_active, created_by)
SELECT c.id, v.code, v.name, v.val, v.unit::tip.duration_unit, TRUE, TRUE, 'SEED'
FROM   tip.retention_category c,
       (VALUES
           ('EXAM_RECORDS', 'EXAM_FINDINGS_25Y',  'Examination Findings',    25, 'YEARS'),
           ('EXAM_RECORDS', 'EXAM_REPORTS_10Y',   'Examination Reports',     10, 'YEARS'),
           ('EXAM_RECORDS', 'EXAM_WORKPAPERS_7Y', 'Examination Workpapers',   7, 'YEARS')
       ) AS v(cat, code, name, val, unit)
WHERE  c.code = v.cat;

-- Sub-Categories: Case Records
INSERT INTO tip.retention_sub_category
    (category_id, code, name, retention_duration_value, retention_duration_unit,
     classification_allowed, is_active, created_by)
SELECT c.id, v.code, v.name, v.val, v.unit::tip.duration_unit, TRUE, TRUE, 'SEED'
FROM   tip.retention_category c,
       (VALUES
           ('CASE_RECORDS', 'CASE_EVENTS_10Y',   'Case Events',              10, 'YEARS'),
           ('CASE_RECORDS', 'CASE_ENFORCE_15Y',  'Enforcement Case Records', 15, 'YEARS')
       ) AS v(cat, code, name, val, unit)
WHERE  c.code = v.cat;

-- Sub-Categories: Financial Transactions
INSERT INTO tip.retention_sub_category
    (category_id, code, name, retention_duration_value, retention_duration_unit,
     classification_allowed, is_active, created_by)
SELECT c.id, v.code, v.name, v.val, v.unit::tip.duration_unit, TRUE, TRUE, 'SEED'
FROM   tip.retention_category c,
       (VALUES
           ('FINANCIAL_TRANS', 'RS5_WIRES',       'RS5 – Wire Transfers',        5, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS5_PAYROLL',     'RS5 – Payroll Records',       5, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS5_CASH_RECONS', 'RS5 – Cash Reconciliations',  5, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS5_CIR',         'RS5 – CIR Records',           5, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS5_FEDLINE',     'RS5 – FedLine Records',       5, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS1_COLLECTIONS', 'RS1 – Collections',           1, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS1_IPAC',        'RS1 – IPAC Records',          1, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS6_INVESTMENTS', 'RS6 – Investments',           6, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS6_NLF_INVEST',  'RS6 – NLF Investments',       6, 'YEARS'),
           ('FINANCIAL_TRANS', 'RS6_INVEST_RPT',  'RS6 – Investment Reporting',  6, 'YEARS')
       ) AS v(cat, code, name, val, unit)
WHERE  c.code = v.cat;

-- Sub-Categories: Correspondence
INSERT INTO tip.retention_sub_category
    (category_id, code, name, retention_duration_value, retention_duration_unit,
     classification_allowed, is_active, created_by)
SELECT c.id, v.code, v.name, v.val, v.unit::tip.duration_unit, TRUE, TRUE, 'SEED'
FROM   tip.retention_category c,
       (VALUES
           ('CORRESPONDENCE', 'CORR_GENERAL_5Y',  'General Correspondence',  5, 'YEARS'),
           ('CORRESPONDENCE', 'CORR_LEGAL_10Y',   'Legal Correspondence',   10, 'YEARS')
       ) AS v(cat, code, name, val, unit)
WHERE  c.code = v.cat;

-- Sub-Categories: Investment Records
INSERT INTO tip.retention_sub_category
    (category_id, code, name, retention_duration_value, retention_duration_unit,
     classification_allowed, is_active, created_by)
SELECT c.id, v.code, v.name, v.val, v.unit::tip.duration_unit, TRUE, TRUE, 'SEED'
FROM   tip.retention_category c,
       (VALUES
           ('INVESTMENT', 'INVEST_PORTFOLIO_7Y', 'Portfolio Investment Records', 7, 'YEARS'),
           ('INVESTMENT', 'INVEST_AUDIT_10Y',    'Investment Audit Records',    10, 'YEARS')
       ) AS v(cat, code, name, val, unit)
WHERE  c.code = v.cat;
