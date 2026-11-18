# 6. Nghỉ phép

Hệ thống có hai loại đơn trên giao diện: phép năm và nghỉ không lương. Employee thao tác đơn của mình; Admin và Manager quản lý đơn toàn hệ thống. Các giới hạn khác nhau giữa giao diện và Backend được nêu ở cuối tài liệu.

## UC-LEAVE-01. Xem đơn nghỉ phép cá nhân

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem đơn nghỉ phép cá nhân |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Theo dõi yêu cầu nghỉ của bản thân và kết quả xử lý. |
| Điều kiện tiên quyết | Đã đăng nhập; tài khoản liên kết nhân viên. |
| Yêu cầu / Kết quả mong đợi | Hiển thị loại nghỉ, khoảng ngày, số ngày, lý do, trạng thái và phản hồi. |
| Kịch bản chính | 1. Tác nhân mở Yêu cầu nghỉ phép.<br>2. Hệ thống tải danh sách theo nhân viên gắn với tài khoản.<br>3. Tác nhân xem các đơn và trạng thái xử lý.<br>4. Tác nhân tìm kiếm, đổi trang hoặc số dòng khi cần.<br>5. Hệ thống cập nhật danh sách tương ứng. |
| Kịch bản phụ | 2a. Tài khoản chưa liên kết nhân viên → trang không tải danh sách cá nhân.<br>5a. Không có đơn phù hợp → danh sách rỗng.<br>4a. Tác nhân đặt lại tìm kiếm → tải lại danh sách không lọc từ khóa. |

## UC-LEAVE-02. Tạo đơn nghỉ phép cá nhân

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Tạo đơn nghỉ phép cá nhân |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Gửi yêu cầu nghỉ phép năm hoặc nghỉ không lương. |
| Điều kiện tiên quyết | Đã đăng nhập; có hồ sơ nhân viên liên kết. |
| Yêu cầu / Kết quả mong đợi | Tạo đơn ở trạng thái Chờ duyệt, với số ngày nghỉ được tính tự động. |
| Kịch bản chính | 1. Tác nhân chọn Tạo yêu cầu nghỉ phép.<br>2. Hệ thống mở biểu mẫu và điền nhân viên hiện tại.<br>3. Tác nhân chọn loại phép, ngày bắt đầu/kết thúc, nhập lý do; có thể chọn nghỉ nửa ngày và ca sáng/chiều khi nghỉ trong một ngày.<br>4. Tác nhân nhấn Lưu lại.<br>5. Hệ thống kiểm tra dữ liệu, lịch trùng và số dư phép năm nếu áp dụng.<br>6. Hệ thống tính số ngày nghỉ, loại trừ thứ Bảy/Chủ nhật, lưu đơn Chờ duyệt.<br>7. Hệ thống thông báo thành công, đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 3a. Chọn phép năm → hệ thống hiển thị số dư theo năm của ngày bắt đầu.<br>5a. Thiếu nhân viên/loại phép/ngày, ngày kết thúc trước ngày bắt đầu hoặc nghỉ nửa ngày kéo dài nhiều ngày → yêu cầu sửa dữ liệu.<br>5b. Trùng khoảng ngày với đơn chưa bị từ chối hoặc vượt số phép năm còn lại → báo lỗi.<br>6a. Nghỉ nửa ngày hợp lệ → tính 0,5 ngày; chỉ chọn cuối tuần → số ngày tính được bằng 0. |

## UC-LEAVE-03. Tạo hộ đơn nghỉ phép

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Tạo hộ đơn nghỉ phép |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Tạo yêu cầu nghỉ cho một nhân viên được chọn. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền quản lý nghỉ phép; nhân viên tồn tại. |
| Yêu cầu / Kết quả mong đợi | Tạo đơn Chờ duyệt cho đúng nhân viên. |
| Kịch bản chính | 1. Tác nhân mở Phê duyệt nghỉ phép và chọn Tạo hộ đơn nghỉ.<br>2. Hệ thống mở biểu mẫu cho phép tìm và chọn nhân viên.<br>3. Tác nhân chọn nhân viên, loại nghỉ, khoảng ngày, lý do và thông tin nghỉ nửa ngày nếu có.<br>4. Tác nhân nhấn Lưu lại.<br>5. Hệ thống kiểm tra dữ liệu, lịch trùng và số dư phép năm.<br>6. Hệ thống lưu đơn Chờ duyệt, thông báo thành công và cập nhật danh sách. |
| Kịch bản phụ | 5a. Nhân viên không tồn tại hoặc ngày không hợp lệ → báo lỗi.<br>5b. Đơn trùng lịch hoặc không đủ phép năm → từ chối lưu.<br>4a. Tác nhân hủy biểu mẫu → không tạo đơn. |

