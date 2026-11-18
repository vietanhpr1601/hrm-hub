# Chạy HRM trên Windows

Database `hrm` đã được tạo mới ngày 30/09/2026, có đúng 19 bảng HR và dữ liệu khởi tạo từ source. DB cũ được giữ dưới tên lưu trữ; xem [kết quả và thông tin sao lưu](docs/DB_RESET_RESULT.md).

Project gồm `hrm-frontend` (React/Vite) và `hrm-backend` (Spring Boot, Java 17, PostgreSQL).

Bảng lương tạo theo nhân viên được chọn, lấy khoản lương từ hồ sơ nhân viên, tính cố định hoặc theo công chuẩn rồi duyệt và ghi nhận thanh toán. Xem [quy trình và công thức](docs/PAYROLL_MANUAL_PROCESS.md).

Phạm vi hiện tại là **Hệ thống quản lý nhân sự (HRM)**. Project/Task/Kanban đã được gỡ khỏi frontend và backend; các nghiệp vụ HR, cơ chế đăng nhập, vai trò và phiên bản công nghệ được giữ nguyên. Không xóa bảng hoặc dữ liệu PostgreSQL. Xem [báo cáo chuyển HR-only](docs/HR_ONLY_CHANGELOG.md), [rà soát database](docs/HR_ONLY_DATABASE_REVIEW.md) và [kết quả kiểm thử mới nhất](docs/HR_ONLY_TEST_REPORT.md).

## Chạy nhanh trên máy hiện tại

PostgreSQL 18 đã có database `hrm`. Script kiểm tra kết nối và chỉ tạo database nếu chưa tồn tại, không xóa dữ liệu hiện có.

```powershell
cd D:\Project\Hrm
.\start.cmd
```

Mở **http://127.0.0.1:5173**. Backend chạy ở `http://127.0.0.1:8080/api`. Hai tiến trình chạy nền, có thể đóng PowerShell sau khi script báo thành công.

```powershell
.\status.cmd    # Kiểm tra các cổng
.\stop.cmd      # Dừng frontend/backend do start.cmd tạo; giữ PostgreSQL
```

Nếu máy vừa khởi động mà PostgreSQL chưa chạy, mở Services và khởi động `postgresql-x64-18`. Nếu báo cổng đang bị chiếm bởi tiến trình cũ, nhấn Ctrl+C ở terminal đang chạy backend/frontend rồi chạy lại `start.cmd`.

Tài khoản ban đầu được cấu hình tại `app.admin` trong `hrm-backend/src/main/resources/application.yaml` (username mặc định `admin`). Mật khẩu theo `ADMIN_PASSWORD` hoặc giá trị mặc định tương ứng trong file đó. Cấu hình này không thay đổi mật khẩu của tài khoản đã tồn tại.

Sau khi thay đổi source hoặc cài máy mới:

```powershell
cd D:\Project\Hrm
.\setup-db.cmd
.\build.cmd
.\start.cmd
```

`build.cmd` dừng các tiến trình do script quản lý, chạy test backend, tạo JAR, cài dependency frontend theo lockfile rồi chạy lint/test/build. Cần có mạng khi thiếu dependency. Nếu thông tin PostgreSQL khác, đặt `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` trong cửa sổ PowerShell trước khi chạy script. Có thể đặt `PSQL_PATH` nếu PostgreSQL không cài dưới `C:\Program Files\PostgreSQL`.

Log chạy: `.runtime/backend.log`, `.runtime/frontend.log`. Script tạo khóa JWT cục bộ tại `.runtime/jwt-secret`; sau chuyển từ phiên chạy cũ, đăng nhập lại nếu phiên trước hết hiệu lực.

Artifact build: `hrm-backend/target/hrm-0.0.1-SNAPSHOT.jar` và thư mục `hrm-frontend/dist`. Kết quả và phạm vi kiểm chứng nằm trong [TEST-REPORT.md](TEST-REPORT.md).

## 1. Frontend

Trong PowerShell, dùng `npm.cmd` để tránh lỗi PowerShell chặn `npm.ps1`. Không cần đổi Execution Policy của máy.

```powershell
cd D:\Project\Hrm\hrm-frontend
npm.cmd ci
# Chỉ tạo .env khi chưa có; giữ nguyên cấu hình đang dùng.
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
npm.cmd run dev
```

Mở `http://localhost:5173`. Có thể dùng `.\dev.cmd` trong thư mục frontend để tự cài dependency khi thiếu và chạy Vite.

