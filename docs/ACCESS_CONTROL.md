# Hướng dẫn phân quyền hệ thống HRM

Giao diện dùng ô chọn vai trò, quyền theo module thu gọn và tìm kiếm. Cập nhật 05/10/2026: có trang **Quản trị → Vai trò** để xem danh sách, tạo và xóa vai trò; bỏ hai thẻ chọn nhanh Admin/Người dùng ở trang phân quyền và form tài khoản. Nhập Excel chỉ dành cho Admin, nhập/xuất yêu cầu mật khẩu. Xem thêm [hướng dẫn chấm công GPS và làm bù](ATTENDANCE_GPS.md).

## 1. Phân quyền theo vai trò

Đăng nhập bằng tài khoản Admin, mở **Quản trị → Phân quyền** tại `/administration/permissions`.

1. Chọn tab **Vai trò**.
2. Dùng ô **Chọn vai trò** để chọn vai trò cần chỉnh. Bấm **Quản lý vai trò** để sang trang danh sách/tạo/xóa vai trò. Admin luôn có toàn quyền.
3. Mở module hoặc nhập **Tìm quyền**, đánh dấu các chức năng được phép sử dụng. Có thể chọn từng quyền hoặc cả module.
4. Nhấn **Lưu quyền**.
5. Chọn tab **Tài khoản**, chọn tài khoản cần phân quyền.
6. Dùng ô **Vai trò** để thêm hoặc bỏ các vai trò đang gán. Có thể gán nhiều vai trò thường; khi chọn Admin, các vai trò được thay bằng Admin và quyền riêng được bỏ. Chỉ xem tài khoản không tự thay đổi vai trò.
7. Nhấn **Lưu tài khoản**.

Tài khoản mới trên form mặc định chọn **Người dùng** (`ROLE_USER`) nếu vai trò này còn tồn tại: chấm công, nghỉ phép và phiếu lương của mình. Nếu đã xóa vai trò Người dùng, form để trống vai trò để Admin chọn vai trò khác; không tự cấp Admin. Tài khoản và vai trò cũ không bị tự chuyển đổi. Cấu hình đã lưu của vai trò Người dùng, kể cả không có quyền nào, được giữ qua khởi động lại.

Ví dụ nhóm chỉ đọc nhân viên: chỉ chọn **Xem hồ sơ nhân viên**. Tài khoản thuộc nhóm này xem được danh sách và hồ sơ, không được thêm, sửa, xóa, nhập hoặc xuất Excel khi chưa được cấp các quyền tương ứng.

Nên cấp quyền xem cùng với quyền thao tác của module để người dùng truy cập được màn hình. Quyền thao tác riêng không tự cấp quyền xem. Ví dụ để xuất Excel từ danh sách nhân viên, cấp **Xem hồ sơ nhân viên** và **Xuất Excel nhân viên**. **Nhập Excel nhân viên mới chỉ dành cho Admin**, không thể cấp cho vai trò hoặc tài khoản khác; quyền nhập cũ của người không phải Admin không còn hiệu lực.

Nhóm mới ban đầu không có quyền. Với nhóm mặc định đã có, hệ thống giữ quyền cũ cho tới lần Admin lưu cấu hình đầu tiên. Lưu một nhóm không đánh dấu quyền nào sẽ thu hồi toàn bộ quyền từ nhóm đó.

### Thông báo và quyền Bảng tin đã gỡ

Từ cập nhật 13/10/2026, quyền **Xem bảng tin** (`BULLETIN_VIEW`) đã được gỡ khỏi danh mục, cấu hình mặc định và quyền có hiệu lực. Quyền cũ còn lưu trong vai trò/tài khoản được bỏ qua và không gửi lại khi mở/lưu màn hình phân quyền.

| Module | Quyền | Phạm vi |
| --- | --- | --- |
| Thông báo | **Xem thông báo của mình** (`NOTIFICATION_VIEW`) | Hiện chuông, mở danh sách, xem số chưa đọc và đánh dấu thông báo cá nhân đã đọc. |

Admin mở **Quản trị → Phân quyền → Vai trò**, chọn vai trò, mở module Thông báo và đánh dấu quyền cần cấp rồi nhấn **Lưu quyền**. Trong tab **Tài khoản**, có ba lựa chọn **Theo vai trò / Cho phép / Chặn** cho quyền này. Quyền riêng **Chặn** có ưu tiên hơn quyền từ vai trò; Admin luôn có toàn quyền trong danh mục hiện tại.

