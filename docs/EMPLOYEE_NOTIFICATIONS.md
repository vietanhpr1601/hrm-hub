# Thông báo kết quả phê duyệt cho nhân viên

## Cách sử dụng

1. Admin hoặc tài khoản có quyền phê duyệt duyệt chấm công/đơn nghỉ phép như trước.
2. Khi giao dịch phê duyệt thành công, hệ thống lưu thông báo cho tài khoản đang liên kết với nhân viên của hồ sơ.
3. Nhân viên được cấp **Xem thông báo của mình** bấm biểu tượng chuông hoặc vào **Trang chủ → Thông báo**. Chuông hiển thị số thông báo chưa đọc.
4. Chọn **Xem chi tiết** để mở đúng ngày chấm công hoặc xem đơn nghỉ trong hộp thoại. Chi tiết nghỉ có làm bù vẫn hiển thị các ngày làm bù đã chọn.
5. Có thể đánh dấu từng thông báo hoặc tất cả là đã đọc; lọc **Tất cả / Chưa đọc** và chuyển trang. Trạng thái được lưu trong database, giữ lại sau khi đăng nhập lại.

Ví dụ nội dung: “Chấm công ngày 06/10/2026 của bạn đã được phê duyệt.” hoặc “Đơn nghỉ phép từ ngày 12/10/2026 đến ngày 13/10/2026 của bạn đã được phê duyệt.”

Chuông và hộp thư cập nhật mỗi 30 giây khi cửa sổ đang hiển thị, khi quay lại cửa sổ hoặc khi bấm **Tải lại**. Không cần nâng phiên bản công nghệ hoặc cài thêm thư viện.

## Phạm vi và quyền truy cập

- Từ 08/10/2026, hộp thư yêu cầu tài khoản hoạt động đã đăng nhập và quyền **Xem thông báo của mình** (`NOTIFICATION_VIEW`). Admin cấp qua vai trò hoặc ngoại lệ tài khoản trong **Quản trị → Phân quyền**. Mỗi tài khoản chỉ xem và đánh dấu thông báo của mình; Admin cũng không đọc hộp thư của người khác qua API này.
- Quyền bị thu hồi sẽ ẩn menu/chuông, chặn truy cập trực tiếp và cả bốn API bên dưới. Giao diện xóa dữ liệu đang giữ, dừng tải định kỳ và bỏ qua phản hồi cũ sau khi nhận thay đổi quyền. Thông báo phê duyệt vẫn được lưu trong Database để người nhận xem khi được cấp lại quyền.
- Mở chi tiết hồ sơ vẫn yêu cầu quyền xem cá nhân tương ứng và hồ sơ nhân viên đang liên kết. Thông báo không tự cấp thêm quyền xem dữ liệu.
- Chỉ tạo thông báo cho lần chuyển sang **Đã duyệt**. Gửi lại yêu cầu duyệt một hồ sơ vốn đã duyệt không tạo bản tin trùng; hai yêu cầu duyệt đồng thời được tuần tự hóa bằng khóa dòng.
- Chấm công tự tính khi duyệt nghỉ không sinh thêm thông báo “chấm công được duyệt”. Nếu hồ sơ đổi trạng thái rồi được duyệt lại, hệ thống ghi nhận lần phê duyệt mới.
- Duyệt thất bại hoặc giao dịch bị hoàn tác không lưu thông báo thành công.
- Nhân viên chưa có tài khoản vẫn được phê duyệt bình thường. Tài khoản đã xóa không nhận; tài khoản đang khóa vẫn lưu lịch sử và chỉ truy cập được sau khi mở khóa.
- Thông báo giữ tại tài khoản nhận ban đầu nếu sau này đổi liên kết nhân viên. Nội dung ghi lại sự kiện lúc phê duyệt; chi tiết hồ sơ hiển thị trạng thái hiện tại.
- Áp dụng cho các lần phê duyệt mới sau khi cập nhật. Không tự tạo thông báo cho hồ sơ đã duyệt trước đó. Đợt này triển khai thông báo duyệt chấm công/nghỉ phép; chưa triển khai bảng tin nội bộ, thông báo lương hoặc thông báo từ chối.

## Lưu trữ và API

Thêm bảng `tbl_notification` vào PostgreSQL hiện có; không xóa hoặc tạo lại database. Bảng lưu tài khoản nhận, loại thông báo, nội dung, thời điểm tạo/đọc, ID và ngày hồ sơ liên quan. Khóa ngoại tới tài khoản không cascade xóa. Có chỉ mục theo người nhận/thời điểm và người nhận/trạng thái đọc.

| API | Công dụng |
| --- | --- |
| `GET /api/notifications?pageIndex=1&pageSize=10&unreadOnly=false` | Danh sách riêng của tài khoản, mới nhất trước. |
| `GET /api/notifications/unread-count` | Số thông báo chưa đọc. |
| `PUT /api/notifications/{id}/read` | Đánh dấu một thông báo của mình đã đọc. |
| `PUT /api/notifications/read-all` | Đánh dấu toàn bộ thông báo của mình đã đọc. |

Không nhận ID người dùng từ client để chọn hộp thư; người nhận được xác định từ phiên đăng nhập. Trạng thái giao diện được xóa khi đổi tài khoản hoặc mất quyền; phản hồi cũ không ghi đè dữ liệu tài khoản mới hoặc khôi phục dữ liệu đã bị thu hồi.

## Kiểm chứng ngày 07/10/2026

- Backend `mvnw.cmd -B verify`: 197/197 test đạt, đóng gói JAR thành công; gồm 12 test thông báo kiểm tra người nhận, trạng thái đọc, giao dịch hoàn tác và phê duyệt đồng thời.
- Frontend `npm.cmd run check`: 71/71 test đạt, lint 0 lỗi (168 cảnh báo hiện có), build thành công. Giữ nguyên các phiên bản công nghệ.
- Chrome trên H2 riêng: 19/19 bước đạt, gồm Admin duyệt cả hai loại hồ sơ qua giao diện, nhân viên vai trò tùy chỉnh nhận thông báo, đọc chi tiết, cập nhật khi quay lại cửa sổ, đăng nhập lại và cô lập tài khoản khác. Đã kiểm tra giao diện máy tính và điện thoại.
- PostgreSQL: sao lưu trước cập nhật tại `.runtime/notification-database/`; thêm đúng bảng `tbl_notification`. Đối chiếu số dòng và giá trị băm xác nhận dữ liệu của cả 22 bảng cũ không đổi. Không tạo hồ sơ kiểm thử trong database thật.
- Ứng dụng chính đã khởi động lại; Chrome mở được màn hình đăng nhập, Backend phản hồi đúng và không có lỗi JavaScript khi tải trang.

Bằng chứng: `.runtime/notification-backend-build.log`, `.runtime/notification-frontend-check.log`, `.runtime/excel-password-e2e/notification-results.json`, `.runtime/notification-database/after.json`. Ảnh giao diện kiểm thử có tiền tố `employee-notification-` trong `.runtime/excel-password-e2e/`.
