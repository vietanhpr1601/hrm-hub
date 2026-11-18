# 5. Chấm công

Các Use Case dưới đây mô tả giao diện hiện có. Admin, Manager và Employee có thể chấm công cho hồ sơ gắn với tài khoản của mình. HR (chấm công) có trang thống kê công; quyền duyệt riêng của vai trò này ở Backend được ghi tại phần đối chiếu cuối tài liệu.

## UC-ATT-01. Check-in

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Check-in (vào ca) |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Ghi nhận lượt vào ca kèm vị trí thực tế, độ chính xác GPS và IP. |
| Điều kiện tiên quyết | Đã đăng nhập; tài khoản liên kết nhân viên và có quyền chấm công; trình duyệt được phép định vị. |
| Yêu cầu / Kết quả mong đợi | Lưu lượt vào ca và tính lại công của ngày được chọn. |
| Kịch bản chính | 1. Tác nhân mở chức năng chấm công trên thanh đầu trang.<br>2. Hệ thống tải ca làm việc, thời gian và yêu cầu vị trí từ trình duyệt.<br>3. Tác nhân chọn Vào ca và ca làm việc.<br>4. Tác nhân gửi và xác nhận chấm công.<br>5. Hệ thống lấy lại vị trí và kiểm tra dữ liệu.<br>6. Hệ thống lưu lượt vào theo thời gian máy chủ, GPS, độ chính xác, IP và khoảng cách nếu có cấu hình; tính lại ngày công.<br>7. Hệ thống thông báo thành công, đóng cửa sổ và cập nhật lịch tháng. |
| Kịch bản phụ | 2a. Không cấp quyền hoặc không lấy được vị trí → thông báo nguyên nhân, chặn gửi.<br>4a. Tác nhân hủy xác nhận → chưa ghi nhận lượt vào.<br>5a. Vị trí không hợp lệ hoặc đã cũ → yêu cầu lấy lại.<br>6a. Lưu thất bại → thông báo lỗi.<br>6b. Chưa có lượt ra tương ứng → công của ca chưa được tính đủ; nếu bổ sung công thực tế vào ngày đã có kết quả duyệt thì gửi duyệt lại. |

## UC-ATT-02. Check-out

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Check-out (ra ca) |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Ghi nhận lượt ra ca và cập nhật kết quả chấm công trong ngày. |
| Điều kiện tiên quyết | Đã đăng nhập và liên kết nhân viên; có quyền chấm công và cho phép trình duyệt định vị. |
| Yêu cầu / Kết quả mong đợi | Lưu lượt ra; cập nhật giờ ra, công, giờ làm và thông tin đi muộn/về sớm theo dữ liệu thực tế. |
| Kịch bản chính | 1. Tác nhân mở cửa sổ chấm công.<br>2. Hệ thống lấy vị trí; tác nhân chọn Ra ca và ca làm việc.<br>3. Tác nhân gửi chấm công.<br>4. Hệ thống yêu cầu xác nhận; tác nhân đồng ý.<br>5. Hệ thống lấy lại vị trí, kiểm tra dữ liệu và lưu lượt ra theo thời gian máy chủ cùng GPS, độ chính xác, IP, khoảng cách nếu có cấu hình.<br>6. Hệ thống tính lại ngày công, thông báo thành công và cập nhật lịch tháng. |
| Kịch bản phụ | 2a. Không lấy được vị trí → thông báo nguyên nhân và chặn chấm công.<br>4a. Ngày công đã duyệt hoặc bị từ chối → cảnh báo công thực tế sẽ được gửi duyệt lại; tác nhân có thể tiếp tục hoặc hủy.<br>5a. Thiếu nhân viên hoặc GPS không hợp lệ, quá cũ → hiển thị lỗi.<br>5b. Lưu thất bại → thông báo lỗi.<br>6a. Thiếu lượt vào hoặc giờ ra không sau giờ vào → công ca chưa được tính đủ. |

