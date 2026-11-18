# 08. Tuyển dụng

Tác nhân: **Admin**, **Manager**, **HR (tuyển dụng)**. Các thao tác với hồ sơ ứng viên nằm trong chi tiết một tin tuyển dụng; chế độ **Xem** khóa các thao tác thay đổi hồ sơ, cần chọn **Chỉnh sửa** để thực hiện.

## UC-REC-01. Xem danh sách tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem danh sách tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Theo dõi các tin tuyển dụng chưa bị xóa. |
| Điều kiện tiên quyết | Tác nhân đã đăng nhập và có quyền tuyển dụng. |
| Yêu cầu / Kết quả mong đợi | Hiển thị mã tin, tiêu đề, người duyệt CV, trạng thái và thông tin phân trang. |
| Kịch bản chính | 1. Tác nhân chọn Tin tuyển dụng.<br>2. Hệ thống lấy danh sách tin tuyển dụng.<br>3. Hệ thống hiển thị danh sách và tổng số tin.<br>4. Tác nhân chuyển trang hoặc thay đổi số dòng để xem tiếp. |
| Kịch bản phụ | 2a. Không có tin phù hợp → danh sách rỗng.<br>2b. Tải dữ liệu thất bại → kết thúc trạng thái tải; danh sách không được cập nhật. |

## UC-REC-02. Tìm kiếm và lọc tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Tìm kiếm và lọc tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Thu hẹp danh sách theo từ khóa, mã tin, tiêu đề, người duyệt hồ sơ hoặc trạng thái. |
| Điều kiện tiên quyết | Tác nhân đang ở danh sách tin tuyển dụng. |
| Yêu cầu / Kết quả mong đợi | Danh sách hiển thị các tin thỏa mãn điều kiện đã chọn. |
| Kịch bản chính | 1. Tác nhân nhập từ khóa hoặc mở bộ lọc.<br>2. Tác nhân nhập điều kiện và chọn Tìm kiếm hoặc Áp dụng.<br>3. Hệ thống tìm theo mã, tên, mô tả và kết hợp các bộ lọc đã chọn.<br>4. Hệ thống hiển thị kết quả từ trang đầu. |
| Kịch bản phụ | 2a. Tác nhân chọn đặt lại → xóa từ khóa, bộ lọc và tải lại danh sách.<br>3a. Không có tin phù hợp → hiển thị danh sách rỗng. |

## UC-REC-03. Xem chi tiết tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem chi tiết tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Xem thông tin tin tuyển dụng và các ứng viên thuộc tin đó. |
| Điều kiện tiên quyết | Tác nhân đã đăng nhập; tin tuyển dụng tồn tại và chưa bị xóa. |
| Yêu cầu / Kết quả mong đợi | Hiển thị thông tin chung, mô tả, người duyệt hồ sơ, trạng thái và danh sách ứng viên. |
| Kịch bản chính | 1. Tác nhân chọn Xem chi tiết ở tin tuyển dụng.<br>2. Hệ thống tải thông tin tin và danh sách ứng viên liên quan.<br>3. Hệ thống hiển thị trang chi tiết ở chế độ xem.<br>4. Tác nhân xem dữ liệu hoặc quay lại danh sách. |
| Kịch bản phụ | 2a. Không tìm thấy tin hoặc không tải được dữ liệu → thông báo không thể tải/không tìm thấy thông tin.<br>3a. Tin chưa có ứng viên → phần danh sách ứng viên rỗng. |

## UC-REC-04. Thêm tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Thêm tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Tạo tin tuyển dụng nội bộ để quản lý hồ sơ ứng viên. |
| Điều kiện tiên quyết | Tác nhân đã đăng nhập; có nhân viên để chọn làm người duyệt hồ sơ. |
| Yêu cầu / Kết quả mong đợi | Tin được lưu và xuất hiện trong danh sách. |
| Kịch bản chính | 1. Tác nhân chọn Đăng tin tuyển dụng.<br>2. Hệ thống tạo mã và mở biểu mẫu.<br>3. Tác nhân nhập tiêu đề, chọn người duyệt hồ sơ, trạng thái và nhập mô tả.<br>4. Tác nhân chọn Lưu lại.<br>5. Hệ thống kiểm tra thông tin bắt buộc và mã tin.<br>6. Hệ thống lưu tin, thông báo thành công, đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 2a. Không tạo được mã → thông báo lỗi khởi tạo mã.<br>4a. Tác nhân chọn Hủy bỏ → đóng biểu mẫu.<br>5a. Thiếu tiêu đề hoặc người duyệt → yêu cầu bổ sung.<br>5b. Mã trùng hoặc người duyệt không tồn tại → không lưu và thông báo lỗi. |

