# Rà soát database khi chuyển ứng dụng sang HR-only

> Cập nhật ngày 30/09/2026: ứng dụng hiện dùng database `hrm` mới với 19 bảng; DB 43 bảng trong bản rà soát này đã được lưu dưới tên `hrm_archive_20260930033224`. Xem [kết quả tạo database mới](DB_RESET_RESULT.md).

Ngày rà soát: 29/09/2026. Phạm vi: đọc source và bản trích cấu trúc sẵn có; **không kết nối thay đổi database, không chạy DROP/DELETE/TRUNCATE, không sửa cấu hình hoặc runtime**.

Nguồn cấu trúc là [schema.json](database/schema.json) và [schema.sql](database/schema.sql), chụp database `hrm` trên PostgreSQL 18.6 lúc `2026-09-28T11:55:42+07:00`. Đây là ảnh chụp 43 bảng trong schema `public`, không phải xác nhận trực tiếp trạng thái DB tại thời điểm dọn bảng sau này.

Đã kiểm tra bổ sung trực tiếp ngày 29/09/2026 bằng giao dịch PostgreSQL **READ ONLY** trước/sau khi chạy bản HR-only. Kết quả: 43 bảng không đổi số dòng, 60 khóa ngoại không đổi, dấu kiểm dữ liệu của 16 bảng nghiệp vụ HR không đổi. Bằng chứng lưu cục bộ trong `.runtime/hr-only-db-before.json` và `.runtime/hr-only-db-after.json`; chỉ có số lượng, metadata và dấu kiểm, không ghi dữ liệu nhân sự hoặc mật khẩu. Các bảng xác thực không đối chiếu dấu kiểm toàn dòng vì đăng nhập/refresh có thể cập nhật thông tin phiên. Không thực hiện lệnh xóa bảng.

## 1. Cơ chế quản lý schema hiện có

- Không tìm thấy Flyway, Liquibase hoặc thư mục migration/changelog trong Backend; `pom.xml` sử dụng Spring Data JPA và PostgreSQL JDBC.
- `hrm-backend/src/main/resources/application.yaml:10` đặt `spring.jpa.hibernate.ddl-auto: update`. Việc bỏ entity Project/Task khỏi ứng dụng **không đồng nghĩa các bảng cũ bị xóa**. Có thể giữ nguyên bảng và dữ liệu trong DB sau khi ứng dụng đã bỏ module.
- `hrm-backend/src/main/java/hrm/config/DatabaseInitializer.java:54` chạy seed vai trò, admin mặc định, phòng ban và chức vụ từ source/CSV. Không thấy seed Project/Task trong initializer này.
- `scripts/local.mjs:42` chỉ tạo database nếu chưa có rồi kiểm tra số bảng; không phải công cụ migration hay dọn bảng.
- Profile kiểm thử dùng `create-drop` với H2; không áp dụng cấu hình test này vào PostgreSQL đang giữ dữ liệu.

## 2. Bảy bảng Project/Task của source hiện tại

Các bảng dưới đây dùng khóa UUID, được ánh xạ bởi các entity Project/Task trước khi loại module. Có thể đưa vào kế hoạch xóa thủ công **sau khi đã sao lưu, xác nhận bỏ dữ liệu module và kiểm tra lại phụ thuộc trên DB thực tế**. Nhiệm vụ chuyển ứng dụng HR-only không cần tự động xóa chúng.

| Bảng | Chức năng | Khóa ngoại đi ra theo snapshot |
| --- | --- | --- |
| `tbl_task_attachment` | File đính kèm công việc | `task_id` → `tbl_task.id` |
| `tbl_task_history` | Lịch sử và bình luận công việc | `task_id` → `tbl_task.id`; `modifier_id` → `tbl_staff.id` |
| `tbl_task` | Công việc | `project_id` → `tbl_project.id`; `activity_id` → `tbl_project_activity.id`; `status_id` → `tbl_project_working_status.id`; `assignee_id` → `tbl_staff.id` |
| `tbl_project_staff` | Thành viên dự án | `project_id` → `tbl_project.id`; `staff_id` → `tbl_staff.id` |
| `tbl_project_activity` | Hoạt động/nhóm công việc dự án | `project_id` → `tbl_project.id` |
| `tbl_project_working_status` | Trạng thái/cột Kanban | `project_id` → `tbl_project.id` |
| `tbl_project` | Dự án | Không có khóa ngoại đi ra |

