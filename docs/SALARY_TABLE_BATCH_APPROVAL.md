# Nhập lương theo hàng và duyệt hàng loạt

## Lương & phụ cấp

Vào hồ sơ nhân viên → **Lương & Phụ cấp**. Mỗi khoản trong danh mục được hiển thị một hàng gồm ô Áp dụng, tên/mã, loại, cách tính và ô nhập số tiền VND. Số tiền đã lưu được giữ nguyên. Chọn khoản áp dụng, nhập tiền và bấm **Lưu bảng khoản lương** một lần để lưu toàn bộ cấu hình của nhân viên.

- Không tự gán khoản mới cho nhân viên. Khoản chưa chọn không được lưu. Số tiền 0 hợp lệ; ô tiền trống, số âm hoặc không hợp lệ bị chặn.
- Có bộ phân loại thu nhập/phụ cấp và giảm trừ. Tổng mức cấu hình là tổng khoản cộng trừ khoản giảm trừ, không phải thực lĩnh sau tính công.
- Khoản dùng công thức cũ được giữ hiển thị và yêu cầu cập nhật danh mục hoặc bỏ áp dụng trước khi lưu; không đổi công thức hay mất khoản ngầm.
- Giữ quyền `SALARY_CONFIG_VIEW`/`SALARY_CONFIG_MANAGE` và API cấu hình hiện có. Cấu hình là nguồn cho bảng lương chưa duyệt; bảng đã duyệt không bị sửa.

## Nghỉ phép và chấm công

- Nghỉ phép: phân loại nhanh theo trạng thái và loại phép, lọc nhân viên và khoảng ngày nghỉ.
- Phê duyệt chấm công: phân loại nhanh Nháp/Chờ duyệt/Đã duyệt/Từ chối, giữ bộ lọc nhân viên, phòng ban, kỳ lương và khoảng ngày.
- **Duyệt tất cả theo bộ lọc** lấy toàn bộ bản ghi chờ duyệt phù hợp trên tất cả trang, không chỉ trang đang xem; tối đa 500 bản ghi mỗi lượt. Nếu vượt giới hạn, yêu cầu thu hẹp bộ lọc thay vì âm thầm chỉ duyệt một phần.
- Hiển thị danh sách và số lượng xác nhận trước khi gửi. Khi chọn xem trạng thái khác Chờ duyệt, nút duyệt hàng loạt bị tắt.
- Endpoint chỉ nhận danh sách ID đã xem trước; backend kiểm tra quyền `LEAVE_APPROVE` hoặc `ATTENDANCE_APPROVE`. Khóa và kiểm tra lại trạng thái từng bản ghi trước khi duyệt. Nghỉ phép chỉ nhận PENDING, công chỉ nhận SUBMITTED; không tự duyệt nháp, bản ghi đã từ chối hoặc đã xóa.
- Mỗi bản ghi có giao dịch riêng. Thiếu phép hoặc lỗi nghiệp vụ ở một đơn không chặn các bản ghi hợp lệ còn lại. Kết quả hiển thị số đã duyệt/không duyệt được và lý do từng mục.
- Luồng duyệt hiện có vẫn kiểm tra số dư phép, đồng bộ công và tạo thông báo. Không bỏ qua các điều kiện này khi duyệt hàng loạt.

API mới: `PUT /api/leave-requests/approve-batch` và `PUT /api/timesheets/approve-batch`, JSON `{ "ids": ["uuid", "uuid"] }`. Trả `data.approved`, `data.failed` và `data.items` gồm id, success, message.

## Kiểm chứng

Backend: kiểm thử validation, quyền API, điều kiện chờ duyệt, lỗi từng bản ghi, và commit giao dịch độc lập trên H2. Frontend: kiểm thử dữ liệu bảng khoản lương, số tiền 0/trống/âm, công thức cũ; danh sách xem trước phải đầy đủ, đúng trạng thái, không trùng ID, không vượt giới hạn. Kiểm tra Chrome sử dụng phản hồi lưu/duyệt mô phỏng, không thay đổi hồ sơ, lương hoặc đơn thật.