## UC-REC-05. Cập nhật tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Cập nhật tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Chỉnh sửa tiêu đề, mô tả, người duyệt CV và trạng thái tin. |
| Điều kiện tiên quyết | Tác nhân đã đăng nhập; tin tuyển dụng tồn tại. |
| Yêu cầu / Kết quả mong đợi | Tin tuyển dụng được cập nhật; hệ thống chuyển về chế độ xem. |
| Kịch bản chính | 1. Tác nhân chọn Chỉnh sửa tin tuyển dụng.<br>2. Hệ thống hiển thị dữ liệu hiện tại.<br>3. Tác nhân thay đổi nội dung hoặc chọn trạng thái Nháp, Đang tuyển, Tạm dừng, Đã đóng.<br>4. Tác nhân chọn Lưu.<br>5. Hệ thống kiểm tra và lưu thay đổi.<br>6. Hệ thống thông báo thành công và hiển thị lại tin ở chế độ xem. |
| Kịch bản phụ | 4a. Tác nhân hủy chỉnh sửa → trở về chế độ xem.<br>5a. Thiếu tiêu đề hoặc người duyệt → yêu cầu bổ sung.<br>5b. Tin/người duyệt không còn tồn tại hoặc lưu thất bại → thông báo lỗi, không xác nhận cập nhật thành công. |

## UC-REC-06. Xóa tin tuyển dụng

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xóa tin tuyển dụng |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Loại tin tuyển dụng khỏi danh sách đang sử dụng. |
| Điều kiện tiên quyết | Tác nhân đã đăng nhập; tin tuyển dụng tồn tại. |
| Yêu cầu / Kết quả mong đợi | Tin được đánh dấu đã xóa; hồ sơ ứng viên vẫn được giữ. |
| Kịch bản chính | 1. Tác nhân chọn Xóa tại tin tuyển dụng.<br>2. Hệ thống hiển thị hộp thoại xác nhận.<br>3. Tác nhân xác nhận xóa.<br>4. Hệ thống đánh dấu tin đã xóa.<br>5. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân chọn Hủy → giữ nguyên tin.<br>4a. Không tìm thấy tin hoặc thao tác thất bại → thông báo lỗi xóa. |

## UC-REC-07. Xem, tìm kiếm và lọc ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem, tìm kiếm và lọc ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Tra cứu hồ sơ ứng viên thuộc một tin tuyển dụng. |
| Điều kiện tiên quyết | Tác nhân đang mở chi tiết tin tuyển dụng. |
| Yêu cầu / Kết quả mong đợi | Hiển thị thông tin ứng viên, liên hệ, phòng ban/vị trí tiếp nhận, CV, trạng thái và ghi chú. |
| Kịch bản chính | 1. Tác nhân mở phần danh sách ứng viên trong tin tuyển dụng.<br>2. Hệ thống tải ứng viên của tin.<br>3. Tác nhân nhập từ khóa hoặc chọn trạng thái và áp dụng bộ lọc.<br>4. Hệ thống tìm theo tên, mã ứng viên, email hoặc số điện thoại, kết hợp trạng thái đã chọn.<br>5. Hệ thống hiển thị kết quả có phân trang. |
| Kịch bản phụ | 3a. Tác nhân đặt lại bộ lọc → xem toàn bộ ứng viên của tin.<br>4a. Không có ứng viên phù hợp → danh sách rỗng.<br>4b. Không tải được dữ liệu → danh sách không được cập nhật. |

