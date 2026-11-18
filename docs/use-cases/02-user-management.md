# 2. Quản lý tài khoản

Tác nhân dưới đây là người được giao diện cho phép truy cập mục **Quản trị → Tài khoản**: Admin. Một tài khoản có thể liên kết với hồ sơ nhân viên và được gán nhiều vai trò.

### UC-USER-01. Xem danh sách tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem danh sách tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Xem các tài khoản chưa bị xóa theo từng trang. |
| Điều kiện tiên quyết | Admin đã đăng nhập. |
| Yêu cầu / Kết quả mong đợi | Danh sách tài khoản, thông tin liên kết, vai trò và trạng thái hoạt động được hiển thị. |
| Kịch bản chính | 1. Admin chọn mục Tài khoản.<br>2. Hệ thống tải và hiển thị danh sách cùng tổng số tài khoản.<br>3. Admin chọn trang hoặc số dòng mỗi trang.<br>4. Hệ thống hiển thị dữ liệu tương ứng. |
| Kịch bản phụ | 2a. Chưa có tài khoản phù hợp → hiển thị danh sách rỗng.<br>2b. Tải dữ liệu thất bại → không nhận được danh sách mới; giao diện hiện chưa có thông báo lỗi riêng cho thao tác này. |

### UC-USER-02. Tìm kiếm và lọc tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Tìm kiếm và lọc tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Tra cứu tài khoản theo từ khóa, trạng thái hoạt động, vai trò, phòng ban hoặc vị trí của nhân viên liên kết. |
| Điều kiện tiên quyết | Admin đang ở danh sách tài khoản. |
| Yêu cầu / Kết quả mong đợi | Danh sách chỉ hiển thị tài khoản thỏa mãn các điều kiện đã áp dụng. |
| Kịch bản chính | 1. Admin nhập từ khóa theo tên đăng nhập, họ tên hoặc mã nhân viên.<br>2. Admin có thể mở bộ lọc và chọn trạng thái, vai trò, phòng ban, vị trí.<br>3. Admin nhấn tìm kiếm hoặc áp dụng bộ lọc.<br>4. Hệ thống tìm dữ liệu, đưa danh sách về trang đầu và hiển thị kết quả. |
| Kịch bản phụ | 3a. Admin chọn đặt lại → xóa từ khóa, điều kiện lọc và tải lại danh sách.<br>4a. Không có tài khoản thỏa mãn → hiển thị danh sách rỗng. |

### UC-USER-03. Xem chi tiết tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem chi tiết tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Xem tên đăng nhập, nhân viên liên kết và vai trò của tài khoản đã chọn. |
| Điều kiện tiên quyết | Admin đã tải được danh sách tài khoản. |
| Yêu cầu / Kết quả mong đợi | Thông tin tài khoản được hiển thị ở chế độ chỉ xem; mật khẩu không được hiển thị. |
| Kịch bản chính | 1. Admin chọn biểu tượng xem tại một tài khoản.<br>2. Hệ thống mở biểu mẫu chi tiết từ dữ liệu của dòng đã chọn.<br>3. Admin xem thông tin.<br>4. Admin chọn Đóng để trở về danh sách. |
| Kịch bản phụ | 2a. Tài khoản chưa liên kết nhân viên hoặc chưa có vai trò → trường tương ứng để trống. |

### UC-USER-04. Thêm tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Thêm tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Tạo tài khoản đăng nhập, lựa chọn nhân viên liên kết và các vai trò. |
| Điều kiện tiên quyết | Admin đã đăng nhập và mở danh sách tài khoản. |
| Yêu cầu / Kết quả mong đợi | Tài khoản mới được lưu với trạng thái hoạt động mặc định; mật khẩu được lưu dưới dạng mã hóa một chiều. |
| Kịch bản chính | 1. Admin chọn thêm tài khoản.<br>2. Hệ thống mở biểu mẫu và tải danh sách vai trò.<br>3. Admin nhập tên đăng nhập, mật khẩu; có thể chọn nhân viên liên kết và một hoặc nhiều vai trò.<br>4. Admin nhấn Lưu lại.<br>5. Hệ thống kiểm tra tên đăng nhập và mật khẩu bắt buộc, sau đó lưu tài khoản.<br>6. Hệ thống đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 3a. Admin chọn nhân viên khi tên đăng nhập đang trống → hệ thống điền tên đăng nhập bằng mã nhân viên nếu có.<br>4a. Admin chọn Hủy bỏ → đóng biểu mẫu, không lưu.<br>5a. Thiếu tên đăng nhập hoặc mật khẩu → hiển thị lỗi tại trường và yêu cầu bổ sung.<br>5b. Tên đăng nhập trùng hoặc vi phạm ràng buộc liên kết khi lưu → máy chủ từ chối; giao diện hiện chưa chuyển lỗi này thành thông báo nghiệp vụ và biểu mẫu có thể vẫn đóng. |

