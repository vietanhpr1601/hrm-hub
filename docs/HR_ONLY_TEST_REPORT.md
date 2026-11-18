# Kết quả kiểm chứng HR-only — 29/09/2026

## Build và chạy ứng dụng

| Kiểm tra | Kết quả |
| --- | --- |
| Backend `mvnw.cmd clean compile` | Thành công, Java 17; không còn dependency Project/Task trong production source |
| Backend `mvnw.cmd verify` sau sửa cuối | 52 test đạt, 0 lỗi, 0 bỏ qua; tạo JAR thành công |
| Backend `mvnw.cmd spring-boot:run` | Khởi động thành công trên 8080; API được bảo vệ trả 401 khi chưa đăng nhập |
| Frontend `npm.cmd run build` | Thành công; còn cảnh báo bundle lớn hơn 500 kB |
| Frontend `npm.cmd test` | 19/19 test đạt |
| Frontend `npm.cmd run lint` | 0 lỗi, 181 cảnh báo import/hook/code chưa dùng |
| API trên PostgreSQL `hrm` | 32/32 mục đạt, bao gồm đăng nhập, refresh, đăng xuất, các API HR và 404 cho API đã gỡ |
| Chrome headless, profile kiểm thử riêng | 37/37 mục đạt: đăng nhập, 15 trang HR, kiểm tra menu, 4 đường dẫn cũ và lỗi runtime/import/HTTP |
| Database trước/sau | 43 bảng giữ nguyên số dòng; 60 FK không đổi; dấu kiểm toàn bộ dòng trong 16 bảng nghiệp vụ HR không đổi |
| Bản đang chạy | Frontend 5173, backend 8080, PostgreSQL 5432 |

Chrome ghi nhận 63 cảnh báo React về prop/defaultProps qua các lần tải trang của một lượt kiểm tra; được ghi riêng, không tắt cảnh báo trong ứng dụng. Không có lỗi JavaScript runtime/import hoặc HTTP nội bộ thất bại trong lượt UI cuối. Không nâng thư viện để xử lý cảnh báo này.

## Phạm vi đã kiểm tra

- Xác thực và phân quyền: đăng nhập, refresh, hồ sơ phiên, đăng xuất, JWT sai loại/hết hạn/sai chữ ký, tài khoản vô hiệu hóa/xóa, CORS và quyền truy cập chấm công/lương cá nhân.
- Tài khoản: tạo, khóa, mở khóa, thay vai trò và xóa mềm trong database kiểm thử.
- Nhân viên, phòng ban, vị trí: CRUD, tìm kiếm, quan hệ, mã trùng, xóa mềm.
- Chấm công: vào/ra, tính công, phê duyệt, giới hạn thao tác theo nhân viên.
- Nghỉ phép: ngày nghỉ không hợp lệ, duyệt/từ chối, tính lại số dư phép và công.
- Tuyển dụng: trạng thái ứng viên, tiếp nhận thành nhân viên, ngăn tiếp nhận lặp.
- Lương: quyền đọc, một bảng lương được chốt cho mỗi kỳ, phiếu lương cá nhân và dữ liệu đã xóa/trùng.
- HR-only: không đăng ký entity/controller Project/Task; Dashboard giữ số liệu HR thực tế và không còn trường dự án; API đã gỡ trả 404.
- UI với Admin: Dashboard, tài khoản, nhân viên, phòng ban, vị trí, lịch/thống kê/phê duyệt công, nghỉ phép, số dư phép, khoản lương, kỳ lương, bảng lương, tuyển dụng. URL Project/Task cũ chuyển về trang chính.

Các test backend dùng H2 tách biệt và rollback. API/UI smoke trên PostgreSQL chỉ đọc bản ghi nghiệp vụ; đăng nhập/refresh/đăng xuất vẫn cập nhật thông tin phiên theo cơ chế sẵn có. Lượt này không chạy lại bộ integration test trên database PostgreSQL riêng.

Chưa kiểm thử trực tiếp webcam/GPS/Cloudinary, chatbot Groq, thao tác thanh toán bên ngoài hoặc mọi tổ hợp công thức lương. Các luồng ghi HR được kiểm thử tự động ở backend, không nhập dữ liệu thử vào PostgreSQL đang sử dụng.

## Artifact và bằng chứng

- Backend: `hrm-backend/target/hrm-0.0.1-SNAPSHOT.jar`; bản sao giao nhận `release/hrm-backend.jar`.
- Frontend: `hrm-frontend/dist`; bản nén giao nhận `release/hrm-frontend.zip`.
- Log và ảnh (tại thư mục `.runtime/`): `hr-only-backend-compile.log`, `hr-only-backend-verify.log`, `hr-only-spring-run.log`, `hr-only-frontend-build.log`, `hr-only-frontend-tests.log`, `hr-only-frontend-lint.log`, `hr-only-ui.log`, `hr-only-ui-results.json`, `hr-only-dashboard.png`.
- Đối chiếu DB chỉ đọc: `.runtime/hr-only-db-before.json`, `.runtime/hr-only-db-after.json`; không chứa dữ liệu dòng HR hoặc mật khẩu.
- Source trước khi gỡ module: `.runtime/hr-only-source-before.zip` (lưu cục bộ).

## Chạy lại

```powershell
cd D:\Project\Hrm
.\start.cmd
node scripts\smoke-api.mjs
node scripts\smoke-ui.mjs
.\status.cmd
```

Chạy API smoke và UI smoke lần lượt để tránh hai lần đăng nhập cùng tài khoản tác động tới phiên nhau. UI smoke dùng Chrome cài sẵn; có thể đặt `HRM_CHROME_PATH` nếu Chrome ở đường dẫn khác. Không cài thêm npm package.

Build lại bằng các lệnh hiện có:

```powershell
cd D:\Project\Hrm
.\stop.cmd
cd hrm-backend
.\mvnw.cmd clean verify
cd ..\hrm-frontend
npm.cmd run build
npm.cmd test
cd ..
.\start.cmd
```

Không trỏ các biến `HRM_TEST_DB_*` vào database `hrm` đang giữ dữ liệu: profile kiểm thử sử dụng `create-drop`.
