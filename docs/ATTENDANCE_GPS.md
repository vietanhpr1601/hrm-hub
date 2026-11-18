# Chấm công GPS và nghỉ có làm bù

## Sử dụng

1. Mở `http://localhost:5173`, nhấn **Ctrl + F5** để tải giao diện mới.
2. Đăng nhập tài khoản đã liên kết nhân viên và được cấp quyền chấm công.
3. Nhấn **Chấm công**, cho phép trình duyệt truy cập **Vị trí**.
4. Chọn ca và **Vào ca / Ra ca**, nhấn **Chấm công → Xác nhận**.

Không yêu cầu quyền camera, chụp ảnh hay tải ảnh. Ứng dụng lấy chính sách GPS từ Backend rồi gọi `navigator.geolocation.watchPosition` với `enableHighAccuracy: true`, `maximumAge: 0`. Trong thời gian mặc định tối đa 25 giây, hệ thống giữ mẫu còn mới có sai số ước tính nhỏ nhất. Đạt mục tiêu 20 m thì dừng sớm; hết thời gian vẫn chấp nhận mẫu tốt nhất nếu không quá 100 m. Vị trí được lấy lại khi xác nhận chấm công. Không có tọa độ dự phòng hoặc tọa độ cố định. Đóng cửa sổ, thay yêu cầu, đổi tài khoản/hồ sơ nhân viên hoặc mất quyền chấm công sẽ hủy theo dõi và bỏ qua kết quả cũ.

Thiếu GPS, bị chặn quyền, không tải được cấu hình, vị trí không hợp lệ hoặc hết thời gian mà chưa có mẫu đạt ngưỡng đều ngăn chấm công và hiển thị hướng dẫn thử lại. Giao diện hiển thị sai số tốt nhất đang thu được và ngưỡng cho phép; chỉ hiện đạt yêu cầu khi đã chọn xong mẫu hợp lệ. Trình duyệt cần HTTPS hoặc localhost. GPS accuracy là sai số ước tính do thiết bị cung cấp; chất lượng định vị phụ thuộc thiết bị và dịch vụ vị trí. Ứng dụng giữ nguyên tọa độ và accuracy của mẫu được chọn, không tự sửa số đo để làm sai số nhỏ hơn.

## Cấu hình chất lượng GPS

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `ATTENDANCE_MAX_ACCURACY_METERS` | `100` | Sai số tối đa chấp nhận, tính bằng mét. Backend kiểm tra cả Check-in và Check-out, kể cả Admin. |
| `ATTENDANCE_TARGET_ACCURACY_METERS` | `20` | Mục tiêu để dừng lấy mẫu sớm; phải lớn hơn 0 và không vượt ngưỡng tối đa. |
| `ATTENDANCE_LOCATION_TIMEOUT_MS` | `25000` | Thời gian lấy mẫu tối đa; số nguyên từ 1000 đến 60000 mili giây. |

Mỗi lượt mở hộp thoại, lấy lại vị trí hoặc xác nhận đều đọc lại chính sách từ `GET /api/check-in-out/location-policy`. Frontend và Backend dùng chung cấu hình; khi không đọc được hoặc cấu hình không hợp lệ, không dùng ngưỡng dự phòng.

Ví dụ cấu hình từ PowerShell tại `D:\Project\Hrm`, sau khi đã build bản mới:

```powershell
.\stop.cmd
$env:ATTENDANCE_MAX_ACCURACY_METERS = '100'
$env:ATTENDANCE_TARGET_ACCURACY_METERS = '20'
$env:ATTENDANCE_LOCATION_TIMEOUT_MS = '25000'
.\start.cmd
```

Các biến trên áp dụng cho lần khởi động từ cửa sổ PowerShell đó; không cần build lại khi chỉ đổi biến môi trường. Kiểm tra tại địa điểm làm việc trước khi chọn ngưỡng sử dụng thực tế. Máy tính hoặc thiết bị có tín hiệu yếu có thể không đạt 100 m; bật quyền vị trí chính xác, bật dịch vụ vị trí trên điện thoại và thử ở nơi thoáng/gần cửa sổ. Ngưỡng accuracy không phải bán kính chấm công quanh công ty; khoảng cách nơi làm việc vẫn chỉ để đối chiếu như dưới đây.

## Dữ liệu và chi tiết