Cấu hình trong `.env`:

```dotenv
VITE_API_ENDPOINT=http://localhost:8080/api
VITE_ROOT_PATH=/
```

Endpoint phải có `/api`. Khởi động lại Vite sau khi sửa `.env`. Cấu hình Cloudinary chỉ cần cho tính năng tải ảnh.

## 2. Backend

Cần JDK 17 trở lên và PostgreSQL đang chạy. Có thể tạo/kiểm tra database bằng `setup-db.cmd` ở thư mục gốc. Trong cửa sổ PowerShell khác:

```powershell
cd D:\Project\Hrm\hrm-backend
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/hrm'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-PostgreSQL>'
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = '<mat-khau-admin-ban-chon>'
$env:ADMIN_EMAIL = 'admin@hrm.com'
.\mvnw.cmd spring-boot:run
```

Backend mặc định dùng cổng `8080`. `ADMIN_*` dùng khởi tạo quản trị viên khi tài khoản chưa tồn tại; đăng nhập bằng tài khoản đã có nếu database đã được thiết lập. Backend đang dùng `ddl-auto: update`, vì vậy hãy trỏ tới database phát triển phù hợp. `GROQ_API_KEY` chỉ cần cho chatbot.

## 3. Kiểm thử

Frontend:

```powershell
cd D:\Project\Hrm\hrm-frontend
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Hoặc chạy cả ba bằng `npm.cmd run check`. Lint báo lỗi có thể gây hỏng chương trình; các cảnh báo về import chưa dùng và dependency của hooks được giữ để tiếp tục dọn dẹp.

Backend:

```powershell
cd D:\Project\Hrm\hrm-backend
.\mvnw.cmd test
```

Các integration test dùng profile `test` và H2 trong bộ nhớ, tách khỏi PostgreSQL. Dependency Maven cần được tải trong lần chạy đầu tiên.

## 4. Luồng đăng nhập

1. Frontend gửi tài khoản tới `/api/auth/login` và lấy hồ sơ qua `/api/auth/getCurrentUser`.
2. Admin vào dashboard; nhân viên/HR manager vào nghỉ phép; các vai trò chuyên trách vào tuyển dụng, bảng lương hoặc thống kê công tương ứng. Đường dẫn đang mở được giữ lại khi phải đăng nhập.
3. Khi access token hết hạn, các request chờ chung một lần refresh rồi thử lại.
4. Nếu refresh không còn hợp lệ, frontend xóa phiên và chuyển về đăng nhập.
5. Backend kiểm tra quyền cho thao tác dữ liệu; việc ẩn menu không thay thế kiểm tra quyền API.

## 5. Chạy bằng Docker (máy cá nhân hoặc VPS)

`docker-compose.yml` chạy ba container: PostgreSQL (`db`), backend Spring Boot (`backend`) và frontend Nginx (`frontend`). Chỉ cổng web mở ra ngoài; Nginx chuyển `/api` sang backend trong mạng nội bộ.

```bash
cp .env.docker.example .env     # Windows: copy .env.docker.example .env
# sửa DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD trong .env
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Mở `http://localhost` (hoặc `http://<IP VPS>`). Lần đầu CSDL trống, backend tự tạo bảng và tài khoản Admin theo `ADMIN_USERNAME`/`ADMIN_PASSWORD`. Dừng: `docker compose down` (dữ liệu vẫn giữ trong volume `db-data`).

Chuyển dữ liệu đang có sang container:

```bash
pg_dump -U postgres -d hrm --no-owner --no-acl > hrm.sql
docker compose exec -T db psql -U hrm -d hrm < hrm.sql
```

Chấm công cần định vị GPS, mà trình duyệt chỉ cho định vị trên HTTPS (hoặc localhost). Trên VPS dùng thêm tệp `docker-compose.vps.yml`: container Caddy nhận cổng 80/443, tự xin chứng chỉ Let's Encrypt cho `SITE_ADDRESS` và chuyển vào frontend.

```bash
# trong .env trên VPS: SITE_ADDRESS=<IP-viết-bằng-dấu-gạch>.sslip.io (hoặc tên miền riêng đã trỏ về IP)
docker compose -f docker-compose.yml -f docker-compose.vps.yml up -d --build
```

Thông tin về lỗi PowerShell: [Microsoft — about_Execution_Policies](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_execution_policies).
