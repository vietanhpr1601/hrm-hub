# Kiểm chứng bảng lương thủ công — 12/10/2026

## Kết quả bản cuối

- Backend: 68 kiểm thử liên quan đạt, gồm 46 `ManualPayrollIntegrationTests`, 7 `PayrollFlowTests`, 15 `BusinessAccessTests`.
- Frontend: `npm.cmd run check` đạt: 100 kiểm thử đạt, ESLint không có lỗi, Vite build thành công. Validator/test phiếu dự thảo đã được bỏ cùng màn hình không còn sử dụng. Các cảnh báo lint/chunk kích thước lớn hiện có không chặn build.
- Backend executable JAR đóng gói thành công, SHA256 `EE0B5DC21BB921AE9D829C253C7D0A2F3991323052787697CE2E656FCB50471D`.
- Đã thay bản chạy `hrm-backend/target/hrm-0.0.1-SNAPSHOT.jar` bằng artifact đã kiểm thử và khởi động lại qua script project. Frontend `http://127.0.0.1:5173` và backend `http://127.0.0.1:8080/api` hoạt động. Bản trước sửa đồng bộ hồ sơ giữ tại `.runtime/payroll-before-profile-sync.jar`.

## Lỗi khoản lương ở hồ sơ không vào bảng

Kiểm tra dữ liệu thực tế: NV2609_003 có 3 cấu hình đang sử dụng lưu sau khi bảng được tạo, nhưng dòng lương cũ có 0 khoản. Tính lại trước đây dùng chính danh sách khoản cũ của dòng lương, nên không nạp cấu hình hồ sơ mới. Bản sửa lấy cấu hình hồ sơ hiện tại ở cả tạo, tính lại toàn bảng/từng người và duyệt; giữ ID, ghi chú và thông tin thanh toán. Màn hình chỉnh phiếu dự thảo riêng được bỏ, API cũ bị chặn bằng 409.

Đã kiểm chứng API thực tế trên hàng chưa duyệt/chưa thanh toán của NV2609_003: cập nhật 0 → 3 khoản, đọc lại PostgreSQL xác nhận 3 khoản khớp hồ sơ. Có snapshot trước/sau trong `.runtime/profile-salary-refresh-20261012-1791822365904*.json`; không duyệt bảng hoặc ghi nhận thanh toán khi kiểm tra.

NV2609_005 trong cùng bảng thực sự không có cấu hình; giữ nguyên dữ liệu người này, thông báo thiếu cấu hình nêu tên/mã cụ thể để người dùng bổ sung hồ sơ hoặc bỏ khỏi bảng. Khoản định nghĩa đã xóa được loại đồng nhất khỏi hồ sơ hiển thị và tính lương.

## Sửa luồng tạo cho nhân viên

Hộp tạo ban đầu chỉ có kỳ/tên/mã/mô tả nên tạo bảng rỗng, còn nút thêm nhân viên trong trang chi tiết bị đưa vào menu tràn ở màn hình nhỏ. Bản sửa cho chọn nhân viên ngay lúc tạo, giữ các nút tạo/thêm hiện trực tiếp, bỏ mã cố định theo tháng để tránh lỗi trùng mã khi tạo nháp thứ hai.

API tạo nhận JSON `{staffIds}` và tạo bảng + dữ liệu lương trong cùng transaction. Chỉ nhân viên được chọn được thêm; thiếu cấu hình hiện được báo rõ thay vì tạo dòng trống để nhập lại. Quyền tạo, danh sách trùng/sai và cấu hình lỗi không để lại dữ liệu dở dang. Kiểm chứng rollback dùng transaction thực trên H2.

## Phạm vi kiểm thử

Backend kiểm tra chọn đúng nhân viên, lấy cấu hình hiện tại thay khoản cũ, bổ sung cấu hình sau lúc tạo bảng, tính lại/duyệt dữ liệu mới, cập nhật từng nhân viên độc lập, cấu hình đã xóa/sai/thiếu, vô hiệu override phiếu riêng, giữ ID/ghi chú/thanh toán, công đã duyệt, hai công thức, làm tròn nguyên đồng, mẫu số bằng 0, quyền tạo/duyệt/trả tiền, khóa sau duyệt, lịch sử đã duyệt bất biến, tiền mặt/chuyển khoản, chứng từ/ngày trả, trả trùng, API đổi trạng thái cũ và khóa kỳ. Có lifecycle HTTP và rollback transaction thực trên H2 độc lập.

Frontend kiểm tra công thức/preview, snapshot, trường thiếu, HALF_UP từng khoản, tổng thu nhập/khấu trừ, hai lựa chọn mới, dữ liệu lịch sử, điều kiện thanh toán và ngày Việt Nam. Đã rà soát tích hợp API, lựa chọn nhân viên, cập nhật từ hồ sơ, dữ liệu thanh toán và QR. Công cụ trình duyệt không khả dụng trong phiên này nên chưa kiểm tra trực quan trên trình duyệt.

Kiểm thử backend tự động dùng `@HrmIntegrationTest` với H2 độc lập. Kiểm chứng thực tế PostgreSQL chỉ cập nhật hàng lương lỗi đang chờ duyệt của NV2609_003 từ cấu hình đã lưu, không tạo bảng mới/duyệt/thanh toán.

## Lỗi ngoài phần bảng lương

Lần chạy toàn bộ backend trước lần chốt hai công thức có 241 kiểm thử và một lỗi:

`NotificationIntegrationTests.approvalHistoryIsCreatedWithoutViewPermissionAndAvailableAfterGrant:197` — mong đợi HTTP 403 nhưng nhận 200. Rerun độc lập vẫn gặp lỗi. Không sửa mã/test thông báo trong thay đổi bảng lương. Các kiểm thử bảng lương/phân quyền liên quan đều đạt ở bản cuối.

Log bản mới: `.runtime/payroll-profile-tests.log`, `.runtime/payroll-profile-backend-build.log`, `.runtime/payroll-profile-frontend-check.log`. Log kiểm thử tổng trước đó: `hrm-backend/target/payroll-full-tests.log`.

Quy trình sử dụng và công thức: [PAYROLL_MANUAL_PROCESS.md](PAYROLL_MANUAL_PROCESS.md).
