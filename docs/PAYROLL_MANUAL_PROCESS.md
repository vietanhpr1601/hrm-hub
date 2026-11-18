# Bảng lương từ cấu hình nhân viên và ghi nhận thanh toán

Cập nhật ngày 12/10/2026. Khoản lương lấy từ mục **Khoản lương** trong hồ sơ nhân viên; tạo bảng cho những nhân viên được chọn.

## Quy trình

1. Vào hồ sơ nhân viên → **Khoản lương**, cấu hình số tiền và cách tính rồi lưu. Đây là nguồn dữ liệu duy nhất cho bảng lương chưa duyệt.
2. Tạo/chọn kỳ lương, nhập khoảng ngày và số công chuẩn của kỳ.
3. Chọn **Tạo bảng lương cho nhân viên**, chọn kỳ và một hoặc nhiều nhân viên, nhập tên bảng. Mã có thể để trống để hệ thống tạo mã riêng. Hệ thống tính lương từ cấu hình đã lưu và tạo bảng **Chờ duyệt** trong cùng một thao tác; lỗi ở một nhân viên thì toàn bộ thao tác được hoàn tác. Nhân viên chưa có khoản lương đang sử dụng được báo rõ tên/mã để bổ sung tại hồ sơ trước khi tạo.
4. Mở bảng để xem chi tiết hoặc **Thêm nhân viên**. Không có màn hình chỉnh/nhập lại khoản lương trên phiếu dự thảo riêng. Nếu cần đổi tiền hoặc cách tính, sửa tại hồ sơ nhân viên.
5. Chọn **Cập nhật khoản lương & tính lại** để lấy khoản lương mới nhất ở hồ sơ và công đã duyệt trong kỳ. Nút cập nhật cạnh mỗi nhân viên cho cập nhật từng người độc lập; phù hợp với bảng cũ có dòng trống. Giữ mã hàng lương, ghi chú và thông tin thanh toán.
6. **Duyệt bảng lương** cũng lấy cấu hình và công đã duyệt mới nhất trước khi khóa số liệu. Bảng cần ít nhất một nhân viên, mỗi người có cấu hình hợp lệ và thực lĩnh không âm. Một nhân viên thiếu cấu hình thật sẽ chặn duyệt toàn bảng bằng thông báo tên/mã rõ ràng; có thể bổ sung hồ sơ hoặc bỏ người đó khỏi bảng. Sau duyệt khóa việc tính lại/xóa, mỗi kỳ chỉ có một bảng được duyệt.
7. Sau khi đã trả tiền, mở từng phiếu và **Ghi nhận đã thanh toán** với ngày trả, hình thức, mã giao dịch/chứng từ và ghi chú. Đây là ghi nhận khoản đã chi, không gọi ngân hàng để chuyển tiền. Mỗi phiếu ghi nhận một lần đủ số thực lĩnh; chưa có thanh toán từng phần.

## Hai công thức

Gọi **A** là số tiền nhập của khoản, **C** là tổng tỷ lệ công đã duyệt trong kỳ, **S** là công chuẩn kỳ.

| Cách tính | Công thức | Ý nghĩa của A |
| --- | --- | --- |
| Cố định (`FIXED`) | A | Số tiền cố định của kỳ |
| Theo công chuẩn (`BY_STANDARD_DAYS`) | A × C ÷ S | Số tiền ứng với đủ công chuẩn; S phải lớn hơn 0 |

S là số công làm việc theo lịch của tháng, nhập tại kỳ lương. Số công thực tế có thể gồm nửa ngày (0,5 công). Mỗi khoản mới làm tròn HALF_UP đến đồng. Tổng thu nhập là tổng các khoản cộng, tổng khấu trừ là tổng các khoản trừ. **Thực lĩnh = tổng thu nhập − tổng khấu trừ.** Không tự chọn công thức OT theo tên/mã khoản và không tự thêm hệ số 1,5.

Ví dụ C = 22 và S = 26:

| Khoản | Cách tính | A | Thành tiền |
| --- | --- | --- | --- |
| Lương cơ bản | Theo công chuẩn | 13.000.000 đ | 11.000.000 đ |
| Ăn trưa | Theo công chuẩn | 780.000 đ | 660.000 đ |
| Phụ cấp | Cố định | 500.000 đ | 500.000 đ |
| Khấu trừ | Cố định | 100.000 đ | 100.000 đ |

Thực lĩnh = 11.000.000 + 660.000 + 500.000 − 100.000 = **12.060.000 đ**.

## Thanh toán và lịch sử

- Chỉ quyền `PAYROLL_PAY` được ghi nhận thanh toán; quyền tạo/chỉnh bảng là `PAYROLL_MANAGE`, duyệt là `PAYROLL_CONFIRM`.
- Chỉ thanh toán phiếu thuộc bảng đã duyệt, chưa bị xóa, chưa thanh toán và thực lĩnh lớn hơn 0.
- Ngày trả không được ở tương lai theo múi giờ Việt Nam. Chuyển khoản cần mã giao dịch/chứng từ; tiền mặt không bắt buộc mã tham chiếu.
- Backend lưu số tiền đã trả từ thực lĩnh đã khóa, ngày, hình thức, tham chiếu và người ghi nhận. Không cho trả lần hai hoặc đổi `PAID` về `UNPAID`.
- QR hỗ trợ nhập thông tin chuyển khoản; tạo/xem QR không đổi trạng thái thanh toán. Bảng tổng hợp hiển thị tổng thực lĩnh, đã trả và chưa trả.
- Khi duyệt lưu công chuẩn, số tiền, loại khoản và cách tính riêng cho lịch sử. Sửa cấu hình nhân viên sau đó không đổi số tiền/công thức đã lưu của bảng được duyệt. Kỳ có bảng đã duyệt không được sửa/xóa.
- Dòng lương cũ trống có thể lấy lại cấu hình đã lưu sau lúc tạo bảng. Cấu hình/định nghĩa khoản đã xóa bị loại khỏi cả hồ sơ hiển thị lẫn tính lương. Bảng đã duyệt giữ số tiền cũ; trường lịch sử còn thiếu không được tự dựng lại.
- Cách tính theo đơn giá nhân trực tiếp công (`DAILY_MULTIPLIED`) chỉ còn phục vụ đọc lịch sử. Cấu hình hiện tại phải là cố định hoặc theo công chuẩn; cập nhật tại hồ sơ/cấu hình khoản lương, không đổi ngầm đơn giá thành tiền tháng.

## API

| Thao tác | Endpoint |
| --- | --- |
| Tạo bảng và phiếu cho nhân viên được chọn | `POST /api/payrolls` với query kỳ/tên/mã/mô tả và JSON `{staffIds: [...]}`; API cũ không có JSON vẫn tạo nháp trống |
| Thêm nhân viên đã chọn | `POST /api/payrolls/{id}/staffs` với `{staffIds: [...]}` |
| Cập nhật khoản lương từ hồ sơ cho một nhân viên | `POST /api/payrolls/payslip/{id}/calculate` |
| Bỏ nhân viên khỏi bảng chưa duyệt | `DELETE /api/payrolls/payslip/{id}` |
| Cập nhật cấu hình nhân viên/công, tính lại bảng chưa duyệt | `POST /api/payrolls/{id}/calculate` |
| Duyệt bảng | `PUT /api/payrolls/{id}/confirm` |
| Ghi nhận thanh toán | `POST /api/payrolls/payslip/{id}/payment` với `{paidDate, paymentMethod, paymentReference, note}` |

Các mutation khóa kỳ rồi bảng theo cùng thứ tự để tránh duyệt/thanh toán/tính lại/xóa đồng thời làm mất dữ liệu. API chỉnh phiếu dự thảo cũ trả 409 và hướng dẫn sửa hồ sơ. API đổi trạng thái thanh toán cũ chỉ cho sửa ghi chú, không được dùng để bỏ qua luồng ghi nhận thanh toán.

Nếu môi trường tắt Hibernate tự cập nhật schema, chạy migration bổ sung cột [20261012_manual_payroll_payment.sql](database/20261012_manual_payroll_payment.sql) trước khi triển khai.
