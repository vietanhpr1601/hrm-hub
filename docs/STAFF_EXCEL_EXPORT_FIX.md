# Sửa xuất danh sách nhân viên Excel

## Nguyên nhân và thay đổi

Nút xuất trong `StaffList` trước đây trả về một Blob rỗng, đặt tên `.xlsx` nhưng không gọi Backend. Backend chưa có endpoint tạo Excel.

- Bổ sung Apache POI `poi-ooxml:5.5.1`; giữ nguyên Java, Spring Boot, Node/npm và các phiên bản Frontend hiện tại.
- Thêm `POST /api/staffs/export-excel`, nhận bộ lọc danh sách nhân viên và trả dữ liệu XLSX nhị phân thật.
- Header: `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` và `Content-Disposition: attachment; filename="Danh_sach_nhan_vien.xlsx"`. Cho phép Frontend đọc header tên file qua CORS.
- Xuất toàn bộ kết quả theo các quy tắc lọc hiện có của danh sách, không giới hạn ở trang đang xem; loại nhân viên đã xóa mềm.
- Workbook có 14 cột: STT, mã nhân viên, họ tên, ngày sinh, giới tính, số điện thoại, email, ngày vào làm, trạng thái, phòng ban, vị trí, CCCD/CMND, địa chỉ hiện tại, mã BHXH.
- Giữ số 0 đầu của mã/số điện thoại/CCCD; ngày là ô ngày Excel; chuỗi do người dùng nhập không được chuyển thành công thức.
- Frontend gọi API với `responseType: 'blob'`, kiểm tra MIME, dữ liệu không rỗng và chữ ký ZIP trước khi tải. JSON/HTML không được lưu thành `.xlsx`.
- Giữ nguyên các chức năng nhập Excel, tải mẫu và các nghiệp vụ khác.

## Kiểm chứng

| Kiểm tra | Kết quả |
| --- | --- |
| Backend Maven verify và build JAR | 57/57 test đạt |
| Bộ test xuất Excel mới | 5/5 đạt; Apache POI mở lại chính nội dung HTTP response |
| Frontend tests | 25/25 đạt, gồm 6 test xuất Excel mới |
| Lint các file Frontend sửa | 0 lỗi; còn cảnh báo có sẵn |
| Frontend Vite build | Thành công; còn cảnh báo kích thước bundle có sẵn |
| Trình duyệt thật: đăng nhập, mở danh sách, nhấn Xuất Excel, nhận file | 11/11 kiểm tra đạt |
| File tải từ giao diện | 4.433 byte; API danh sách có 3 nhân viên; HTTP 200, đúng Content-Type và Content-Disposition |
| Mở trực tiếp bằng Microsoft Excel | Chưa xác nhận: Excel từ chối lệnh COM hoặc treo khi mở file; không tính bước này là đạt |

Microsoft Excel trên máy tạo được workbook trắng qua COM, nhưng các lần mở file để kiểm chứng bị lỗi `0x80010001`/`0x800AC472`. Đây là giới hạn còn lại của bước kiểm chứng trực tiếp; kiểm tra bằng thư viện và tải qua trình duyệt đã đạt. Không thay đổi cài đặt Excel hoặc workbook đang có của người dùng để xử lý lỗi tự động hóa.

Backend và Frontend đã được khởi động lại với bản sửa. Truy cập `http://127.0.0.1:5173`, tải lại trang danh sách nhân viên và chọn **Xuất Excel** để nhận file mới. File rỗng đã tải trước khi sửa không tự được cập nhật.

## Bằng chứng cục bộ

- `.runtime/staff-export-backend-build.log`
- `.runtime/staff-export-tests.log`
- `.runtime/staff-export-verification/browser-results.json`
- `.runtime/staff-export-verification/browser-2026-09-30T11-32-31-692Z/Danh_sach_nhan_vien.xlsx`
- `hrm-backend/target/staff-export-verification/staff-export-test.xlsx`: dữ liệu kiểm thử H2, không ghi dữ liệu giả vào PostgreSQL ứng dụng.
- `.runtime/staff-export-verification/excel-native-result.log`: ghi nhận lỗi điều khiển Excel, không phải kết quả kiểm chứng thành công.
