# Kết quả kiểm chứng — 28/09/2026

> Đây là báo cáo lịch sử trước khi gỡ Project/Task. Kết quả cho phiên bản HR-only ngày 29/09/2026 nằm tại [HR_ONLY_TEST_REPORT.md](docs/HR_ONLY_TEST_REPORT.md).

## Đã chạy thành công

| Phần | Kết quả |
| --- | --- |
| Frontend `npm.cmd run check` | 19 test đạt; lint 0 lỗi; build Vite thành công |
| Backend test H2 | 50 test đạt, không bỏ qua test |
| Backend test PostgreSQL 18 | 50 test đạt trên database riêng `hrm_regression` |
| Backend đóng gói | JAR Spring Boot chạy được, khoảng 64 MB |
| API smoke trên PostgreSQL `hrm` | 30/30 kiểm tra đạt; không thay đổi bản ghi nghiệp vụ |
| Frontend HTTP | HTTP 200; cấu hình thực trỏ `http://localhost:8080/api` |
| PostgreSQL hiện có | Database `hrm`, 43 bảng; giữ dữ liệu hiện có |
| Khởi động | `start.cmd` chạy nền backend8080/frontend5173; chạy lặp không tạo thêm tiến trình |

Artifact để sử dụng: `release/hrm-backend.jar`, `release/hrm-frontend.zip`. Nguồn artifact tương ứng: `hrm-backend/target/hrm-0.0.1-SNAPSHOT.jar`, `hrm-frontend/dist`.

## Phạm vi test

- Frontend: trang đích theo vai trò, trở về đường dẫn sau đăng nhập, chặn redirect ngoài ứng dụng, refresh đồng thời/hết hạn/thiếu token, request cũ không ghi đè phiên mới, timeout loading khi request gối nhau.
- Backend auth: đăng nhập/hồ sơ, JWT hết hạn/sai chữ ký/sai loại, phân biệt access/refresh, tài khoản bị vô hiệu hóa/xóa, CORS preflight và truy cập chưa xác thực.
- Phòng ban/vị trí/nhân sự: tạo, sửa, tìm kiếm, liên kết và xóa mềm; mã trùng bị từ chối; tìm kiếm không truyền phân trang vẫn dùng giá trị mặc định.
- Tuyển dụng: ứng viên qua các trạng thái, tiếp nhận thành nhân viên, chặn tiếp nhận lặp.
- Nghỉ phép: kiểm tra dữ liệu đầu vào, duyệt/từ chối, tính lại số dư và ngày công.
- Chấm công: vào/ra, tính công, phê duyệt; chặn nhân viên thao tác bản ghi người khác.
- Dự án/công việc: tạo và cập nhật, Kanban, công việc của nhân viên, chặn người ngoài dự án.
- Lương: quyền truy cập, chỉ một bảng lương xác nhận mỗi kỳ, truy vấn đúng phiếu cá nhân, bỏ bản xóa, xử lý dữ liệu trùng cũ.
- API smoke: auth, dashboard và danh sách/tìm kiếm các phân hệ nhân sự, tài khoản, phòng ban, vị trí, dự án, công việc, tuyển dụng, ứng viên, ca, công, kỳ lương, bảng lương, nghỉ phép.

## Giới hạn còn lại

Chưa kiểm thử thao tác trực quan trong trình duyệt vì phiên công cụ không có trình duyệt kết nối. Chưa xác minh chatbot Groq, tải ảnh Cloudinary, webcam hoặc dịch vụ chuyển khoản bên ngoài. Các test hiện có không bao phủ toàn bộ công thức tính lương, tình huống đồng thời và mọi tổ hợp nghiệp vụ.

Lint vẫn có cảnh báo code cũ về import chưa dùng/dependency hooks; Vite cảnh báo bundle JavaScript lớn. Đây không phải lỗi chặn build.

Lần đóng gói offline đầu tiên thiếu POM/dependency của Maven jar plugin. Đã tải dependency chính thức bằng Maven và đóng gói lại thành công; không bỏ qua test lỗi để tạo artifact (50 test đã đạt trước bước đóng gói).

## Chạy lại

```powershell
cd D:\Project\Hrm
.\build.cmd
.\start.cmd
node scripts\smoke-api.mjs
.\status.cmd
```

Test mặc định dùng H2. Việc chọn PostgreSQL cho test chỉ qua các biến `HRM_TEST_DB_*`; dùng database riêng vì test cấu hình `create-drop`. Không trỏ `HRM_TEST_DB_URL` vào `hrm` chứa dữ liệu ứng dụng.
