-- Preserve existing payment emails; allow one message for each event per payslip.
DO $$
DECLARE old_constraint RECORD;
BEGIN
    IF to_regclass('public.tbl_payroll_email') IS NULL THEN RETURN; END IF;
    ALTER TABLE public.tbl_payroll_email ADD COLUMN IF NOT EXISTS event_type VARCHAR(32) NOT NULL DEFAULT 'PAYMENT';
    FOR old_constraint IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.conrelid = 'public.tbl_payroll_email'::regclass AND c.contype = 'u'
            AND cardinality(c.conkey) = 1 AND a.attname = 'payslip_id'
    LOOP
        EXECUTE format('ALTER TABLE public.tbl_payroll_email DROP CONSTRAINT %I', old_constraint.conname);
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.tbl_payroll_email'::regclass AND conname = 'uq_payroll_email_event') THEN
        ALTER TABLE public.tbl_payroll_email ADD CONSTRAINT uq_payroll_email_event UNIQUE(payslip_id, event_type);
    END IF;
END $$;