## UC-LEAVE-04. Cập nhật đơn nghỉ phép

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Cập nhật đơn nghỉ phép |
| Tác nhân | Admin, Manager; Employee sửa đơn của mình |
| Mô tả chức năng | Thay đổi nội dung đơn còn Chờ duyệt. |
| Điều kiện tiên quyết | Đã đăng nhập; đơn tồn tại, thuộc phạm vi được thao tác và đang Chờ duyệt. |
| Yêu cầu / Kết quả mong đợi | Lưu nội dung mới và tính lại số ngày nghỉ; đơn vẫn Chờ duyệt. |
| Kịch bản chính | 1. Tác nhân chọn Sửa trên đơn Chờ duyệt.<br>2. Hệ thống mở biểu mẫu với nội dung hiện có.<br>3. Tác nhân cập nhật thông tin nghỉ và nhấn Lưu lại.<br>4. Hệ thống kiểm tra quyền, trạng thái, ngày nghỉ, trùng lịch và phép năm.<br>5. Hệ thống tính lại số ngày và lưu đơn.<br>6. Hệ thống thông báo thành công, đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 4a. Đơn không còn Chờ duyệt → không cho cập nhật.<br>4b. Đơn không tồn tại hoặc Employee sửa đơn người khác → từ chối.<br>4c. Dữ liệu sai, trùng lịch hoặc vượt phép năm → hiển thị lỗi.<br>3a. Tác nhân hủy biểu mẫu → giữ nguyên đơn. |

## UC-LEAVE-05. Hủy đơn nghỉ phép cá nhân

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Hủy đơn nghỉ phép cá nhân |
| Tác nhân | Admin, Manager, Employee |
| Mô tả chức năng | Gỡ một đơn Chờ duyệt khỏi danh sách yêu cầu cá nhân. |
| Điều kiện tiên quyết | Đã đăng nhập, liên kết nhân viên; đơn của mình đang Chờ duyệt trên giao diện. |
| Yêu cầu / Kết quả mong đợi | Đơn được đánh dấu đã xóa và không còn xuất hiện trong danh sách thông thường. |
| Kịch bản chính | 1. Tác nhân mở Yêu cầu nghỉ phép.<br>2. Tác nhân chọn Hủy đơn trên đơn Chờ duyệt.<br>3. Hệ thống yêu cầu xác nhận.<br>4. Tác nhân xác nhận hủy.<br>5. Hệ thống kiểm tra quyền và đánh dấu xóa đơn.<br>6. Hệ thống thông báo hủy thành công và tải lại danh sách. |
| Kịch bản phụ | 4a. Tác nhân bỏ qua xác nhận → giữ nguyên đơn.<br>5a. Đơn không tồn tại hoặc Employee yêu cầu hủy đơn người khác → từ chối.<br>5b. Lưu thất bại → hiển thị lỗi hủy đơn. |

## UC-LEAVE-06. Xem và tìm kiếm danh sách đơn nghỉ phép

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem và tìm kiếm danh sách đơn nghỉ phép |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Tra cứu đơn của các nhân viên để xử lý nghỉ phép. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền truy cập Phê duyệt nghỉ phép. |
| Yêu cầu / Kết quả mong đợi | Danh sách phân trang hiển thị nhân viên, phòng ban, nội dung nghỉ và kết quả xử lý. |
| Kịch bản chính | 1. Tác nhân mở Phê duyệt nghỉ phép.<br>2. Hệ thống tải danh sách đơn.<br>3. Tác nhân tìm theo mã/tên nhân viên hoặc chọn nhân viên, loại phép, trạng thái trong bộ lọc.<br>4. Tác nhân áp dụng điều kiện tìm kiếm.<br>5. Hệ thống hiển thị các đơn phù hợp.<br>6. Tác nhân chuyển trang, đổi số dòng hoặc đặt lại bộ lọc. |
| Kịch bản phụ | 5a. Không có đơn phù hợp → hiển thị danh sách rỗng.<br>6a. Đặt lại bộ lọc → tải lại danh sách theo điều kiện mặc định. |

