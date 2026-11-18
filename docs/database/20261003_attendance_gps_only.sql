-- Chạy sau khi dừng Backend và sao lưu PostgreSQL.
-- Chỉ bỏ đường dẫn ảnh chấm công; giữ nguyên hồ sơ, công và lương.
BEGIN;
ALTER TABLE public.tbl_check_in_out_record DROP COLUMN IF EXISTS photo_url;
ALTER TABLE public.tbl_timesheet_detail DROP COLUMN IF EXISTS photo_check_in_url;
ALTER TABLE public.tbl_timesheet_detail DROP COLUMN IF EXISTS photo_check_out_url;

-- Hibernate không tự cập nhật CHECK của enum khi nâng cấp dữ liệu hiện có.
DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'public.tbl_leave_request'::regclass AND contype = 'c'
        AND pg_get_constraintdef(oid) LIKE '%leave_type%'
    LOOP
        EXECUTE format('ALTER TABLE public.tbl_leave_request DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE public.tbl_leave_request ADD CONSTRAINT tbl_leave_request_leave_type_check
    CHECK (leave_type IN ('ANNUAL', 'UNPAID', 'MAKEUP'));
COMMIT;
