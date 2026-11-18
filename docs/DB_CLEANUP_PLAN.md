# Danh sách bảng đề nghị xóa — 30/09/2026

> Cập nhật: người dùng đã đồng ý chạy lại dữ liệu mới. Đã lưu DB cũ thành `hrm_archive_20260930033224` và tạo database `hrm` mới với đúng 19 bảng HR. Xem [kết quả thực hiện](DB_RESET_RESULT.md). Danh sách và trạng thái bên dưới là kế hoạch trước khi chuyển sang phương án này.

Trạng thái: chưa xóa bảng nào. Source HR-only hiện dùng 19 bảng; 24 bảng dưới đây không được source sử dụng. Một số bảng cũ vẫn chứa dữ liệu, vì vậy cần xác nhận phạm vi trước khi thực thi.

| Bảng | Số dòng | Nhóm |
| --- | ---: | --- |
| attendances | 2 | HR/tài khoản cũ |
| candidates | 1 | HR/tài khoản cũ |
| departments | 2 | HR/tài khoản cũ |
| employees | 3 | HR/tài khoản cũ |
| job_postings | 2 | HR/tài khoản cũ |
| leave_requests | 3 | HR/tài khoản cũ |
| payroll_periods | 1 | HR/tài khoản cũ |
| payrolls | 2 | HR/tài khoản cũ |
| positions | 1 | HR/tài khoản cũ |
| project_members | 1 | Project/Task |
| projects | 1 | Project/Task |
| roles | 4 | HR/tài khoản cũ |
| task_attachments | 1 | Project/Task |
| task_histories | 3 | Project/Task |
| tasks | 2 | Project/Task |
| tbl_project | 0 | Project/Task |
| tbl_project_activity | 0 | Project/Task |
| tbl_project_staff | 0 | Project/Task |
| tbl_project_working_status | 0 | Project/Task |
| tbl_task | 0 | Project/Task |
| tbl_task_attachment | 0 | Project/Task |
| tbl_task_history | 0 | Project/Task |
| uploaded_files | 0 | Tệp cũ, đang rỗng |
| users | 3 | HR/tài khoản cũ |

## Sao lưu đã hoàn tất

File cục bộ: D:\Project\Hrm\.runtime\db-cleanup\hrm-before-cleanup-2026-09-30T02-52-50-404Z.dump

SHA-256: 47de6326b9c5a8f3d356643859c341b05d19c423e779591831e67d9bd3cbc5f5

Đã xác nhận đủ 43 mục dữ liệu bảng, giải nén/đọc được toàn bộ archive; chưa phục hồi vào database thử. Không in dữ liệu HR hoặc thông tin đăng nhập.

## Phương án

- Xóa 13 bảng Project/Task và uploaded_files: loại bỏ 8 dòng dữ liệu dự án/công việc cũ; giữ 19 bảng ứng dụng hiện tại và 11 bảng HR cũ.
- Xóa toàn bộ 24 bảng không dùng: loại bỏ thêm 24 dòng HR/tài khoản cũ, còn đúng 19 bảng ứng dụng hiện tại.
- Chỉ xóa 8 bảng rỗng: 7 bảng tbl_project*/tbl_task* và uploaded_files; giữ toàn bộ bảng có dữ liệu.

SQL chuẩn bị dùng transaction, lock timeout, DROP RESTRICT (không CASCADE), kiểm tra dấu kiểm bảng cần xóa và xác nhận dữ liệu bảng giữ nguyên trước COMMIT. Chưa thực thi vì automatic approval review từ chối lệnh xóa 13 bảng còn dữ liệu khi chưa có chấp thuận cụ thể.