## UC-REC-08. Thêm ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Thêm ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Tạo hồ sơ ứng viên gắn với tin tuyển dụng đang mở. |
| Điều kiện tiên quyết | Tin đang mở ở chế độ chỉnh sửa; có phòng ban và vị trí tiếp nhận. |
| Yêu cầu / Kết quả mong đợi | Hồ sơ ứng viên được lưu và xuất hiện trong danh sách của tin. |
| Kịch bản chính | 1. Tác nhân chọn Thêm ứng viên.<br>2. Hệ thống tạo mã ứng viên và mở biểu mẫu.<br>3. Tác nhân nhập họ tên, email, điện thoại; chọn phòng ban, vị trí tiếp nhận và thông tin bổ sung.<br>4. Tác nhân chọn trạng thái phù hợp, có thể đính kèm CV, rồi chọn Lưu lại.<br>5. Hệ thống kiểm tra dữ liệu và lưu hồ sơ gắn với tin tuyển dụng.<br>6. Hệ thống thông báo thành công, đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 2a. Không tạo được mã → thông báo lỗi.<br>3a. Thay đổi phòng ban → phải chọn lại vị trí trong phòng ban mới.<br>4a. Tác nhân hủy → đóng biểu mẫu.<br>5a. Thiếu họ tên, email, điện thoại, phòng ban, vị trí hoặc email sai định dạng → yêu cầu sửa.<br>5b. Mã ứng viên trùng hoặc dữ liệu liên quan không tồn tại → thông báo lỗi, không lưu. |

## UC-REC-09. Cập nhật hồ sơ ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Cập nhật hồ sơ ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Sửa thông tin cá nhân, liên hệ, nơi tiếp nhận, CV, trạng thái và ghi chú của ứng viên. |
| Điều kiện tiên quyết | Tin tuyển dụng đang ở chế độ chỉnh sửa; hồ sơ ứng viên tồn tại. |
| Yêu cầu / Kết quả mong đợi | Hồ sơ được cập nhật và hiển thị lại trong danh sách. |
| Kịch bản chính | 1. Tác nhân chọn Sửa hồ sơ tại ứng viên.<br>2. Hệ thống mở biểu mẫu với thông tin hiện tại.<br>3. Tác nhân thay đổi thông tin và chọn Lưu lại.<br>4. Hệ thống kiểm tra dữ liệu và lưu hồ sơ.<br>5. Hệ thống thông báo thành công, đóng biểu mẫu và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân chọn Hủy bỏ → không lưu thay đổi hồ sơ.<br>4a. Thiếu thông tin bắt buộc hoặc email không hợp lệ → yêu cầu nhập lại.<br>4b. Hồ sơ hoặc dữ liệu liên quan không tồn tại → thông báo lỗi lưu. |

## UC-REC-10. Xóa ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xóa ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Loại hồ sơ ứng viên khỏi danh sách sử dụng. |
| Điều kiện tiên quyết | Tác nhân đang chỉnh sửa tin tuyển dụng; ứng viên tồn tại. |
| Yêu cầu / Kết quả mong đợi | Hồ sơ được đánh dấu đã xóa và không còn hiển thị trong danh sách. |
| Kịch bản chính | 1. Tác nhân chọn Xóa ứng viên.<br>2. Hệ thống yêu cầu xác nhận.<br>3. Tác nhân xác nhận.<br>4. Hệ thống đánh dấu hồ sơ đã xóa.<br>5. Hệ thống thông báo thành công và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân hủy → giữ nguyên hồ sơ.<br>4a. Hồ sơ không tồn tại hoặc xóa thất bại → thông báo lỗi. |

## UC-REC-11. Thay đổi trạng thái ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Thay đổi trạng thái ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Cập nhật giai đoạn xử lý hồ sơ ứng viên. |
| Điều kiện tiên quyết | Tin đang ở chế độ chỉnh sửa; trên danh sách, ứng viên chưa ở trạng thái Đã onboard hoặc Từ chối/Bị loại. |
| Yêu cầu / Kết quả mong đợi | Trạng thái mới được lưu và hiển thị. |
| Kịch bản chính | 1. Tác nhân mở danh sách trạng thái tại dòng ứng viên.<br>2. Tác nhân chọn trạng thái mới.<br>3. Hệ thống cập nhật trạng thái hồ sơ.<br>4. Hệ thống thông báo thành công và tải lại danh sách ứng viên. |
| Kịch bản phụ | 2a. Chọn Từ chối/Bị loại → thực hiện UC-REC-12.<br>2b. Chọn Đã onboard trong danh sách trạng thái → chỉ đổi trạng thái, không tạo hồ sơ nhân viên; tiếp nhận thực tế dùng UC-REC-13.<br>3a. Ứng viên không tồn tại hoặc cập nhật thất bại → thông báo lỗi. |