## UC-ATT-03. Xem lịch sử chấm công cá nhân

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem lịch sử chấm công cá nhân |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Theo dõi công của bản thân theo tháng bằng lịch hoặc bảng. |
| Điều kiện tiên quyết | Đã đăng nhập; tài khoản liên kết nhân viên. |
| Yêu cầu / Kết quả mong đợi | Hiển thị công, giờ chuẩn, giờ tăng ca, phút đi muộn/về sớm và trạng thái ngày công. |
| Kịch bản chính | 1. Tác nhân mở Chi tiết chấm công.<br>2. Hệ thống tải dữ liệu tháng hiện tại của nhân viên đăng nhập.<br>3. Tác nhân chuyển tháng hoặc chuyển chế độ lịch/bảng.<br>4. Hệ thống hiển thị các ngày và số liệu tổng hợp tháng.<br>5. Tác nhân chọn ngày cần đối chiếu để mở chi tiết. |
| Kịch bản phụ | 2a. Tài khoản chưa liên kết nhân viên → không tải được lịch công cá nhân.<br>4a. Ngày chưa có dữ liệu → không hiển thị công đã ghi nhận cho ngày đó.<br>5a. Tác nhân mở ngày trống → cửa sổ chi tiết không có bản ghi chấm công tương ứng. |

## UC-ATT-04. Xem chi tiết ngày công và vị trí

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem chi tiết ngày công và vị trí |
| Tác nhân | Admin, Manager; Employee xem dữ liệu của mình qua lịch cá nhân |
| Mô tả chức năng | Đối chiếu giờ vào/ra, tổng giờ, đi muộn/về sớm/tăng ca, GPS và các đơn nghỉ hoặc ngày làm bù liên quan. |
| Điều kiện tiên quyết | Đã đăng nhập; mở lịch cá nhân hoặc danh sách phê duyệt công có quyền truy cập. |
| Yêu cầu / Kết quả mong đợi | Hiển thị dữ liệu ngày được chọn và các lượt chấm công thô. |
| Kịch bản chính | 1. Tác nhân chọn một ngày trong lịch hoặc mở chi tiết trên danh sách phê duyệt.<br>2. Hệ thống mở cửa sổ chi tiết.<br>3. Hệ thống tải nhật ký và đơn nghỉ/ngày làm bù liên quan.<br>4. Tác nhân xem tổng giờ, công, ca, giờ vào/ra, muộn/sớm/tăng ca, trạng thái, ghi chú, tọa độ, độ chính xác GPS, IP và khoảng cách tới nơi làm việc.<br>5. Tác nhân đóng cửa sổ. |
| Kịch bản phụ | 3a. Không có lượt chấm công hoặc đơn liên quan → hiển thị thông báo tương ứng.<br>3b. Người dùng chỉ có quyền cá nhân yêu cầu dữ liệu của người khác → từ chối.<br>4a. Chưa có đủ giờ vào/ra hoặc dữ liệu GPS cũ còn thiếu → không tự điền dữ liệu giả.<br>4b. Chưa cấu hình địa điểm làm việc → khoảng cách hiển thị Chưa cấu hình. |

## UC-ATT-05. Xem và tìm kiếm danh sách ngày công

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem và tìm kiếm danh sách ngày công |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Tra cứu các ngày công để kiểm tra và phê duyệt. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền truy cập Phê duyệt chấm công. |
| Yêu cầu / Kết quả mong đợi | Danh sách ngày công được phân trang và lọc đúng tiêu chí. |
| Kịch bản chính | 1. Tác nhân mở Phê duyệt chấm công.<br>2. Hệ thống tải danh sách ngày công và dữ liệu bộ lọc.<br>3. Tác nhân nhập mã/tên nhân viên hoặc chọn phòng ban, nhân viên, kỳ lương, khoảng ngày, trạng thái.<br>4. Tác nhân áp dụng bộ lọc.<br>5. Hệ thống hiển thị danh sách phù hợp.<br>6. Tác nhân chuyển trang, đổi số dòng hoặc đặt lại bộ lọc khi cần. |
| Kịch bản phụ | 5a. Không có ngày công phù hợp → danh sách rỗng.<br>5b. Kỳ lương được chọn không còn tồn tại → không trả về ngày công theo kỳ đó.<br>6a. Đặt lại bộ lọc → hệ thống tải lại danh sách theo bộ lọc mặc định. |