Trong snapshot, không có bảng HR nào tham chiếu ngược vào bảy bảng này. Các liên kết đến nhân viên đi từ bảng Project/Task sang `tbl_staff`; **không được xóa nhân viên để dọn module**.

Nếu có một đợt dọn bảng được quyết định riêng, thứ tự phụ thuộc cần xét là: đính kèm/lịch sử → công việc → thành viên/hoạt động/trạng thái dự án → dự án. Tất cả khóa ngoại liên quan trong snapshot là `ON DELETE NO ACTION`; cascade trong JPA không đồng nghĩa database có `ON DELETE CASCADE`.

## 3. Nhóm bảng dạng cũ cùng tồn tại

Snapshot còn năm bảng Project/Task dùng `bigint`: `projects`, `project_members`, `tasks`, `task_attachments`, `task_histories`. Không thấy entity của Backend hiện tại ánh xạ năm tên này. Không kết luận dữ liệu không còn giá trị hoặc chắc chắn không có ứng dụng khác sử dụng chỉ từ việc không tìm thấy trong source hiện tại.

| Phụ thuộc của nhóm cũ | Ý nghĩa khi dọn bảng |
| --- | --- |
| `project_members.project_id` → `projects.id`; `tasks.project_id` → `projects.id` | Bảng thành viên và công việc phụ thuộc dự án. |
| `task_attachments.task_id`, `task_histories.task_id` → `tasks.id` | File đính kèm và lịch sử phụ thuộc công việc. |
| `project_members.employee_id`, `projects.manager_employee_id`, `tasks.assignee_employee_id` → `employees.id` | Giữ bảng nhân viên dạng cũ; không xóa theo dự án. |
| `task_attachments.uploaded_by`, `task_histories.changed_by` → `users.id` | Giữ tài khoản dạng cũ. |
| **`uploaded_files.task_id` → `tasks.id`** | Có phụ thuộc từ bảng tệp dùng chung nằm ngoài năm bảng Project/Task; chưa thể coi nhóm năm bảng là tách biệt hoàn toàn. |

`uploaded_files` còn có `owner_id` → `users.id`, loại tệp (`purpose`) và cột `task_id` cho phép rỗng. Vì vậy **không xóa toàn bộ `uploaded_files`** để bỏ Task. Không dùng `DROP ... CASCADE`: lệnh có thể kéo theo ràng buộc/đối tượng ngoài phạm vi và che khuất phụ thuộc còn cần giữ. Quyết định xử lý liên kết tệp cũ phải được tách riêng, sau khi kiểm tra dữ liệu và nơi đang sử dụng.

## 4. Bảng HR phải giữ

| Nhóm nghiệp vụ | Bảng của source hiện tại |
| --- | --- |
| Xác thực, phân quyền | `tbl_user`, `tbl_role`, `tbl_user_role` |
| Nhân viên và tổ chức | `tbl_staff`, `tbl_department`, `tbl_position` |
| Chấm công | `tbl_shift_work`, `tbl_check_in_out_record`, `tbl_timesheet`, `tbl_timesheet_detail` |
| Nghỉ phép | `tbl_leave_request` |
| Lương | `tbl_salary_item`, `tbl_staff_salary_item`, `tbl_period`, `tbl_payroll`, `tbl_payslip`, `tbl_payslip_item` |
| Tuyển dụng | `tbl_recruitment`, `tbl_candidate` |

