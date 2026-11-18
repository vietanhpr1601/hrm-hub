# Email duyệt lương và thanh toán qua Gmail

## Thời điểm và người nhận

Hệ thống tạo **hai thông báo độc lập** cho mỗi phiếu lương:

1. Sau khi **duyệt bảng lương**, báo phiếu lương đã sẵn sàng và số thực lĩnh đã chốt. Nội dung nói rõ chưa xác nhận đã trả tiền.
2. Sau khi **Ghi nhận đã thanh toán**, báo số tiền thực tế đã ghi nhận chi trả, ngày và hình thức trả.

Mỗi thông báo lưu trong cùng giao dịch nghiệp vụ tương ứng. Tạo bảng nháp chưa gửi email. Không gửi bù cho những lần duyệt/thanh toán trước khi triển khai; email thanh toán đang có được giữ lại khi nâng cấp.

Người nhận là **Email công ty** trong hồ sơ nhân viên, nếu trống thì dùng **Email** thông thường. Nếu thiếu hoặc không hợp lệ, thanh toán vẫn được lưu, email có trạng thái lỗi. Cập nhật hồ sơ rồi dùng **Thử gửi lại**.

Email văn bản tiếng Việt gồm nhân viên, kỳ/bảng lương, số tiền đúng thời điểm thông báo và hướng dẫn mở Phiếu lương cá nhân. Không đính kèm mật khẩu hay thông tin tài khoản ngân hàng. Không gửi tới các nhân viên khác.

## Khởi động nhanh bằng Gmail

1. Bật Xác minh 2 bước trong tài khoản Google dùng gửi thư.
2. Tạo Mật khẩu ứng dụng cho HRM tại https://myaccount.google.com/apppasswords. Một số tài khoản có chính sách hạn chế nên không có tùy chọn này; xem hướng dẫn Google: https://support.google.com/accounts/answer/185833?hl=vi.
3. Tại PowerShell ở thư mục source, chạy:

```powershell
cd D:\Project\Hrm
.\start-gmail.cmd
```

Lệnh hỏi địa chỉ Gmail và mật khẩu ứng dụng 16 ký tự (ẩn khi nhập), đặt SMTP `smtp.gmail.com`, cổng 587, STARTTLS bắt buộc, rồi khởi động lại HRM. Không dùng mật khẩu Google thông thường. Không gửi mật khẩu trong chat. Lệnh không lưu mật khẩu vào source; phải nhập lại khi dùng lệnh ở lần sau. Tài liệu SMTP Google: https://developers.google.com/workspace/gmail/imap/imap-smtp.

Lưu ý: bật Gmail sẽ gửi các thư PENDING đã lưu trong hàng chờ. Cần máy chạy và có Internet. Chỉ khởi động bình thường bằng `start.cmd` trong terminal mới thì Gmail không tự được bật lại; dùng `start-gmail.cmd` để nhập cấu hình.

## Cấu hình SMTP

Mặc định `PAYROLL_MAIL_ENABLED=false`: email nằm trong hàng chờ, không có thư nào được gửi. Cần thông tin SMTP từ tài khoản email của đơn vị. Không nhập mật khẩu vào chat hoặc commit vào source.

Trong PowerShell dùng khởi động phần mềm:

```powershell
cd D:\Project\Hrm
$env:PAYROLL_MAIL_ENABLED = 'true'
$env:MAIL_HOST = '<may-chu-SMTP>'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = '<dia-chi-email-gui>'
$env:MAIL_FROM = '<dia-chi-email-gui>'
# Nhập mật khẩu SMTP/mật khẩu ứng dụng tại máy, không hiện khi gõ.
$smtpCredential = Get-Credential -UserName $env:MAIL_USERNAME -Message 'Nhập mật khẩu SMTP hoặc mật khẩu ứng dụng'
$env:MAIL_PASSWORD = $smtpCredential.GetNetworkCredential().Password
$env:MAIL_STARTTLS = 'true'
$env:MAIL_SSL = 'false'
.\stop.cmd
.\start.cmd
```