Vai trò đã lưu cấu hình dùng các quyền hợp lệ đã được cấp. Các vai trò hệ thống chưa cấu hình và vai trò Người dùng mới khởi tạo có quyền Thông báo trong cấu hình mặc định; vai trò tùy chỉnh mới không tự được cấp. Mã Bảng tin đã gỡ không được cấp mới qua API.

Khi không có quyền Thông báo, giao diện ẩn menu và chuông, dừng tải định kỳ, xóa dữ liệu thông báo đang giữ trên giao diện; API danh sách, số chưa đọc, đọc từng mục và đọc tất cả đều từ chối. Khi quyền được cập nhật về trình duyệt, phản hồi cũ không khôi phục dữ liệu đã bị thu hồi. Tài khoản không được cấp trang nghiệp vụ nào sẽ vào màn hình **Không có quyền truy cập**, không chuyển vòng lặp tới Thông báo; vẫn mở được Trang cá nhân qua ảnh đại diện.

Thông báo phê duyệt vẫn được lưu cho người nhận ngay cả lúc họ chưa có quyền xem; cấp lại quyền sẽ xem được lịch sử. Quyền này không cho đọc hộp thư của tài khoản khác, kể cả Admin, và không tự cấp quyền mở chi tiết chấm công/đơn nghỉ. Xem thêm [Thông báo nhân viên](EMPLOYEE_NOTIFICATIONS.md).

### Trang cá nhân

Từ cập nhật 14/10/2026, nhấn ảnh đại diện ở góc phải → **Trang cá nhân** (`/profile`) để xem thông tin tài khoản, cá nhân, liên hệ và công việc. Mọi tài khoản đang hoạt động đều có thể xem trang này sau đăng nhập, không cần quyền quản lý hồ sơ nhân viên.

API `GET /api/staffs/me` lấy nhân viên liên kết từ tài khoản đăng nhập, không nhận mã nhân viên để xem hộ người khác. Trang chỉ xem dữ liệu; tài khoản chưa liên kết hồ sơ vẫn xem được thông tin tài khoản và nhận hướng dẫn liên hệ quản trị viên. Quyền xem/sửa danh sách và hồ sơ nhân viên khác không thay đổi.

## 2. Ngoại lệ theo tài khoản

Trong tab **Tài khoản**, mở module hoặc tìm quyền. Mỗi chức năng có ba lựa chọn:

| Lựa chọn | Ý nghĩa |
| --- | --- |
| Theo vai trò | Nhận quyền nếu có ít nhất một nhóm vai trò được gán cấp quyền. |
| Cho phép | Cấp riêng quyền cho tài khoản, kể cả khi nhóm chưa cấp. |
| Chặn | Từ chối quyền, kể cả khi một hoặc nhiều nhóm đã cấp. |

Quyền hiệu lực được xem trước trên màn hình và chỉ áp dụng sau khi nhấn **Lưu tài khoản**.

Admin luôn có toàn quyền. Trang phân quyền không giới hạn nhóm hoặc tài khoản Admin. Khi cấp vai trò Admin cho tài khoản khác, các ngoại lệ quyền riêng sẽ được bỏ. Muốn thay vai trò Admin hiện có, dùng màn hình quản lý tài khoản; hệ thống không cho khóa, xóa hoặc bỏ vai trò của Admin đang hoạt động cuối cùng.

## 3. Khi thay đổi quyền

- Backend đọc lại quyền hiện tại từ cơ sở dữ liệu ở mỗi yêu cầu; token đăng nhập cũ không giữ quyền đã bị thu hồi.
- Giao diện cập nhật thông tin quyền khi tải lại trang hoặc quay lại cửa sổ ứng dụng. Có thể nhấn F5 sau khi Admin đổi quyền.
- Menu và nút thao tác chỉ hiển thị khi có quyền phù hợp. Truy cập trực tiếp URL hoặc gửi yêu cầu API vẫn phải qua kiểm tra quyền của Backend.
- Chỉ Admin được quản lý tài khoản, nhóm và cấu hình phân quyền.
- Phòng ban chỉ dùng để quản lý tổ chức nhân sự, không cấp quyền cho tài khoản. Đổi hoặc xóa phòng ban không thay đổi quyền từ vai trò và tài khoản. Các quyền có nội dung **của mình** vẫn chỉ áp dụng cho nhân viên liên kết với tài khoản.
- Từ ngày 02/10/2026, chức năng phân quyền theo phòng ban đã được loại bỏ. Quyền phòng ban cũ không được chuyển tự động thành quyền vai trò hoặc quyền riêng.