Giữ cả các bảng HR dạng cũ ngoài phạm vi chuyển module: `attendances`, `candidates`, `departments`, `employees`, `job_postings`, `leave_requests`, `payroll_periods`, `payrolls`, `positions`, `roles`, `users`. Việc hợp nhất hoặc xóa chúng cần một nhiệm vụ di chuyển dữ liệu riêng. Giữ `uploaded_files` vì là dữ liệu tệp có liên kết khác ngoài Task.

## 5. Phụ thuộc source cần loại trước khi cân nhắc dọn DB

- Trước đợt refactor, `DashboardServiceImpl` gọi `ProjectRepository` để đếm dự án và tổng hợp hoạt động gần đây. Chỉ gỡ menu Project/Task chưa loại hết nhu cầu truy vấn bảng dự án.
- Các entity, repository, controller, service và `ProjectUtils` tham chiếu module phải được gỡ hoặc điều chỉnh đồng bộ. Không xóa bảng khi bản Backend đang chạy vẫn ánh xạ/truy vấn chúng.
- Dữ liệu file vật lý hoặc dịch vụ lưu trữ bên ngoài không tự mất khi xóa dòng/bảng đính kèm. Không dọn file chung theo suy đoán từ tên bảng.

## 6. Truy vấn kiểm tra lại khóa ngoại (chỉ đọc)

Truy vấn dưới đây chỉ đọc PostgreSQL catalog để xác nhận phụ thuộc vào/ra các bảng dự kiến bỏ. Tài liệu này không thực thi truy vấn và không kèm lệnh xóa.

```sql
SELECT src_ns.nspname AS source_schema,
       src.relname AS source_table,
       con.conname AS constraint_name,
       dst_ns.nspname AS target_schema,
       dst.relname AS target_table,
       pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con
JOIN pg_class src ON src.oid = con.conrelid
JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
JOIN pg_class dst ON dst.oid = con.confrelid
JOIN pg_namespace dst_ns ON dst_ns.oid = dst.relnamespace
WHERE con.contype = 'f'
  AND (
    (src_ns.nspname = 'public' AND src.relname IN (
      'tbl_project', 'tbl_project_staff', 'tbl_project_activity',
      'tbl_project_working_status', 'tbl_task', 'tbl_task_attachment',
      'tbl_task_history', 'projects', 'project_members', 'tasks',
      'task_attachments', 'task_histories'
    ))
    OR
    (dst_ns.nspname = 'public' AND dst.relname IN (
      'tbl_project', 'tbl_project_staff', 'tbl_project_activity',
      'tbl_project_working_status', 'tbl_task', 'tbl_task_attachment',
      'tbl_task_history', 'projects', 'project_members', 'tasks',
      'task_attachments', 'task_histories'
    ))
  )
ORDER BY source_schema, source_table, constraint_name;
```

Có thể đối chiếu thêm bằng [inspect-schema.sql](database/inspect-schema.sql), là truy vấn catalog sẵn có. Cần kiểm tra thêm view, trigger, hàm SQL, các ứng dụng khác và dữ liệu thực trước một quyết định dọn DB; snapshot hiện tại chủ yếu chứng minh cấu trúc bảng và khóa ngoại.

## Nguồn đối chiếu

- `docs/database/schema.json`: tên DB, thời điểm chụp, danh sách bảng và thuộc tính khóa ngoại.
- `docs/database/schema.sql:732`: nhóm `tbl_project`; `:953`: nhóm `tbl_task`; `:1688`: khóa ngoại Project/Task; `:1737`: liên kết `uploaded_files.task_id` đến `tasks`.
- `hrm-backend/pom.xml`, `hrm-backend/src/main/resources/application.yaml:10`, `hrm-backend/src/main/java/hrm/config/DatabaseInitializer.java:54`, `scripts/local.mjs:42`.
- Các entity Project/Task và `DashboardServiceImpl` được kiểm tra trước khi loại module; nếu chúng không còn trong bản HR-only, dùng bản trích schema làm nguồn lịch sử cho phần phụ thuộc DB này.