## UC-REC-12. Từ chối ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Từ chối ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Ghi nhận ứng viên bị loại và ghi chú lý do. |
| Điều kiện tiên quyết | Tác nhân đang chỉnh sửa tin; ứng viên còn được phép chọn trạng thái trên danh sách. |
| Yêu cầu / Kết quả mong đợi | Ứng viên chuyển sang Từ chối/Bị loại; lý do được lưu vào ghi chú. |
| Kịch bản chính | 1. Tác nhân chọn trạng thái Từ chối/Bị loại.<br>2. Hệ thống mở hộp thoại lý do từ chối.<br>3. Tác nhân nhập lý do và chọn Xác nhận loại.<br>4. Hệ thống lưu trạng thái cùng ghi chú.<br>5. Hệ thống thông báo thành công, đóng hộp thoại và tải lại danh sách. |
| Kịch bản phụ | 3a. Tác nhân chọn Hủy → không đổi trạng thái.<br>3b. Để trống lý do → source hiện tại vẫn cho phép xác nhận.<br>4a. Không cập nhật được → thông báo lỗi. |

## UC-REC-13. Tiếp nhận ứng viên thành nhân viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Tiếp nhận ứng viên thành nhân viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Tạo hồ sơ nhân viên từ hồ sơ ứng viên bằng thao tác Onboard. |
| Điều kiện tiên quyết | Tin đang ở chế độ chỉnh sửa; ứng viên ở trạng thái Chờ việc, đã có phòng ban và vị trí tiếp nhận. |
| Yêu cầu / Kết quả mong đợi | Tạo hồ sơ nhân viên có mã mới, ngày bắt đầu là ngày hiện tại, trạng thái làm việc Thử việc; ứng viên chuyển sang Đã onboard. |
| Kịch bản chính | 1. Tác nhân chọn Onboard tại ứng viên Chờ việc.<br>2. Hệ thống kiểm tra nơi tiếp nhận và tình trạng ứng viên.<br>3. Hệ thống tạo hồ sơ nhân viên từ thông tin cá nhân, liên hệ, địa chỉ, phòng ban và vị trí của ứng viên.<br>4. Hệ thống chuyển ứng viên sang Đã onboard.<br>5. Hệ thống thông báo thành công và tải lại danh sách ứng viên. |
| Kịch bản phụ | 2a. Thiếu phòng ban hoặc vị trí → thông báo và mở biểu mẫu sửa hồ sơ.<br>2b. Ứng viên không tồn tại, đã xóa hoặc đã được tiếp nhận → từ chối thao tác và thông báo lỗi. |

## UC-REC-14. Đính kèm hoặc thay CV ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Đính kèm hoặc thay CV ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Tải CV lên và lưu liên kết CV trong hồ sơ ứng viên. |
| Điều kiện tiên quyết | Tác nhân đang mở biểu mẫu thêm/sửa ứng viên; dịch vụ lưu tệp đã được cấu hình. |
| Yêu cầu / Kết quả mong đợi | Hồ sơ ứng viên lưu liên kết đến CV đã tải lên. |
| Kịch bản chính | 1. Tác nhân chọn Tải CV.<br>2. Tác nhân chọn tệp PDF, DOC hoặc DOCX.<br>3. Hệ thống tải tệp lên và hiển thị liên kết CV trong biểu mẫu.<br>4. Tác nhân chọn Lưu lại hồ sơ.<br>5. Hệ thống lưu liên kết CV cùng thông tin ứng viên. |
| Kịch bản phụ | 2a. Tác nhân không chọn tệp → không thực hiện tải lên.<br>3a. Dịch vụ tải tệp lỗi → thông báo lỗi, không thay liên kết CV trong biểu mẫu.<br>4a. Hủy biểu mẫu → liên kết mới chưa được lưu vào hồ sơ; tệp đã tải lên không được tự thu hồi. |

