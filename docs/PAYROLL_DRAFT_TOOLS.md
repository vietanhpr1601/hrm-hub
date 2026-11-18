# Tự lập bảng nháp, cập nhật và xóa

## Tự lập bảng lương để duyệt sau

**Khi lưu kỳ lương mới, hệ thống tự tạo bảng nháp trong cùng giao dịch.** Nhân viên đủ cấu hình được tự thêm và tính từ công đã duyệt; kết quả hiển thị số nhân viên cùng danh sách chưa được đưa vào và lý do. Nếu chưa có ai đủ cấu hình, vẫn tạo một bảng nháp trống để bổ sung sau. Nếu tính lương lỗi, cả kỳ lương và bảng nháp đều được hoàn tác; không để lại dữ liệu dở dang. Tạo kỳ mới cần cả PERIOD_MANAGE và PAYROLL_MANAGE. Cập nhật kỳ không tạo thêm bảng trùng.

Sau khi tạo, chọn **Mở bảng nháp** để xem và duyệt sau. Nhân viên sửa/bổ sung cấu hình sau đó có thể được thêm vào bảng nháp rồi Tính lại. Không tự duyệt, trả tiền hoặc gửi email tại bước tạo kỳ.

Với kỳ cũ chưa có bảng (hoặc đã xóa bản nháp để lập lại), trong **Lương thưởng → Bảng lương**, chọn **Tự lập bảng nháp**, chọn kỳ rồi **Xem trước nhân viên**. Mặc định chọn các hồ sơ chưa xóa, có cấu hình lương hợp lệ và không nằm hoàn toàn ngoài thời gian làm việc của kỳ. Nhân viên chưa vào làm, đã nghỉ trước kỳ hoặc thiếu cấu hình được hiển thị kèm lý do và không tự đưa vào bảng. Có thể bỏ chọn nhân viên trước khi tạo.

Chọn **Tạo bản nháp** để hệ thống lấy cấu hình lương/phụ cấp đã lưu và công APPROVED trong kỳ, tính phiếu và tạo bảng DRAFT. Không tự duyệt, ghi nhận trả tiền hay gửi email. Người quản lý mở bảng kiểm tra rồi duyệt sau. Đây là thao tác tự lập theo kỳ do bạn kích hoạt, không có lịch chạy hàng tháng ngầm.

Khóa kỳ trước khi tạo và từ chối tự lập nếu kỳ đã có bảng đang sử dụng, tránh tạo trùng khi bấm lại. Khi đã có bản nháp, dùng Tính lại để cập nhật, hoặc xóa bản nháp trước khi tự lập lại. Cấu hình thay đổi không còn hợp lệ lúc gửi sẽ làm hủy toàn bộ lần tạo, không để lại bảng rỗng hoặc phiếu dở dang.

## Cập nhật và xóa

- **Kỳ lương:** nút có chữ Cập nhật và Xóa nằm tại cột Thao tác. Cập nhật mở form tên/mã/tháng/năm/khoảng ngày/công chuẩn hiện có.
- **Bảng lương:** Cập nhật sửa tên, mã và mô tả; Tính lại lấy các khoản trong hồ sơ nhân viên và công được duyệt mới nhất; Xóa bỏ bản nháp khỏi danh sách đang sử dụng.
- Muốn đổi số tiền khoản lương, sửa trong hồ sơ nhân viên → Lương & Phụ cấp rồi bấm Tính lại trên bảng nháp.
- Xóa kỳ kèm các bảng và phiếu nháp trong kỳ. Xóa bảng kèm các phiếu thuộc bảng đó. Có xác nhận trước khi xóa; dữ liệu được đánh dấu đã xóa để giữ lịch sử.
- Không được sửa/xóa bảng đã duyệt hoặc đã thanh toán; không sửa/xóa kỳ đã có bảng chốt hoặc trả tiền. Backend tiếp tục kiểm tra dù gọi API trực tiếp.
- Không có kỳ lương, bảng lương hay thanh toán thật nào được tạo/cập nhật/xóa trong quá trình kiểm thử giao diện.

API mới: `GET/POST /api/payrolls/auto-draft/{periodId}` (POST nhận staffIds từ danh sách xem trước), `PUT /api/payrolls/{id}/details` nhận name/code/description. Yêu cầu PAYROLL_MANAGE. API tính lại, xóa và quyền quản lý kỳ giữ nguyên.