## UC-LEAVE-07. Duyệt đơn nghỉ phép

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Duyệt đơn nghỉ phép |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Chấp nhận yêu cầu nghỉ và cập nhật dữ liệu công liên quan. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền duyệt; đơn Chờ duyệt xuất hiện trong danh sách. |
| Yêu cầu / Kết quả mong đợi | Đơn chuyển thành Đã duyệt; phép năm được tính vào số ngày đã dùng; hệ thống thực hiện tính lại công các ngày làm việc liên quan. |
| Kịch bản chính | 1. Tác nhân mở danh sách đơn và kiểm tra yêu cầu.<br>2. Tác nhân chọn Phê duyệt.<br>3. Hệ thống yêu cầu xác nhận; tác nhân đồng ý.<br>4. Hệ thống kiểm tra đơn và kiểm tra lại hạn mức nếu là phép năm.<br>5. Hệ thống lưu trạng thái Đã duyệt và thực hiện cập nhật công cho khoảng nghỉ, bỏ qua cuối tuần.<br>6. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân hủy xác nhận → đơn không đổi.<br>4a. Không tìm thấy đơn hoặc không đủ phép năm → hiển thị lỗi.<br>4b. Đơn đã được duyệt → Backend trả lại đơn hiện tại.<br>5a. Một lần tính công phát sinh lỗi → service bỏ qua lỗi đó; không bảo đảm mọi ngày công đều đã cập nhật chỉ từ thông báo duyệt thành công. |

## UC-LEAVE-08. Từ chối đơn nghỉ phép

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Từ chối đơn nghỉ phép |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Không chấp nhận yêu cầu nghỉ và ghi nhận phản hồi. |
| Điều kiện tiên quyết | Đã đăng nhập với quyền duyệt; đơn đang Chờ duyệt trên giao diện. |
| Yêu cầu / Kết quả mong đợi | Đơn chuyển thành Từ chối và lưu phản hồi nếu được nhập. |
| Kịch bản chính | 1. Tác nhân chọn Từ chối trên đơn Chờ duyệt.<br>2. Hệ thống mở cửa sổ nhập ý kiến phản hồi/lý do từ chối.<br>3. Tác nhân nhập phản hồi nếu cần và xác nhận Từ chối.<br>4. Hệ thống lưu trạng thái Từ chối và nội dung phản hồi.<br>5. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân đóng cửa sổ → giữ nguyên đơn.<br>3b. Để trống phản hồi → luồng hiện tại vẫn cho gửi yêu cầu từ chối.<br>4a. Đơn không tồn tại hoặc lưu thất bại → báo lỗi. |

## UC-LEAVE-09. Xóa đơn nghỉ phép đã xử lý

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xóa đơn nghỉ phép đã xử lý |
| Tác nhân | Admin, Manager |
| Mô tả chức năng | Loại bỏ đơn đã duyệt hoặc đã từ chối khỏi danh sách quản lý. |
| Điều kiện tiên quyết | Đã đăng nhập; đơn tồn tại và đã được xử lý trên giao diện quản lý. |
| Yêu cầu / Kết quả mong đợi | Đơn được đánh dấu xóa; nếu từng được duyệt, hệ thống thực hiện tính lại công liên quan và không tính đơn vào số phép đã dùng. |
| Kịch bản chính | 1. Tác nhân mở Phê duyệt nghỉ phép.<br>2. Tác nhân chọn Xóa ở một đơn đã xử lý.<br>3. Hệ thống hiển thị xác nhận xóa.<br>4. Tác nhân xác nhận.<br>5. Hệ thống đánh dấu xóa đơn và thực hiện cập nhật công nếu đơn từng được duyệt.<br>6. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 4a. Tác nhân hủy xác nhận → giữ nguyên đơn.<br>5a. Không tìm thấy đơn hoặc xóa thất bại → hiển thị lỗi.<br>5b. Tính lại công một ngày lỗi → service bỏ qua lỗi tính công của ngày đó. |

## UC-LEAVE-10. Xem số dư phép năm

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem số dư phép năm |
| Tác nhân | Admin, Manager; Employee xem số dư của mình trong biểu mẫu nghỉ phép |
| Mô tả chức năng | Tra cứu định mức, số ngày đã nghỉ được duyệt và số ngày phép còn lại. |
| Điều kiện tiên quyết | Đã đăng nhập; có hồ sơ nhân viên cần tra cứu. |
| Yêu cầu / Kết quả mong đợi | Hiển thị phép còn lại bằng định mức trừ phép năm đã duyệt trong năm được chọn. |
| Kịch bản chính | 1. Admin hoặc Manager mở Số dư phép năm.<br>2. Hệ thống tải số dư các nhân viên của năm hiện tại.<br>3. Tác nhân chọn năm, tìm kiếm hoặc lọc phòng ban/vị trí.<br>4. Hệ thống hiển thị nhân viên, định mức, số ngày đã dùng và còn lại.<br>5. Tác nhân chuyển trang hoặc đặt lại bộ lọc khi cần. |
| Kịch bản phụ | 1a. Employee mở biểu mẫu nghỉ và chọn Phép năm → hệ thống tải số dư của bản thân theo năm ngày bắt đầu nghỉ; không có trang danh sách số dư cho Employee.<br>4a. Hồ sơ chưa có định mức → dùng mặc định 12 ngày.<br>4b. Không có nhân viên phù hợp → danh sách rỗng.<br>2a. Không tải được số dư → không hiển thị được số dư tương ứng. |