## UC-ATT-06. Duyệt chấm công

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Duyệt chấm công |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Xác nhận một ngày công để phục vụ tổng hợp và tính lương. |
| Điều kiện tiên quyết | Đã đăng nhập; ngày công tồn tại và chưa ở trạng thái Đã duyệt trên giao diện. |
| Yêu cầu / Kết quả mong đợi | Ngày công chuyển thành Đã duyệt; lưu ghi chú nếu có. |
| Kịch bản chính | 1. Tác nhân mở danh sách phê duyệt chấm công.<br>2. Tác nhân kiểm tra ngày công và chọn Duyệt công.<br>3. Hệ thống mở cửa sổ xác nhận.<br>4. Tác nhân nhập ghi chú nếu cần và xác nhận.<br>5. Hệ thống cập nhật trạng thái Đã duyệt.<br>6. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 4a. Tác nhân hủy xác nhận → giữ nguyên trạng thái.<br>5a. Không tìm thấy ngày công hoặc cập nhật thất bại → thông báo lỗi.<br>2a. Ngày công từng bị từ chối → giao diện vẫn cho chọn Duyệt công. |

## UC-ATT-07. Từ chối chấm công

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Từ chối chấm công |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Đánh dấu một ngày công không được chấp nhận. |
| Điều kiện tiên quyết | Đã đăng nhập; ngày công tồn tại và chưa ở trạng thái Từ chối trên giao diện. |
| Yêu cầu / Kết quả mong đợi | Ngày công chuyển thành Từ chối; lưu ghi chú nếu có. |
| Kịch bản chính | 1. Tác nhân mở danh sách phê duyệt chấm công.<br>2. Tác nhân kiểm tra ngày công và chọn Từ chối.<br>3. Hệ thống mở cửa sổ xác nhận.<br>4. Tác nhân nhập ghi chú nếu cần và xác nhận.<br>5. Hệ thống cập nhật trạng thái Từ chối.<br>6. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 4a. Tác nhân hủy → giữ nguyên dữ liệu.<br>5a. Không tìm thấy ngày công hoặc lưu thất bại → thông báo lỗi.<br>2a. Ngày công đã được duyệt → giao diện vẫn cho chuyển sang Từ chối. |

## UC-ATT-08. Xem thống kê công

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem thống kê công |
| Tác nhân | Admin, Manager, HR (chấm công) |
| Mô tả chức năng | Tổng hợp công nhiều nhân viên hoặc xem từng ngày của một nhân viên. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền truy cập Thống kê công. |
| Yêu cầu / Kết quả mong đợi | Hiển thị tổng công, giờ chuẩn, giờ tăng ca và số phút đi muộn/về sớm theo phạm vi chọn. |
| Kịch bản chính | 1. Tác nhân mở Thống kê công.<br>2. Hệ thống tải dữ liệu tháng hiện tại.<br>3. Tác nhân tìm theo mã/tên nhân viên hoặc lọc phòng ban, nhân viên, khoảng ngày.<br>4. Tác nhân áp dụng bộ lọc.<br>5. Hệ thống hiển thị tổng hợp theo nhân viên; khi chọn một nhân viên, hiển thị chi tiết theo ngày.<br>6. Tác nhân xem số liệu tổng hoặc làm mới dữ liệu. |
| Kịch bản phụ | 2a. Tải dữ liệu thất bại → thông báo không thể tải dữ liệu chấm công.<br>5a. Chế độ một nhân viên có ngày kết thúc trước ngày bắt đầu hoặc chênh lệch vượt 92 ngày → không tạo danh sách chi tiết ngày.<br>5b. Không có công trong phạm vi → các tổng tương ứng bằng 0. |

