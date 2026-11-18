# 1. Xác thực

Các tác nhân dùng chung chức năng xác thực: Admin, Manager, Employee và HR (tuyển dụng, lương, chấm công). Tài khoản có thể mang nhiều vai trò. Đặc tả được đối chiếu với mã nguồn hiện tại, không bổ sung chức năng đăng ký hoặc khôi phục mật khẩu.

### UC-AUTH-01. Đăng nhập

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Đăng nhập |
| Tác nhân | Admin, Manager, Employee, HR |
| Mô tả chức năng | Xác thực tài khoản để truy cập các chức năng được cấp quyền. |
| Điều kiện tiên quyết | Tài khoản đã được tạo, chưa bị xóa và đang hoạt động. |
| Yêu cầu / Kết quả mong đợi | Người dùng được xác thực; hệ thống lưu phiên đăng nhập và mở trang phù hợp. |
| Kịch bản chính | 1. Người dùng mở trang đăng nhập.<br>2. Hệ thống hiển thị biểu mẫu và tên đăng nhập đã ghi nhớ, nếu có.<br>3. Người dùng nhập tên đăng nhập, mật khẩu; có thể chọn ghi nhớ tên đăng nhập.<br>4. Người dùng nhấn Đăng nhập.<br>5. Hệ thống kiểm tra thông tin bắt buộc, đối chiếu tài khoản và trạng thái hoạt động.<br>6. Hệ thống lấy thông tin người dùng, vai trò và lưu phiên đăng nhập.<br>7. Hệ thống thông báo thành công, chuyển về trang được yêu cầu trước đó hoặc trang mặc định theo vai trò. |
| Kịch bản phụ | 5a. Thiếu tên đăng nhập hoặc mật khẩu → hiển thị lỗi tại trường và yêu cầu bổ sung.<br>5b. Sai thông tin hoặc tài khoản đã bị xóa → thông báo đăng nhập không thành công.<br>5c. Tài khoản bị khóa → thông báo tài khoản bị khóa hoặc vô hiệu hóa.<br>5d. Không kết nối được máy chủ → thông báo lỗi kết nối.<br>6a. Không lấy được thông tin người dùng → xóa phiên vừa tạo và thông báo lỗi.<br>7a. Tài khoản không có vai trò phù hợp hoặc trang đích không được phép truy cập → hiển thị trang không có quyền truy cập. |

### UC-AUTH-02. Đăng xuất

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Đăng xuất |
| Tác nhân | Admin, Manager, Employee, HR |
| Mô tả chức năng | Kết thúc phiên sử dụng trên trình duyệt hiện tại. |
| Điều kiện tiên quyết | Người dùng đang đăng nhập. |
| Yêu cầu / Kết quả mong đợi | Thông tin đăng nhập trên trình duyệt được xóa; người dùng trở về trang đăng nhập. |
| Kịch bản chính | 1. Người dùng mở menu tài khoản ở thanh đầu trang.<br>2. Người dùng chọn Đăng xuất.<br>3. Hệ thống hiển thị hộp thoại xác nhận.<br>4. Người dùng xác nhận Đăng xuất.<br>5. Hệ thống gửi yêu cầu đăng xuất, xóa trạng thái đăng nhập trên trình duyệt.<br>6. Hệ thống chuyển về trang đăng nhập và tải lại trang. |
| Kịch bản phụ | 4a. Người dùng chọn Hủy → đóng hộp thoại, giữ phiên hiện tại.<br>5a. Yêu cầu đăng xuất tới máy chủ thất bại → trình duyệt vẫn xóa phiên và trở về trang đăng nhập. |

## Ghi chú phạm vi và hành vi hiện tại

- Ghi nhớ đăng nhập chỉ ghi nhớ **tên đăng nhập**, không lưu mật khẩu vào mục ghi nhớ.
- Làm mới phiên được thực hiện tự động khi một yêu cầu bị từ chối xác thực; nếu không làm mới được, hệ thống xóa phiên và yêu cầu đăng nhập lại. Đây là xử lý hỗ trợ, không có màn hình hoặc thao tác độc lập để người dùng làm mới phiên.
- Đăng xuất hiện xóa phiên phía trình duyệt; máy chủ chưa có cơ chế thu hồi toàn bộ mã đăng nhập đã cấp. Không đặc tả đăng xuất tất cả thiết bị.
- Không có luồng đăng ký, quên mật khẩu, đặt lại mật khẩu hoặc tự đổi mật khẩu trên giao diện.

## Đối chiếu mã nguồn

| Phạm vi | Nguồn đối chiếu |
| --- | --- |
| Biểu mẫu, kiểm tra và ghi nhớ tên đăng nhập | `hrm-frontend/src/pages/auth/LoginPage.jsx:31`, `:39`, `:47` |
| Tạo và xóa phiên; lấy người dùng hiện tại | `hrm-frontend/src/store/useAuthStore.js:19`, `:65`, `:87` |
| Menu và xác nhận đăng xuất | `hrm-frontend/src/layout/components/LayoutHeader.jsx:246`, `:442` |
| Trang mặc định, kiểm tra vai trò | `hrm-frontend/src/utils/auth.js:3`, `:10`, `:19`; `hrm-frontend/src/components/auth/AuthGuard.jsx:5` |
| Làm mới phiên tự động | `hrm-frontend/src/services/createHttpClient.js:40`; `hrm-frontend/src/services/HttpService.js:17` |
| Xác thực, làm mới, đăng xuất phía máy chủ | `hrm-backend/src/main/java/hrm/controller/AuthController.java:48`, `:69`, `:91`, `:105` |
| Trạng thái tài khoản và thông báo lỗi | `hrm-backend/src/main/java/hrm/model/User.java:59`; `hrm-backend/src/main/java/hrm/security/UserDetailsServiceImpl.java:20`; `hrm-backend/src/main/java/hrm/exception/GlobalExceptionHandler.java:21` |