## Đối chiếu phạm vi và giới hạn hiện tại

- Menu Yêu cầu nghỉ phép mở cho mọi vai trò đã khai báo, nhưng các API đơn nghỉ chỉ cho Admin, Manager, Employee. HR (tuyển dụng/lương/chấm công) chỉ có vai trò chuyên môn tương ứng có thể nhìn thấy trang nhưng không hoàn thành luồng; không coi các vai trò này là tác nhân thành công nếu chưa có thêm quyền.
- Employee chỉ có nút sửa/hủy đơn Chờ duyệt. Trang quản lý chỉ có nút duyệt/từ chối/sửa khi Chờ duyệt và nút xóa khi đã xử lý. Backend rộng hơn: xóa không giới hạn trạng thái; duyệt có thể nhận đơn từng bị từ chối; từ chối có thể áp dụng đơn đã duyệt và thực hiện tính lại công. Đây là khả năng gọi Backend trực tiếp, chưa có luồng nút tương ứng cho mọi chuyển trạng thái trên giao diện.
- API lấy một đơn theo mã tồn tại và kiểm tra quyền Employee; giao diện đang dùng dữ liệu của dòng danh sách khi mở biểu mẫu, không có trang xem chi tiết riêng.
- Kiểm tra trùng dựa trên khoảng ngày của đơn chưa bị từ chối, chưa xóa; chưa tách hai ca sáng/chiều khi xét trùng. Vì vậy không mô tả việc tạo hai đơn nửa ngày cùng ngày như luồng được hỗ trợ.
- Ngày phép chỉ loại trừ thứ Bảy/Chủ nhật; không thấy lịch ngày lễ. Phép đã dùng được tính theo năm của ngày bắt đầu đơn; chưa chia đơn vượt năm thành hạn mức của từng năm. Lý do nghỉ và lý do từ chối chưa bị bắt buộc trong mã hiện tại.
- Duyệt phép kích hoạt cập nhật công: phép có lương tạo công nghỉ, phép không lương không tạo công hưởng lương; phần làm việc còn lại của nghỉ nửa ngày được xử lý theo dữ liệu chấm công. Chưa có thao tác gửi thông báo email hoặc duyệt nhiều cấp trong luồng.

## Nguồn đối chiếu

- UC-LEAVE-01–05: `hrm-frontend/src/pages/Leave/MyLeaveRequests.jsx:49`, `:60`, `:128`; `hrm-frontend/src/pages/Leave/components/LeaveRequestForm.jsx:25`, `:60`, `:99`; `hrm-frontend/src/store/useLeaveStore.js:68`; `hrm-backend/src/main/java/hrm/controller/LeaveRequestController.java:75`, `:98`, `:126`; `hrm-backend/src/main/java/hrm/service/impl/LeaveRequestServiceImpl.java:108`, `:133`, `:155`.
- UC-LEAVE-06–09: `hrm-frontend/src/pages/Leave/LeaveRequestList.jsx:112`, `:175`, `:289`, `:396`; `hrm-backend/src/main/java/hrm/controller/LeaveRequestController.java:150`; `hrm-backend/src/main/java/hrm/service/impl/LeaveRequestServiceImpl.java:171`, `:195`.
- UC-LEAVE-10: `hrm-frontend/src/pages/Leave/LeaveBalance.jsx:27`; `hrm-frontend/src/pages/Leave/components/LeaveRequestForm.jsx:99`; `hrm-backend/src/main/java/hrm/service/impl/LeaveRequestServiceImpl.java:214`, `:354`.
- Quy tắc kiểm tra, trạng thái và công: `hrm-backend/src/main/java/hrm/service/impl/LeaveRequestServiceImpl.java:243`; `hrm-backend/src/main/java/hrm/repository/LeaveRequestRepository.java:19`; `hrm-backend/src/main/java/hrm/service/impl/TimesheetServiceImpl.java:187`; `hrm-backend/src/main/java/hrm/model/LeaveRequest.java`; quyền trang tại `hrm-frontend/src/navigationConfig.js`.