## 4. Nhập Excel nhân viên mới

Admin vào **Hồ sơ nhân viên**, chọn **Tải mẫu**, điền dữ liệu theo sheet hướng dẫn, sau đó dùng **Nhập Excel**. Chọn file và nhập mật khẩu của tài khoản đang đăng nhập trong hộp xác nhận. File phải là `.xlsx`, tối đa 1 MB và 1.000 dòng dữ liệu. Không sửa tên các cột trong mẫu. Tài khoản khác không có nút nhập/tải mẫu và không thể gọi trực tiếp API để nhập.

Họ tên, email và trạng thái nhân viên là bắt buộc. Mã nhân viên có thể để trống để hệ thống sinh tự động. Các mã định danh, số điện thoại, CCCD và mã bảo hiểm nên nhập ở định dạng Text để giữ số 0 đầu. Phòng ban và vị trí tra theo sheet danh mục.

Nếu có lỗi, hệ thống hiển thị dòng Excel, trường và nguyên nhân; toàn bộ lượt nhập đó không thêm nhân viên nào. File chỉ dùng để thêm mới, không ghi đè nhân viên cũ và không tự tạo tài khoản đăng nhập.

### Xác nhận khi nhập và xuất

- **Nhập Excel:** chỉ Admin; mật khẩu xác nhận là mật khẩu đăng nhập của Admin đó.
- **Xuất Excel:** Admin hoặc tài khoản được cấp quyền xuất; mỗi lần xuất đều nhập mật khẩu của chính tài khoản đang đăng nhập. Mật khẩu của tài khoản khác không thay thế được.
- Sai hoặc thiếu mật khẩu: không nhập dữ liệu, không tải file; hiển thị lỗi và cho nhập lại, không tự đăng xuất.
- Hủy hộp thoại: không thực hiện thao tác. Mật khẩu xác nhận không được lưu vào localStorage hay nhớ cho lần sau; đổi tài khoản sẽ đóng hộp xác nhận.
- Tải mẫu trống dành cho Admin không yêu cầu mật khẩu xác nhận vì không xuất danh sách nhân viên.

### Quản lý vai trò

Admin vào **Quản trị → Vai trò** tại `/administration/roles`:

1. Xem danh sách, tìm vai trò theo tên hoặc mã. Danh sách hiển thị số quyền và số tài khoản đang sử dụng, kể cả tài khoản bị khóa.
2. Bấm **Tạo vai trò**, nhập tên và mã rồi lưu. Vai trò mới chưa có quyền và chưa được gán tài khoản.
3. Bấm **Phân quyền** tại dòng vai trò để mở đúng vai trò trong trang phân quyền; chọn quyền rồi nhấn **Lưu quyền**.
4. Bấm **Xóa** tại dòng vai trò chưa được sử dụng. Nhập đúng mã vai trò trong hộp xác nhận rồi bấm **Xác nhận xóa**. Hủy hộp thoại sẽ giữ nguyên vai trò.

Cập nhật 06/10/2026: chỉ vai trò **Admin** được bảo vệ không cho xóa. Có thể xóa **Người dùng**, vai trò tùy chỉnh hoặc vai trò HR cũ chưa được gán. Nếu còn tài khoản sử dụng, kể cả tài khoản bị khóa, phải gỡ vai trò khỏi các tài khoản đó trước. Tài khoản đã xóa không chặn thao tác; liên kết cũ được giữ để bảo toàn lịch sử. Sau khi xóa, vai trò biến mất khỏi danh sách, không được gán lại và không tự khôi phục khi khởi động lại hệ thống. Xóa vai trò không xóa tài khoản. Trang Vai trò và các thao tác này chỉ dành cho Admin.

Kiểm tra trang Vai trò ngày 05/10/2026: Frontend build thành công, 64/64 test đạt; Chrome đạt 18/18 bước trên dữ liệu H2 riêng, gồm tạo, mã trùng, tìm kiếm, mở đúng vai trò để phân quyền, hủy/xác nhận xóa, chặn vai trò vừa được tài khoản khác sử dụng và chặn người không phải Admin. Không tạo/xóa vai trò trong PostgreSQL thật khi kiểm thử.

