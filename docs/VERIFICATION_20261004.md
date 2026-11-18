# Kết quả kiểm tra ngày 04/10/2026

## Phạm vi hoàn tất

- Nhập Excel nhân viên và tải mẫu chỉ dành cho Admin; không thể cấp quyền nhập cho người dùng khác bằng vai trò hay ngoại lệ tài khoản.
- Nhập/xuất danh sách nhân viên đều xác nhận mật khẩu của tài khoản đang đăng nhập. Sai mật khẩu trả lỗi tại hộp thoại, không làm mất phiên đăng nhập; xuất thành công vẫn là XLSX dạng blob.
- Xóa vai trò không còn tài khoản sử dụng; giữ hai vai trò lõi Admin/Người dùng. Vai trò còn gán cho tài khoản bị khóa vẫn không được xóa.
- Chấm công: không cộng công thực tế khi không có giờ hữu ích trong ca, không tạo lượt vào chiều từ lượt ra sáng, thu hồi đơn nghỉ đã duyệt phải duyệt lại công tính lại.
- Dashboard: thống kê theo ngày vào làm/nghỉ việc thực tế đến hôm nay theo giờ Việt Nam; biểu đồ cột số nguyên, báo hồ sơ thiếu ngày; có trường Ngày nghỉ việc trên hồ sơ nhân viên.

## Kết quả chạy

| Kiểm tra | Kết quả |
| --- | --- |
| Backend `mvnw.cmd -B clean verify` | 181 tests; 0 lỗi, 0 thất bại; BUILD SUCCESS, tạo JAR. |
| Frontend `npm.cmd run check` | 65 tests; 0 thất bại; ESLint 0 errors; Vite build thành công. |
| Chrome: xác nhận mật khẩu Excel và xóa vai trò | 17/17 bước đạt. |
| Chrome: biểu đồ và nhập/sửa ngày nghỉ việc | 10/10 bước đạt. |
| Chrome: GPS không camera, quyền cá nhân và nghỉ làm bù | 19/19 bước đạt. |
| PostgreSQL thực tế | 22 bảng; thêm cột `tbl_staff.resignation_date` kiểu date, nullable. Hash của toàn bộ trường nhân viên cũ trước/sau không đổi. |
| Khởi chạy bản mới | PostgreSQL 5432, Backend 8080 và Frontend 5173 đều đang lắng nghe. |

Kiểm thử tự động và thao tác tạo/sửa dữ liệu dùng H2 riêng, không dùng dữ liệu nghiệp vụ thật. Kiểm thử trình duyệt GPS sử dụng vị trí giả lập của Chrome và chặn quyền camera; ứng dụng vẫn lấy vị trí từ `navigator.geolocation`. Chưa có kiểm thử GPS ngoài thực địa trên điện thoại.

ESLint còn 170 warnings hiện có; Vite có cảnh báo kích thước bundle và dữ liệu Browserslist cũ. Không nâng phiên bản, không cập nhật dependency để xử lý các cảnh báo này.

## Bằng chứng trong workspace

- `.runtime/hrm-final-backend-build.log`
- `.runtime/hrm-final-frontend-check.log`
- `.runtime/excel-password-e2e/browser-results.json` — Excel/vai trò.
- `.runtime/excel-password-e2e/dashboard-browser-results.json`
- `.runtime/excel-password-e2e/attendance-browser-results.json`
- `.runtime/excel-password-e2e/dashboard-corrected-chart.png`
- Backend: `hrm-backend/target/hrm-0.0.1-SNAPSHOT.jar`
- Frontend: `hrm-frontend/dist`

## Quy tắc giữ nguyên và giới hạn

- Công vẫn theo mức 0,5/1 của ca khi có giờ hữu ích dương; chưa chuyển sang công tỷ lệ theo giờ hoặc đặt ngưỡng đủ ca. Chưa xử lý đầy đủ ca tùy chỉnh/qua đêm, nhiều phiên vào/ra hoặc đối soát số giờ làm bù. Không tính lại hàng loạt bảng công/lương cũ.
- Biểu đồ dựa trên hồ sơ chưa xóa, không có lịch sử nhiều lần nghỉ/tái tuyển dụng. Ngày nghỉ không biết được để trống, không suy đoán từ ngày sửa hồ sơ; bổ sung từ thông tin thực tế.
- Xem [nghiệp vụ chấm công](ATTENDANCE_BUSINESS_PROCESS.md), [hướng dẫn phân quyền/Excel](ACCESS_CONTROL.md), [quy tắc biểu đồ](DASHBOARD_STAFF_TREND.md).

## Chạy hệ thống

Tại `D:\Project\Hrm`:

```powershell
.\start.cmd
.\status.cmd
```

Mở `http://127.0.0.1:5173`. Dùng `stop.cmd` khi cần dừng Backend/Frontend; PostgreSQL và dữ liệu được giữ nguyên.
