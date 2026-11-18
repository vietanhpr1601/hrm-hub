# Trợ lý HRM

Chat dùng HTTP JSON thông thường, hoạt động qua proxy `/api` và Cloudflare Quick Tunnel, không cần SSE.

## Khả năng

- Tra cứu trực tiếp: `Danh sách phòng ban`, `Tổng số nhân viên`, `Danh sách nhân viên`, `Tìm nhân viên NV001` (hoặc tên/phòng ban). Không cần API key.
- Hiển thị nguồn dữ liệu đã truy vấn và thời gian theo múi giờ Việt Nam.
- Danh sách tối đa 30 dòng, luôn trả tổng kết quả; tìm theo tên không phân biệt dấu tiếng Việt.
- Hồ sơ nhân sự đã xóa không xuất hiện. Tổng hồ sơ không đồng nghĩa số nhân viên đang làm việc.
- Câu hỏi tự nhiên dùng Groq và công cụ đọc dữ liệu có kiểm tra quyền. Không tự sửa dữ liệu.
- Chưa có công cụ tra cứu lương, nghỉ phép, chấm công hoặc tài liệu chính sách. Các chủ đề này chỉ được hướng dẫn tham khảo, không khẳng định số liệu công ty.
- Quyền `AI_CHAT` cho phép dùng chat; `STAFF_VIEW` và `DEPARTMENT_VIEW` kiểm soát dữ liệu riêng biệt.
- Chat chỉ đưa mã, tên, email công việc, phòng ban và chức danh cho công cụ AI. Không đưa CCCD, địa chỉ, số bảo hiểm hoặc số điện thoại.
- Khi AI không khả dụng, trả thông báo rõ ràng; không giả lập một câu trả lời AI bằng danh sách toàn bộ nhân viên.

## Cấu hình Groq

Tạo API key trong tài khoản Groq của bạn. Trong PowerShell dùng chạy ứng dụng:

```powershell
cd D:\Project\Hrm
$env:GROQ_API_KEY = '<API-key-cua-ban>'
$env:GROQ_MODEL = 'llama-3.3-70b-versatile'
.\stop.cmd
.\build.cmd
.\start.cmd
```

Key chỉ nằm phía backend. Không đặt key trong biến `VITE_*` hoặc commit vào source. Request chat không sử dụng key/model do trình duyệt gửi lên. Dữ liệu công cụ mà người dùng được phép truy cập có thể được gửi đến Groq khi AI yêu cầu tra cứu; các lệnh tra cứu trực tiếp ở trên không gọi Groq.

Sau lần build đầu, chỉ cần restart backend trong terminal có biến `GROQ_API_KEY` để thay key. Chat chạy qua HTTP client chung, tự làm mới access token theo cơ chế của ứng dụng. Lịch sử chỉ lưu trong bộ nhớ tab và được xóa khi đổi tài khoản; không lưu API key trong trình duyệt.

## Kiểm tra

- `AiChatServiceTests`: phân quyền dữ liệu, tổng hồ sơ, tìm kiếm tiếng Việt, giới hạn danh sách, giả mạo role, nhiều tool call và lỗi nhà cung cấp (không gọi API thật).
- `AiChatIntegrationTests`: xác thực, quyền chat và quyền dữ liệu độc lập, JSON response, validation.
- Frontend: lint/build và bộ kiểm thử HTTP client, gồm làm mới token và đường dẫn API tương đối.

Chất lượng câu trả lời tự nhiên còn phụ thuộc mô hình. Nguồn hiển thị là danh mục thực sự đã được công cụ truy vấn, không phải chứng nhận mọi câu AI đều chính xác. Không có khả năng duyệt web hoặc xác minh quy định pháp luật mới nhất.