Kiểm tra cập nhật ngày 06/10/2026: Backend `mvnw.cmd -B clean verify` đạt 185/185 test và đóng gói JAR thành công; Frontend `npm.cmd run check` đạt 64/64 test, lint không có lỗi và build thành công. Chrome đạt 15/15 bước trên H2 riêng: xóa Người dùng chưa gán, chặn khi còn tài khoản bị khóa sử dụng, hủy/xác nhận đúng mã, bảo vệ Admin, từ chối gán vai trò đã xóa và tạo tài khoản bằng vai trò khác. Test tích hợp chạy lại bộ khởi tạo hai lần, xác nhận vai trò đã xóa không được khôi phục hoặc cấu hình lại. Các tài khoản và vai trò trong PostgreSQL thật không bị xóa khi kiểm thử. Log: `.runtime/user-role-deletion-backend-build.log`, `.runtime/user-role-deletion-frontend-check.log`; kết quả Chrome: `.runtime/excel-password-e2e/user-role-deletion-results.json`.

## 5. Khởi chạy

Tại PowerShell trong `D:\Project\Hrm`:

```powershell
.\start.cmd
.\status.cmd
```

Truy cập `http://127.0.0.1:5173`. Dừng ứng dụng bằng `stop.cmd`; PostgreSQL và dữ liệu được giữ lại. Nếu cần build lại, dừng ứng dụng trước bằng `stop.cmd`, sau đó chạy `build.cmd` để tránh Windows khóa file JAR đang chạy. Các phiên bản Java, Spring Boot, Node và npm được giữ nguyên.

## 6. Kết quả kiểm thử ngày 02/10/2026

| Phạm vi | Kết quả |
| --- | --- |
| Backend `mvnw.cmd -B verify` | 120 test đạt; đóng gói JAR thành công. |
| Frontend `npm.cmd run check` | 42 test đạt; lint không có lỗi; build thành công. Còn 182 cảnh báo lint và cảnh báo kích thước bundle/Browserslist. |
| Chrome với H2 riêng, cổng 8081/5174 | 18 bước kiểm tra đạt; không có lỗi JavaScript. |

Các bước trên trình duyệt gồm: xác nhận chỉ có hai tab vai trò/tài khoản; đăng nhập Admin; tạo nhóm; lưu quyền xem nhân viên; gán nhóm cho tài khoản; đăng nhập tài khoản được gán; xác nhận API từ chối thao tác chưa được cấp; bỏ quyền trong nhóm và kiểm tra JWT cũ bị từ chối; kiểm tra ngoại lệ Cho phép/Chặn; xác nhận API cấp quyền phòng ban trả 404; liên kết nhân viên với phòng ban không cấp thêm quyền; kiểm tra nút/menu và URL quản trị; thu hồi quyền khi người dùng quay lại cửa sổ.

Các test tích hợp còn kiểm tra quyền phòng ban cũ không có tác dụng ngay cả khi bảng cũ vẫn còn dữ liệu; chuyển/xóa phòng ban không đổi quyền vai trò/tài khoản; hợp nhiều nhóm; nhóm rỗng; giả mạo cấu hình quyền; giới hạn dữ liệu cá nhân và bảo vệ Admin cuối cùng. Các thao tác tạo dữ liệu thử dùng H2 riêng; không tạo tài khoản/nhân viên thử trong PostgreSQL thật.

Đã sao lưu PostgreSQL rồi áp dụng `docs/database/20261002_remove_department_permissions.sql` để xóa riêng bảng `tbl_department_permission` (có 1 cấu hình quyền cũ). Database còn 21 bảng; đối chiếu số dòng và giá trị băm toàn bộ dữ liệu xác nhận 21 bảng còn lại không đổi. Hai bảng quyền đang sử dụng là `tbl_role_permission` và `tbl_user_permission`. Bản sao lưu và kết quả đối chiếu nằm trong `.runtime/no-department-verification/`.

Log và bằng chứng kiểm tra:

- `.runtime/no-department-backend-verify.log`
- `.runtime/no-department-frontend-check.log`
- `.runtime/no-department-e2e/browser-results.json` và các ảnh chụp màn hình cùng thư mục
- `.runtime/no-department-verification/migration.json`
- `.runtime/no-department-verification/live-results.json`