- Ghi thời điểm vào/ra theo máy chủ, múi giờ Việt Nam; ca, latitude, longitude, GPS accuracy, thời điểm lấy vị trí và IP kết nối tới Backend.
- Backend kiểm tra tọa độ hữu hạn, đúng khoảng; accuracy không âm và không vượt ngưỡng cấu hình (mặc định 100 m); vị trí không quá 120 giây và không lệch tương lai quá 30 giây. Bản ghi vượt ngưỡng bị từ chối trước khi lưu hoặc tính lại công.
- IP lấy từ kết nối HTTP, không lấy IP do Frontend gửi hoặc từ dịch vụ IP bên ngoài. Khi chạy cùng máy, IP có thể là `127.0.0.1` hoặc địa chỉ loopback IPv6.
- Người dùng tự chấm công không được sửa giờ bằng payload. Quyền quản lý chấm công vẫn giữ khả năng ghi giờ thủ công vốn có của API.
- Chi tiết ngày công hiển thị giờ vào/ra, tổng giờ theo bảng công, đi muộn, về sớm, tăng ca, ca làm việc, nhật ký GPS, accuracy, khoảng cách, IP, trạng thái và ghi chú; bên dưới có đơn nghỉ/ngày làm bù liên quan.
- Khi bổ sung bản ghi vào ngày đã duyệt/từ chối, công thực tế được gửi duyệt lại. Phê duyệt nghỉ không tự động phê duyệt phần chấm công của ca còn lại trong ngày nghỉ nửa ngày.
- Dữ liệu cũ không có accuracy hoặc khoảng cách được giữ trống, không tự điền số giả.

## Địa điểm làm việc (tùy chọn)

Mặc định chưa cấu hình tọa độ nơi làm việc; giao diện hiển thị **Chưa cấu hình** ở khoảng cách. Khoảng cách chỉ để đối chiếu, không giới hạn bán kính chấm công.

Muốn cấu hình, nhập tọa độ thật của địa điểm làm việc trong PowerShell tại `D:\Project\Hrm`:

```powershell
.\stop.cmd
$env:ATTENDANCE_WORKPLACE_NAME = Read-Host 'Tên địa điểm làm việc'
$env:ATTENDANCE_WORKPLACE_LATITUDE = Read-Host 'Latitude thực tế của địa điểm'
$env:ATTENDANCE_WORKPLACE_LONGITUDE = Read-Host 'Longitude thực tế của địa điểm'
.\start.cmd
```

Phải nhập đủ cả latitude và longitude, dùng dấu chấm thập phân. Cấu hình sai sẽ báo lỗi khi khởi động. Có thể đặt ba biến môi trường này trước mỗi lần khởi chạy. Backend tính khoảng cách theo mét và lưu tên địa điểm/khoảng cách tại thời điểm chấm công; cấu hình đổi sau này không thay đổi lịch sử.

## Nghỉ có làm bù

Tại **Nghỉ phép → Yêu cầu nghỉ phép**, tạo đơn với loại **Nghỉ có làm bù**, chọn khoảng ngày nghỉ và thêm các ngày làm bù trước khi lưu.

- Mỗi ngày nghỉ làm việc chọn một ngày làm bù. Nghỉ nửa ngày chọn một ngày; vẫn có thể chấm công ca còn lại của ngày nghỉ.
- Ngày làm bù nằm ngoài khoảng nghỉ, không được trùng nhau hoặc trùng lịch nghỉ/làm bù đang chờ duyệt hay đã duyệt của cùng nhân viên.
- Số ngày nghỉ vẫn theo lịch hiện có của hệ thống: bỏ thứ Bảy và Chủ nhật.
- Quy ước hiện tại: ngày nghỉ loại này không hưởng công và không trừ phép năm. Ngày làm bù phải check-in/out thực tế và được duyệt mới dùng làm công tính lương. Không cộng công hai lần, không tự tạo lượt chấm công khi duyệt đơn.
- Đơn chờ duyệt được sửa ngày làm bù. Từ chối/hủy đơn giải phóng lịch; khi duyệt lại sẽ kiểm tra trùng lịch lần nữa.

## Database và build

### Nâng ngưỡng sai số GPS — 08/10/2026

Ngưỡng mặc định được nâng từ **50 m lên 100 m** theo yêu cầu sử dụng. Mục tiêu dừng sớm vẫn là 20 m và thời gian lấy mẫu tối đa là 25 giây. Frontend đọc ngưỡng từ Backend; không cần sửa mã giao diện. Thay đổi này giúp chấp nhận mẫu có sai số từ trên 50 m đến 100 m, không khắc phục trường hợp bị từ chối quyền vị trí hoặc thiết bị không trả tọa độ.