## UC-ATT-09. Xuất báo cáo thống kê công

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xuất báo cáo thống kê công |
| Tác nhân | Admin, Manager, HR (chấm công) |
| Mô tả chức năng | Xuất kết quả thống kê hiện tại thành tệp CSV. |
| Điều kiện tiên quyết | Đã mở Thống kê công và xác định phạm vi thống kê. |
| Yêu cầu / Kết quả mong đợi | Tải tệp CSV gồm dữ liệu hiện được tổng hợp và dòng tổng cộng. |
| Kịch bản chính | 1. Tác nhân chọn bộ lọc cho báo cáo.<br>2. Hệ thống hiển thị kết quả thống kê.<br>3. Tác nhân chọn xuất báo cáo.<br>4. Hệ thống tạo báo cáo tổng hợp nhân viên hoặc báo cáo chi tiết cá nhân theo chế độ hiện tại.<br>5. Hệ thống tải tệp CSV về trình duyệt và thông báo thành công. |
| Kịch bản phụ | 4a. Không có dòng dữ liệu → tệp chỉ có thông tin báo cáo, tiêu đề và tổng tương ứng.<br>4b. Tạo báo cáo thất bại → thông báo không thể xuất báo cáo. |

## Đối chiếu phạm vi và giới hạn hiện tại

- Chấm công nhanh có thể chọn thời điểm ghi nhận. Backend hiện không chặn chấm lại ca đã duyệt/từ chối; đoạn chặn đã bị chú thích. Khi tính lại công thông thường, trạng thái Đã duyệt/Từ chối được giữ. Không mô tả bước bắt buộc gửi duyệt lại.
- Ảnh và GPS là dữ liệu bắt buộc; không thấy kiểm tra khu vực GPS hoặc nhận diện khuôn mặt trong luồng này. Không quy định phải check-in trước mới được lưu check-out.
- Backend cho HR (chấm công) tìm, lấy chi tiết và duyệt/từ chối ngày công; giao diện Phê duyệt chấm công chỉ mở cho Admin, Manager. Nhật ký thô lại chỉ mở cho Admin, Manager, Employee ở Backend. Đây là khác biệt phân quyền, không phải luồng HR (chấm công) hoàn chỉnh trên giao diện.
- API xem công theo nhân viên/khoảng ngày và xem theo mã ngày công chưa kiểm tra quyền sở hữu trong service; giao diện lịch cá nhân chỉ truyền nhân viên đăng nhập. Không suy ra Backend đã bảo vệ toàn bộ quyền xem lịch của Employee.
- Thống kê công cộng tất cả trạng thái được trả về; tính lương chỉ sử dụng ngày công Đã duyệt. Vì vậy tổng thống kê có thể khác công tính lương.
- Có hàm lưu/cập nhật ngày công trong service, nhưng không có điểm gọi tạo/sửa ngày công thủ công tại Controller hoặc giao diện đang xét; không đặc tả thành Use Case giao diện.

## Nguồn đối chiếu

- UC-ATT-01–02: `hrm-frontend/src/layout/components/LayoutHeader.jsx:137`, `:275`, `:456`, `:650`; `hrm-frontend/src/pages/Timekeeping/components/WebcamCapture.jsx:5`; `hrm-backend/src/main/java/hrm/controller/CheckInOutController.java:34`; `hrm-backend/src/main/java/hrm/service/impl/CheckInOutServiceImpl.java:44`.
- UC-ATT-03–04: `hrm-frontend/src/pages/Timekeeping/TimekeepingCalendar.jsx:37`, `:144`; `hrm-frontend/src/pages/Timesheet/TimesheetApprovalList.jsx:128`; `hrm-frontend/src/store/useTimesheetStore.js:74`; `hrm-backend/src/main/java/hrm/controller/CheckInOutController.java:42`.
- UC-ATT-05–07: `hrm-frontend/src/pages/Timesheet/TimesheetApprovalList.jsx:100`, `:106`, `:178`; `hrm-backend/src/main/java/hrm/controller/TimesheetController.java:30`; `hrm-backend/src/main/java/hrm/service/impl/TimesheetServiceImpl.java:57`, `:150`.
- UC-ATT-08–09: `hrm-frontend/src/pages/Timekeeping/TimekeepingSummary.jsx:68`, `:149`, `:282`.
- Quy tắc tính công và lưu trữ: `hrm-backend/src/main/java/hrm/service/impl/TimesheetServiceImpl.java:173`; các entity `hrm-backend/src/main/java/hrm/model/Timesheet.java`, `TimesheetDetail.java`, `CheckInOutRecord.java`; quyền trang tại `hrm-frontend/src/navigationConfig.js`.
