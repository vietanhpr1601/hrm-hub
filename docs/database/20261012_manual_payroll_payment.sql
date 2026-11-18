-- PostgreSQL: additive payroll snapshots and recorded full-net settlements.
-- Run before deploying the manual payroll workflow if schema auto-update is disabled.
-- Historical PAID records keep their status. Do not invent payment dates,
-- amounts, methods, references or operators for those historical records.
BEGIN;

ALTER TABLE tbl_payslip
    ADD COLUMN IF NOT EXISTS standard_work_days double precision,
    ADD COLUMN IF NOT EXISTS paid_date date,
    ADD COLUMN IF NOT EXISTS paid_amount double precision,
    ADD COLUMN IF NOT EXISTS payment_method varchar(255),
    ADD COLUMN IF NOT EXISTS payment_reference varchar(255),
    ADD COLUMN IF NOT EXISTS paid_by varchar(100);

ALTER TABLE tbl_payslip_item
    ADD COLUMN IF NOT EXISTS calculation_type varchar(255),
    ADD COLUMN IF NOT EXISTS item_type varchar(255);

-- Nullable snapshots preserve old rows. An unknown standard-work-day snapshot
-- stays NULL in API responses; historical confirmed amounts are not recalculated.
-- Legacy drafts require explicit recalculation before confirmation.
-- New/edited/recalculated draft line results round HALF_UP to whole VND,
-- and the full settlement records the sum of those rounded line amounts.
-- New flows support FIXED and BY_STANDARD_DAYS only. DAILY_MULTIPLIED enum
-- values remain for historical records; legacy draft lines require an explicit
-- formula edit before recalculation or confirmation. Never rewrite old amounts.
COMMIT;