### UC-USER-05. Cập nhật thông tin tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Cập nhật thông tin tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Chỉnh sửa tên đăng nhập, nhân viên liên kết và các vai trò của tài khoản. |
| Điều kiện tiên quyết | Admin đã đăng nhập; tài khoản chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Thông tin tài khoản được cập nhật; mật khẩu hiện tại được giữ nguyên. |
| Kịch bản chính | 1. Admin chọn sửa tài khoản.<br>2. Hệ thống hiển thị thông tin hiện tại.<br>3. Admin sửa tên đăng nhập, thay đổi hoặc bỏ liên kết nhân viên và điều chỉnh vai trò theo nhu cầu.<br>4. Admin nhấn Lưu lại.<br>5. Hệ thống kiểm tra tên đăng nhập bắt buộc và lưu thay đổi.<br>6. Hệ thống đóng biểu mẫu, tải lại danh sách. |
| Kịch bản phụ | 4a. Admin chọn Hủy bỏ → đóng biểu mẫu, không lưu thay đổi.<br>5a. Tên đăng nhập để trống → yêu cầu nhập lại.<br>5b. Tài khoản không còn tồn tại, tên đăng nhập trùng hoặc lưu thất bại → máy chủ trả lỗi; giao diện hiện có thể đóng biểu mẫu dù thao tác chưa thành công. |

### UC-USER-06. Thay đổi vai trò tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Thay đổi vai trò tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Gán hoặc bỏ các vai trò thông qua biểu mẫu cập nhật tài khoản. |
| Điều kiện tiên quyết | Admin đã đăng nhập; tài khoản cần phân quyền chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Tập vai trò mới được lưu cho tài khoản. |
| Kịch bản chính | 1. Admin chọn sửa tài khoản.<br>2. Hệ thống tải các vai trò có sẵn và hiển thị vai trò hiện tại.<br>3. Admin chọn thêm hoặc bỏ các vai trò.<br>4. Admin nhấn Lưu lại.<br>5. Hệ thống thay thế tập vai trò hiện tại bằng lựa chọn đã gửi.<br>6. Hệ thống đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 3a. Admin bỏ toàn bộ vai trò → hệ thống cho phép gửi danh sách vai trò rỗng; tài khoản không được cấp thêm quyền mặc định.<br>4a. Admin chọn Hủy bỏ → không lưu thay đổi.<br>5a. Lưu thất bại → chưa cập nhật vai trò; giao diện hiện có thể vẫn đóng biểu mẫu. |

### UC-USER-07. Khóa tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Khóa tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Chuyển tài khoản đang hoạt động sang trạng thái bị khóa. |
| Điều kiện tiên quyết | Admin đã đăng nhập; tài khoản đang hoạt động và chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Tài khoản bị vô hiệu hóa; lần xác thực tiếp theo bị từ chối. |
| Kịch bản chính | 1. Admin chọn thao tác khóa tại tài khoản đang hoạt động.<br>2. Hệ thống hiển thị xác nhận khóa kèm tên đăng nhập.<br>3. Admin xác nhận.<br>4. Hệ thống lưu trạng thái bị khóa.<br>5. Hệ thống đóng hộp thoại và tải lại danh sách. |
| Kịch bản phụ | 3a. Admin chọn Hủy bỏ → giữ nguyên trạng thái.<br>4a. Cập nhật thất bại → hộp thoại chưa đóng, trạng thái chưa được cập nhật; giao diện hiện không có thông báo lỗi riêng. |

### UC-USER-08. Mở khóa tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Mở khóa tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Cho phép tài khoản bị khóa hoạt động trở lại. |
| Điều kiện tiên quyết | Admin đã đăng nhập; tài khoản đang bị khóa và chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Tài khoản trở về trạng thái hoạt động và có thể đăng nhập bằng thông tin hợp lệ. |
| Kịch bản chính | 1. Admin chọn mở khóa tại tài khoản bị khóa.<br>2. Hệ thống hiển thị xác nhận mở khóa.<br>3. Admin xác nhận.<br>4. Hệ thống lưu trạng thái hoạt động.<br>5. Hệ thống đóng hộp thoại và tải lại danh sách. |
| Kịch bản phụ | 3a. Admin chọn Hủy bỏ → tài khoản vẫn bị khóa.<br>4a. Cập nhật thất bại → hộp thoại chưa đóng, trạng thái chưa được cập nhật; giao diện hiện không có thông báo lỗi riêng. |

