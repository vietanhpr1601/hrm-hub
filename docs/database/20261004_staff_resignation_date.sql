-- Additive only. Existing unknown resignation dates remain NULL; modify_date
-- records profile edits and must never be used to invent a resignation date.
ALTER TABLE tbl_staff ADD COLUMN IF NOT EXISTS resignation_date date;