Đã chạy **20/20 test Backend** liên quan GPS và build JAR thành công; **22/22 test Frontend định vị** đạt. Kiểm tra giới hạn mới xác nhận 100 m được chấp nhận, 100,01 m bị từ chối ở cả Frontend và API Check-in/Check-out. Test Backend sử dụng Database H2 riêng. Log build: `.runtime/gps-100m-backend-build.log`.

### Kiểm thử cải thiện chất lượng GPS — 07/10/2026

- Backend: `mvnw.cmd -B verify` thành công, **207/207 test** đạt và đóng gói JAR. Kiểm tra cấu hình, phân quyền đọc chính sách, ngưỡng sai số cho cả Admin/nhân viên và từ chối dữ liệu không hợp lệ trước khi lưu.
- Frontend: `npm.cmd run check` thành công, **83/83 test** đạt, lint không có lỗi và build production thành công. Trong đó có **22 test định vị** kiểm tra chọn mẫu tốt nhất, thời hạn, hủy yêu cầu, đổi phiên đăng nhập và lỗi cấu hình.
- Chrome: **16/16 tình huống** đạt trên môi trường H2 riêng. Bao gồm từ chối GPS; sai số giảm từ 120 m xuống 35 m rồi 12 m; giữ mẫu 35 m khi mẫu sau kém hơn; hết thời gian với sai số 250 m; đóng hộp thoại khi đang lấy vị trí; lỗi tải chính sách; thử lại; lấy GPS mới lúc xác nhận Check-in/Check-out và lưu đúng tọa độ/accuracy. Camera bị từ chối không ảnh hưởng chấm công; không phát sinh lỗi JavaScript.
- Kiểm thử trình duyệt dùng **GPS mô phỏng**, xác nhận xử lý của ứng dụng; không phải phép đo độ chính xác cảm biến tại nơi làm việc. Cần thử thêm trên thiết bị thực tế với quyền vị trí chính xác được bật.
- Không thay đổi cấu trúc Database hoặc nâng phiên bản công nghệ trong lần cập nhật này. Cảnh báo Browserslist/bundle lớn hiện hữu không làm build thất bại.
- Đã khởi động lại hệ thống chính: PostgreSQL `5432`, Backend `8080`, Frontend `5173`. Chrome tải được trang đăng nhập, kết nối Backend và không có lỗi JavaScript. Đối chiếu số dòng và checksum trước/sau xác nhận dữ liệu **23 bảng PostgreSQL giữ nguyên**; dữ liệu chấm công thử chỉ nằm trong H2 riêng đã dừng sau kiểm thử.

Log kiểm thử: `.runtime/gps-accuracy-backend-build.log`, `.runtime/gps-accuracy-frontend-check.log`, `.runtime/excel-password-e2e/gps-accuracy-results.json`.

### Kiểm tra phiên bản GPS trước đó — 03/10/2026

Migration: [20261003_attendance_gps_only.sql](database/20261003_attendance_gps_only.sql). Đã sao lưu PostgreSQL dạng custom trong `.runtime/attendance-verification/` và kiểm tra checksum trước khi áp dụng. Migration chỉ bỏ `photo_url`, `photo_check_in_url`, `photo_check_out_url`; đối chiếu dữ liệu của 21 bảng hiện có xác nhận giữ nguyên ngoài các cột ảnh đó. Khi khởi động, Hibernate bổ sung metadata GPS và bảng `tbl_leave_makeup_date` (22 bảng); không đặt lại DB.

Java, Spring Boot, Node và npm giữ nguyên phiên bản. Build lại khi cần:

```powershell
.\stop.cmd
.\build.cmd
.\start.cmd
.\status.cmd
```

Kiểm tra ngày 03/10/2026: Backend `mvnw.cmd -B verify` đạt 143 test và đóng gói JAR; Frontend `npm.cmd run check` đạt 56 test, lint không có lỗi và build thành công. Vẫn có cảnh báo lint/Browserslist/kích thước bundle hiện hữu, không nâng phiên bản để xử lý các cảnh báo này.

19/19 bước kiểm tra trình duyệt đạt, sử dụng Chrome và H2 riêng với GPS mô phỏng: camera bị từ chối vẫn Check-in/Check-out thành công; từ chối GPS không tạo bản ghi; đổi vị trí trước Check-out được lưu đúng; dữ liệu gửi không có ảnh, IP tự khai hay giờ tự chọn; kiểm tra màn hình phân quyền, chi tiết công và tạo đơn chọn ngày làm bù. Không có lỗi JavaScript. Việc này xác nhận luồng ứng dụng, không đo độ chính xác cảm biến GPS trên thiết bị của người dùng.