Chọn host, port và kiểu xác thực theo hướng dẫn của nhà cung cấp. Cấu hình mặc định dùng SMTP AUTH và STARTTLS bắt buộc. Nếu nhà cung cấp dùng TLS trực tiếp cổng 465, đặt `MAIL_PORT=465`, `MAIL_SSL=true`, `MAIL_STARTTLS=false`. `MAIL_SMTP_AUTH=false` chỉ dùng khi SMTP nội bộ của đơn vị cho phép. Tài khoản chỉ hỗ trợ OAuth cần tích hợp tương ứng; không dùng mật khẩu thông thường nếu nhà cung cấp không cho phép.

Các biến trên chỉ tồn tại trong phiên PowerShell và các tiến trình khởi động từ đó. Cần cấu hình lại sau khi mở terminal mới hoặc dùng cơ chế lưu bí mật của máy chủ. Thay mật khẩu/cấu hình cần restart backend. Bản source thay đổi cần build trước lần sử dụng đầu tiên.

## Theo dõi và xử lý lỗi

Trong chi tiết phiếu lương, người có `PAYROLL_VIEW` thấy hai trạng thái riêng (Duyệt bảng lương / Xác nhận thanh toán), người nhận, thời điểm SMTP tiếp nhận và nút làm mới. Người có `PAYROLL_PAY` được thử lại từng email FAILED; không gửi lại email SENT/PENDING. Phần xem phiếu cá nhân không làm lộ trạng thái email của người khác.

- Worker chạy khi bật gửi email, kiểm tra hàng chờ mỗi 30 giây, tối đa 20 thư mỗi lượt. Máy chủ phải đang chạy và kết nối được SMTP.
- Chỉ dữ liệu đã commit mới được worker thấy. Duyệt/thanh toán rollback không tạo email để gửi. Unique (payslip_id, event_type) và khóa hàng tránh tạo job/gửi đồng thời cho cùng một phiếu và cùng loại thông báo.
- Lỗi SMTP không làm đảo trạng thái đã thanh toán. Không tự lặp lại email lỗi; kiểm tra cấu hình và hộp thư rồi thử lại thủ công.
- SENT nghĩa là máy chủ SMTP đã nhận thư, chưa đảm bảo thư vào Inbox. Cần kiểm tra Spam và cấu hình nhà cung cấp.
- SMTP không hỗ trợ giao dịch nguyên tử cùng PostgreSQL. Nếu tiến trình dừng sau lúc SMTP nhận nhưng trước lúc ghi SENT, có thể gửi trùng khi khởi động lại; tương tự timeout có thể xảy ra sau khi SMTP đã nhận thư. Vì vậy kiểm tra hộp thư trước khi thử gửi lại.
- Email chưa cấu hình vẫn được giữ PENDING. Khi bật SMTP, các thư PENDING trong hàng chờ sẽ được gửi; kiểm tra các phiếu đã ghi nhận trong thời gian chờ trước khi bật.

API: `GET /api/payrolls/payslip/{id}/emails` trả hai loại thông báo; endpoint `/email` cũ giữ thông báo thanh toán. `POST /api/payrolls/payslip/{id}/email/retry?eventType=CONFIRMATION` hoặc `PAYMENT` thử lại đúng loại.

Bảng `tbl_payroll_email` được tạo bằng Hibernate update đang dùng. `start.cmd` tự nâng cấp khóa duy nhất sang (payslip_id, event_type), giữ dữ liệu cũ là PAYMENT. Khi chạy backend trực tiếp không qua start/setup-db, phải áp dụng `docs/database/20261026_payroll_email_events.sql` trước lần nâng cấp. Môi trường tắt Hibernate update cần chạy `docs/database/20261026_payroll_email.sql` cho cài mới, hoặc file events cho cài cũ.

Kiểm thử dùng H2 và JavaMailSender giả lập, không gửi thư thật. Cần kiểm tra thực tế với SMTP của đơn vị sau khi cấu hình.
