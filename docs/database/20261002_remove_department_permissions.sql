-- Phan quyen chi con nhom vai tro va ngoai le theo tai khoan.
-- Dung Backend cu va sao luu database truoc khi ap dung.
-- Khong xoa phong ban, nhan vien, tai khoan hay quyen vai tro/tai khoan.
BEGIN;
SET LOCAL lock_timeout = '5s';
DROP TABLE IF EXISTS public.tbl_department_permission;
COMMIT;