## UC-REC-15. Xem và tải CV ứng viên

| Nội dung | Chi tiết |
| --- | --- |
| Tên Use Case | Xem và tải CV ứng viên |
| Tác nhân | Admin, Manager, HR (tuyển dụng). |
| Mô tả chức năng | Mở tài liệu CV hoặc yêu cầu tải tài liệu về máy. |
| Điều kiện tiên quyết | Hồ sơ ứng viên có liên kết CV. |
| Yêu cầu / Kết quả mong đợi | Trình duyệt mở CV hoặc xử lý tải tệp theo liên kết. |
| Kịch bản chính | 1. Tác nhân tìm ứng viên trong danh sách hoặc mở biểu mẫu hồ sơ.<br>2. Tác nhân chọn Xem CV hoặc biểu tượng tải CV.<br>3. Hệ thống mở liên kết CV trong cửa sổ/thẻ trình duyệt mới.<br>4. Tác nhân xem hoặc lưu tệp theo xử lý của trình duyệt. |
| Kịch bản phụ | 2a. Chưa có CV → danh sách hiển thị Chưa nộp, không có liên kết xem/tải.<br>3a. Liên kết tệp không còn truy cập được → trình duyệt/dịch vụ lưu tệp báo lỗi. |

## Phạm vi và điểm cần lưu ý

- “Đăng tin tuyển dụng” trong source là lưu tin nội bộ; không có luồng xuất bản lên website tuyển dụng bên ngoài hoặc ứng viên tự nộp hồ sơ.
- Backend có xóa nhiều tin, xóa nhiều ứng viên và lấy riêng ứng viên theo mã định danh; giao diện hiện tại không có thao tác xóa hàng loạt hoặc trang chi tiết ứng viên độc lập.
- Xóa tin chỉ đánh dấu tin đã xóa; backend không gỡ liên kết ứng viên với tin, dù thông báo xác nhận trên giao diện nói liên kết sẽ bị xóa.
- Chọn trạng thái Đã onboard không tương đương thao tác Onboard tạo nhân viên. Thao tác Onboard tạo nhân viên Thử việc, dù thông báo giao diện sử dụng cụm từ “nhân viên chính thức”.
- Người duyệt CV là dữ liệu được phân công trên tin; source chưa có quy trình phê duyệt CV riêng chỉ dành cho người này.

## Đối chiếu source

- Quyền tuyển dụng và nghiệp vụ tin: `hrm-backend/src/main/java/hrm/controller/RecruitmentController.java:23`; `hrm-backend/src/main/java/hrm/service/impl/RecruitmentServiceImpl.java:35`.
- Danh sách, tìm kiếm, thêm/xóa tin: `hrm-frontend/src/pages/Recruitment/RecruitmentList.jsx:79`; `hrm-frontend/src/pages/Recruitment/components/RecruitmentFormDialog.jsx:32`.
- Chi tiết và cập nhật tin: `hrm-frontend/src/pages/Recruitment/RecruitmentDetail.jsx:143`.
- Danh sách, thêm/sửa/xóa, trạng thái và Onboard: `hrm-frontend/src/pages/Recruitment/RecruitmentDetail.jsx:181`; `hrm-frontend/src/pages/Recruitment/RecruitmentDetail.jsx:274`; `hrm-frontend/src/pages/Recruitment/RecruitmentDetail.jsx:410`.
- Điều kiện hồ sơ và CV: `hrm-frontend/src/pages/Recruitment/components/CandidateFormDialog.jsx:44`; `hrm-frontend/src/pages/Recruitment/components/RejectionDialog.jsx:11`.
- Lưu/xóa/trạng thái/tiếp nhận ứng viên: `hrm-backend/src/main/java/hrm/controller/CandidateController.java:24`; `hrm-backend/src/main/java/hrm/service/impl/CandidateServiceImpl.java:135`; `hrm-backend/src/main/java/hrm/service/impl/CandidateServiceImpl.java:270`.
- Dữ liệu nghiệp vụ: `hrm-backend/src/main/java/hrm/model/Recruitment.java:17`; `hrm-backend/src/main/java/hrm/model/Candidate.java:19`.