### UC-USER-09. Xóa tài khoản

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xóa tài khoản |
| Tác nhân | Admin |
| Mô tả chức năng | Loại tài khoản khỏi danh sách quản lý và ngăn sử dụng tài khoản để xác thực. |
| Điều kiện tiên quyết | Admin đã đăng nhập; tài khoản chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Tài khoản được đánh dấu đã xóa và không còn xuất hiện trong danh sách. |
| Kịch bản chính | 1. Admin chọn xóa tại tài khoản cần xóa.<br>2. Hệ thống yêu cầu xác nhận.<br>3. Admin xác nhận xóa.<br>4. Hệ thống kiểm tra sự tồn tại và đánh dấu tài khoản đã xóa.<br>5. Hệ thống tải lại danh sách, đóng hộp thoại xác nhận. |
| Kịch bản phụ | 3a. Admin chọn Hủy bỏ → không xóa tài khoản.<br>4a. Tài khoản không còn tồn tại hoặc xóa thất bại → máy chủ trả lỗi; hộp thoại hiện có thể vẫn đóng do cách xử lý lỗi ở giao diện. |

## Ghi chú phạm vi và hành vi hiện tại

- Giao diện giới hạn module cho Admin, nhưng `UserController` và `UserServiceImpl` chưa kiểm tra vai trò riêng; máy chủ chỉ áp dụng yêu cầu đăng nhập chung. Không diễn giải đây là cơ chế phân quyền Admin đầy đủ ở cả hai phía.
- Nhân viên liên kết và vai trò không được biểu mẫu đánh dấu là điều kiện bắt buộc. Không có kiểm tra nghiệp vụ riêng với thông báo “nhân viên đã liên kết tài khoản khác”; lỗi ràng buộc dữ liệu có thể được trả về dưới dạng lỗi hệ thống.
- Các thao tác thêm, sửa và xóa trong `userStore` bắt lỗi nhưng không truyền lỗi tiếp lên biểu mẫu. Vì vậy, đóng biểu mẫu/hộp thoại không phải bằng chứng lưu thành công. Các kịch bản chính mô tả trường hợp máy chủ lưu thành công.
- Mật khẩu chỉ có trường nhập khi tạo mới. Máy chủ có khả năng nhận mật khẩu khi cập nhật, nhưng giao diện sửa không cung cấp trường này; không có Use Case đổi mật khẩu độc lập.
- Chưa có chức năng xóa nhiều tài khoản, khôi phục tài khoản, quản lý phiên hoặc kiểm tra cấm tự khóa/tự xóa tài khoản trên giao diện. Menu **Vai trò** hiện là trang đang phát triển; danh sách vai trò chỉ phục vụ chọn quyền trong biểu mẫu và bộ lọc.
- Vai trò lưu trong phiên giao diện không được chủ động làm mới ngay khi tài khoản bị sửa quyền. Không đặc tả việc mọi menu ở phiên đang mở đổi quyền tức thời.

## Đối chiếu mã nguồn

| Phạm vi | Nguồn đối chiếu |
| --- | --- |
| Quyền vào module | `hrm-frontend/src/navigationConfig.js:204`; `hrm-frontend/src/App.jsx:145` |
| Danh sách, lọc, xem, sửa, xóa, khóa | `hrm-frontend/src/pages/User/UserList.jsx:97`, `:141`, `:153`, `:164`, `:181`, `:288`, `:356` |
| Biểu mẫu, trường bắt buộc, chọn nhân viên và nhiều vai trò | `hrm-frontend/src/pages/User/UserForm.jsx:20`, `:29`, `:38`, `:122`, `:134`, `:158` |
| Lưu và xử lý lỗi | `hrm-frontend/src/store/userStore.js:77`, `:86`, `:96`; `hrm-frontend/src/services/UserService.js:22` |
| Xử lý tài khoản và điều kiện lọc | `hrm-backend/src/main/java/hrm/controller/UserController.java:19`; `hrm-backend/src/main/java/hrm/service/impl/UserServiceImpl.java:38`, `:122`, `:129`, `:143` |
| Ràng buộc tài khoản, liên kết nhân viên và trạng thái xác thực | `hrm-backend/src/main/java/hrm/model/User.java:25`, `:35`, `:59`; `hrm-backend/src/main/java/hrm/security/JwtAuthenticationFilter.java:32`; `hrm-backend/src/main/java/hrm/security/UserDetailsServiceImpl.java:20` |
| Danh sách vai trò; phân biệt trang đang phát triển | `hrm-backend/src/main/java/hrm/controller/RoleController.java:20`; `hrm-frontend/src/App.jsx:57`, `:99`, `:149` |
