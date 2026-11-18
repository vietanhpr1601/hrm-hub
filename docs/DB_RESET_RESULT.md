# Database HRM mới — 30/09/2026

Đã tạo lại môi trường dữ liệu mới cho ứng dụng HR-only. Database ứng dụng vẫn có tên **`hrm`**, PostgreSQL tại **`localhost:5432`**. Backend và frontend đang chạy lại bình thường.

## Kết quả thực tế

- Database `hrm` hiện có đúng **19 bảng**, khớp 19 entity HR trong source; **25 khóa ngoại**.
- Không còn bảng Project/Task hoặc bảng legacy trong `hrm` mới.
- Java, Spring Boot, Node/npm, `pom.xml`, `package.json`, lockfile, `application.yaml` và script khởi động được giữ nguyên. Không cần build lại vì không thay đổi source ứng dụng.
- Giữ nguyên owner `postgres`, encoding UTF8, locale và tablespace của DB trước đó.

## Dữ liệu khởi tạo

Backend tự khởi tạo dữ liệu bằng seed đang có trong source:

| Nhóm | Bảng | Số bản ghi |
| --- | --- | ---: |
| Vai trò | `tbl_role` | 6 |
| Tài khoản quản trị | `tbl_user` | 1 |
| Gán vai trò quản trị | `tbl_user_role` | 1 |
| Phòng ban | `tbl_department` | 7 |
| Chức vụ/vị trí | `tbl_position` | 20 |
| Ca làm việc | `tbl_shift_work` | 4 |

Tổng cộng **39 bản ghi mặc định**. Tài khoản Admin dùng tên đăng nhập và mật khẩu cấu hình hiện tại trong `app.admin` của `hrm-backend/src/main/resources/application.yaml` hoặc biến môi trường tương ứng; mật khẩu không được ghi vào báo cáo.

13 bảng nghiệp vụ còn lại đang rỗng: `tbl_candidate`, `tbl_check_in_out_record`, `tbl_leave_request`, `tbl_payroll`, `tbl_payslip`, `tbl_payslip_item`, `tbl_period`, `tbl_recruitment`, `tbl_salary_item`, `tbl_staff`, `tbl_staff_salary_item`, `tbl_timesheet`, `tbl_timesheet_detail`.

Admin mới chưa gắn hồ sơ nhân viên. Các chức năng cá nhân như check-in/check-out và xin nghỉ cần tạo nhân viên, gắn tài khoản trước khi sử dụng.

## Dữ liệu cũ và bản sao lưu

DB cũ **không bị xóa vật lý**. Toàn bộ DB cũ được đổi tên thành **`hrm_archive_20260930033224`**, rồi một database `hrm` mới được tạo. Việc giữ DB lưu trữ không làm các bảng cũ xuất hiện trong database mà ứng dụng đang dùng.

Bản sao lưu đầy đủ trước thao tác:

```text
D:\Project\Hrm\.runtime\db-cleanup\hrm-before-cleanup-2026-09-30T03-29-11-048Z.dump
```

SHA-256: `82e376d79b9e362cff4d824a2a4129d7919909958efd206be0d9bb13c622833b`.

Đã kiểm tra đủ 43 mục dữ liệu bảng và giải nén/đọc được toàn bộ archive. Chưa chạy phục hồi thử vào database khác.

## Kiểm tra sau khởi tạo

| Kiểm tra | Kết quả |
| --- | --- |
| Schema so với entity hiện tại | Đúng 19/19 bảng |
| Dữ liệu seed so với source | Đúng 39 bản ghi, các bảng nghiệp vụ khác rỗng |
| API smoke | 32/32 đạt |
| Chrome UI smoke | 37/37 đạt; không lỗi runtime/import hoặc HTTP nội bộ thất bại |
| Trạng thái | PostgreSQL 5432, backend 8080, frontend 5173 đang lắng nghe |

UI vẫn có cảnh báo React prop/defaultProps (45 cảnh báo trong lượt kiểm tra DB rỗng), được ghi riêng; không nâng thư viện hoặc tắt cảnh báo. Bằng chứng tại `.runtime/db-cleanup/after.json`, `fresh-database-result.json`, `fresh-api.log`, `fresh-ui.log` và `.runtime/hr-only-ui-results.json`.

## Sử dụng

Mở **http://127.0.0.1:5173** và đăng nhập lại; phiên đăng nhập từ DB cũ không còn phù hợp. Trong pgAdmin, refresh danh sách database và chọn đúng **`hrm` → Schemas → public → Tables** để xem 19 bảng mới. DB có tiền tố `hrm_archive_` chỉ là dữ liệu cũ được giữ lại.

Lần khởi động tiếp theo:

```powershell
cd D:\Project\Hrm
.\start.cmd
.\status.cmd
```

Hệ thống xét duyệt tự động đã từ chối `DROP DATABASE` dù người dùng chấp thuận, vì phân loại xóa toàn bộ database là thao tác phá hủy mức nghiêm trọng. Do đó phương án đã thực hiện là lưu nguyên DB cũ và tạo `hrm` mới, không thực thi lệnh xóa bị chặn.
