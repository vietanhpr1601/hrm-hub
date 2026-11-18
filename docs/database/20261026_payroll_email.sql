-- Only needed if Hibernate ddl-auto update is disabled. Additive; no payroll changes.
CREATE TABLE IF NOT EXISTS tbl_payroll_email (
    id UUID PRIMARY KEY,
    payslip_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL DEFAULT 'PAYMENT',
    recipient VARCHAR(255),
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(255) NOT NULL,
    error_message VARCHAR(255),
    sent_at TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    create_date TIMESTAMP,
    modify_date TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    is_deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT uq_payroll_email_event UNIQUE(payslip_id, event_type)
);
CREATE INDEX IF NOT EXISTS idx_payroll_email_pending ON tbl_payroll_email(status, create_date);
