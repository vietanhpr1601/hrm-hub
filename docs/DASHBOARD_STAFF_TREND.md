# Biểu đồ biến động nhân sự

Biểu đồ trên Dashboard thể hiện **số lượt vào làm và nghỉ việc theo tháng** trong 12 tháng gần nhất, gồm tháng hiện tại. Đây là số phát sinh từng tháng, không phải tổng số nhân viên còn đang làm việc tại cuối tháng.

## Quy tắc thống kê

| Nội dung | Cách tính |
| --- | --- |
| Vào làm | Đếm hồ sơ có ngày vào làm trong tháng. Không tính trạng thái **Chờ nhận việc (1)** và **Không nhận việc (4)**. Hồ sơ đã nghỉ việc nhưng còn được lưu vẫn đóng góp lượt vào làm ở tháng tương ứng. |
| Nghỉ việc | Đếm hồ sơ có trạng thái **Đã nghỉ việc (5)** và có **Ngày nghỉ việc** trong tháng. Không lấy ngày tạo hồ sơ hoặc ngày sửa hồ sơ làm ngày nghỉ. |
| Phạm vi hồ sơ | Loại hồ sơ đã xóa ở cả hai cột. Xóa hồ sơ không được coi là một lượt nghỉ việc. |
| Mốc thời gian | Dùng ngày hiện tại theo múi giờ Việt Nam (`Asia/Ho_Chi_Minh`). Các tháng trước tính đủ tháng; tháng hiện tại chỉ tính đến hôm nay, gồm cả hôm nay. Ngày trong tương lai chưa được tính. |
| Cách hiển thị | Cột **Vào làm** và **Nghỉ việc** dùng số nguyên, đơn vị người. Tháng không phát sinh có giá trị 0. |

Chỉ số nhân viên vào làm trong tháng và biểu đồ nhỏ trên thẻ nhân sự dùng cùng quy tắc với cột Vào làm. Biểu đồ nhỏ thể hiện lượt vào làm trong 6 tháng gần nhất. Không suy ra tỷ lệ tăng trưởng tổng nhân sự từ các lượt vào làm này.

## Nhập ngày để thống kê đúng

Tài khoản cần có quyền xem và cập nhật nhân viên.

1. Mở **Danh sách nhân viên**, chọn **Sửa** hồ sơ cần cập nhật.
2. Trong tab **Thông tin cá nhân**, mở mục **Thông tin hồ sơ nhân viên**.
3. Kiểm tra trường **Ngày chính thức**. Đây là ngày vào làm được dùng cho biểu đồ; trong biểu mẫu thêm nhanh, trường này có tên **Ngày vào làm việc**.
4. Khi nhân viên đã nghỉ, chọn trạng thái **Đã nghỉ việc** và nhập **Ngày nghỉ việc** thực tế. Trường ngày nghỉ cũng tiếp tục hiện nếu hồ sơ đã có ngày này.
5. Nhấn **Lưu lại**. Ngày nghỉ không được trước ngày vào làm và không được sau ngày hiện tại tại Việt Nam.
6. Quay lại Dashboard để tải số liệu mới. Nếu đang mở Dashboard, dữ liệu tự làm mới mỗi 2 phút.

Ví dụ: nhân viên vào làm ngày 15/03 và nghỉ ngày 20/09 được tính một lượt vào làm tháng 03, một lượt nghỉ tháng 09. Việc sửa số điện thoại trong tháng 10 không chuyển lượt nghỉ sang tháng 10.

## Hồ sơ cũ và dữ liệu chưa đầy đủ

Ngày nghỉ việc được phép để trống khi chưa biết ngày thực tế. Hệ thống không tự điền ngày sửa hồ sơ hoặc một ngày phỏng đoán. Hồ sơ đã nghỉ nhưng thiếu ngày nghỉ không được đưa vào một tháng cụ thể; biểu đồ hiện ghi chú **hồ sơ nghỉ việc thiếu ngày nghỉ**. Hồ sơ đã vào làm nhưng thiếu ngày vào làm cũng có ghi chú tương ứng. Các số hồ sơ thiếu ngày được tính trên toàn bộ hồ sơ còn lưu, không chỉ những hồ sơ có thể thuộc 12 tháng đang hiển thị.

Mẫu Excel hiện tại chưa có cột Ngày nghỉ việc. Sau khi nhập nhân viên có trạng thái Đã nghỉ việc, bổ sung ngày thực tế trong hồ sơ theo các bước trên; giữ trống nếu chưa có thông tin đáng tin cậy.

## Giới hạn lịch sử làm việc

Mỗi hồ sơ hiện lưu một ngày vào làm, một ngày nghỉ việc và trạng thái hiện tại; chưa có lịch sử nhiều lần nghỉ và tái tuyển dụng. Trạng thái **Nghỉ việc đi làm lại (6)** được tính lượt vào làm theo ngày vào làm đang lưu, không được tính là một lượt nghỉ việc hiện tại. Nếu cập nhật ngày vào làm cho đợt tái tuyển dụng mới, cần xóa ngày nghỉ của đợt cũ khỏi trường này để không mâu thuẫn với ngày vào làm mới. Biểu đồ không thể tái dựng đầy đủ các đợt làm việc cũ từ một hồ sơ như vậy.
