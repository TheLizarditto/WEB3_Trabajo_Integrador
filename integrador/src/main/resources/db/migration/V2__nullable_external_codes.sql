-- Preserve existing IDs, external codes, relationships, orders and audit history.
-- Unique indexes still reject duplicate non-null keys and allow multiple NULLs.
ALTER TABLE iw3_master MODIFY COLUMN external_code VARCHAR(100) NULL;
ALTER TABLE iw3_order MODIFY COLUMN external_code VARCHAR(100) NULL;
