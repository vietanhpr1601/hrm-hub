package hrm.service;

import hrm.enums.PaidStatus;
import hrm.model.PayrollEmail;
import hrm.model.Payslip;
import hrm.model.Staff;
import hrm.repository.PayrollEmailRepository;
import hrm.repository.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.NumberFormat;
import java.time.*;
import java.util.*;

@Service
public class PayrollEmailService {
    public record Status(String status, String recipient, String message, String sentAt, int attempts, boolean configured,
                         PayrollEmail.EventType eventType) {}
    private final PayrollEmailRepository emails;
    private final PayslipRepository payslips;
    private final PayrollMailGateway mail;
    public PayrollEmailService(PayrollEmailRepository emails, PayslipRepository payslips, PayrollMailGateway mail) {
        this.emails = emails; this.payslips = payslips; this.mail = mail;
    }

    // Joins the payment transaction: a rollback cannot leave a sendable email behind.
    @Transactional
    public void enqueuePayment(Payslip slip) {
        if (slip.getPaidStatus() != PaidStatus.PAID) throw new IllegalStateException("Chỉ gửi thông báo cho phiếu đã thanh toán");
        enqueue(slip, PayrollEmail.EventType.PAYMENT);
    }

    @Transactional
    public void enqueueConfirmation(Payslip slip) {
        if (slip.getPayroll().getStatus() != hrm.enums.PayrollStatus.CONFIRMED)
            throw new IllegalStateException("Chỉ gửi thông báo khi bảng lương đã được duyệt");
        enqueue(slip, PayrollEmail.EventType.CONFIRMATION);
    }

    private void enqueue(Payslip slip, PayrollEmail.EventType event) {
        if (emails.findByPayslipIdAndEventType(slip.getId(), event).isPresent()) return;
        PayrollEmail job = new PayrollEmail();
        job.setPayslipId(slip.getId());
        job.setEventType(event);
        String address = recipient(slip);
        job.setRecipient(validEmail(address) ? address : null);
        boolean payment = event == PayrollEmail.EventType.PAYMENT;
        String subject = ("HRM - " + (payment ? "Thông báo thanh toán lương: " : "Phiếu lương đã được duyệt: ")
                + slip.getPayroll().getPeriod().getName()).replace('\r', ' ').replace('\n', ' ');
        job.setSubject(subject.substring(0, Math.min(255, subject.length())));
        String amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(payment ? slip.getPaidAmount() : slip.getNetSalary());
        job.setContent("Xin chào " + slip.getStaff().getDisplayName() + ",\n\n"
                + (payment ? "Hệ thống HRM đã ghi nhận thanh toán lương của bạn.\n" : "Bảng lương đã được duyệt và phiếu lương của bạn đã sẵn sàng. Thông báo này chưa xác nhận đã trả tiền.\n")
                + "Kỳ lương: " + slip.getPayroll().getPeriod().getName() + "\n"
                + "Bảng lương: " + slip.getPayroll().getName() + "\n"
                + (payment ? "Số tiền đã thanh toán: " : "Thực lĩnh trên phiếu lương: ") + amount + " VND\n"
                + (payment ? "Ngày thanh toán: " + slip.getPaidDate() + "\nHình thức: "
                    + (slip.getPaymentMethod() == hrm.enums.PaymentMethod.BANK_TRANSFER ? "Chuyển khoản" : "Tiền mặt") + "\n\n" : "\n")
                + "Bạn có thể đăng nhập HRM → Lương thưởng → Phiếu lương cá nhân để xem chi tiết.\n"
                + "Nếu có sai lệch, vui lòng liên hệ bộ phận nhân sự/kế toán.\n\n"
                + "Đây là email tự động từ hệ thống HRM.");
        if (!validEmail(job.getRecipient())) {
            job.setStatus(PayrollEmail.Status.FAILED); job.setErrorMessage("Hồ sơ nhân viên thiếu email hợp lệ. Cập nhật hồ sơ rồi thử lại.");
        }
        emails.save(job);
    }

    @Transactional(readOnly = true)
    public Status status(UUID payslipId) {
        return status(payslipId, PayrollEmail.EventType.PAYMENT);
    }

    @Transactional(readOnly = true)
    public List<Status> statuses(UUID payslipId) {
        return Arrays.stream(PayrollEmail.EventType.values()).map(event -> status(payslipId, event)).toList();
    }

    private Status status(UUID payslipId, PayrollEmail.EventType event) {
        requirePayslip(payslipId);
        return emails.findByPayslipIdAndEventType(payslipId, event).map(job -> new Status(job.getStatus().name(), job.getRecipient(),
                job.getErrorMessage(), job.getSentAt() == null ? null : job.getSentAt().atOffset(ZoneOffset.ofHours(7)).toString(),
                job.getAttempts(), mail.ready(), event)).orElse(new Status("NOT_QUEUED", null, "Chưa có thông báo email cho thời điểm này.", null, 0, mail.ready(), event));
    }

    @Transactional
    public Status retry(UUID payslipId) {
        return retry(payslipId, PayrollEmail.EventType.PAYMENT);
    }

    @Transactional
    public Status retry(UUID payslipId, PayrollEmail.EventType event) {
        var existing = emails.findByPayslipIdAndEventType(payslipId, event).orElseThrow(() -> new IllegalStateException("Phiếu chưa có thông báo email"));
        var job = emails.lockById(existing.getId()).orElseThrow();
        if (job.getStatus() != PayrollEmail.Status.FAILED) throw new IllegalStateException("Chỉ thử lại email gửi thất bại; không gửi lại email đã thành công hoặc đang chờ.");
        if (!mail.ready()) throw new IllegalStateException("Cần cấu hình tài khoản email trước khi thử lại.");
        Payslip slip = requirePayslip(payslipId);
        if (slip.getPayroll().getStatus() != hrm.enums.PayrollStatus.CONFIRMED)
            throw new IllegalStateException("Bảng lương chưa được duyệt");
        if (event == PayrollEmail.EventType.PAYMENT && slip.getPaidStatus() != PaidStatus.PAID)
            throw new IllegalStateException("Phiếu lương chưa thanh toán");
        String address = recipient(slip);
        if (!validEmail(address)) throw new IllegalArgumentException("Cập nhật email hợp lệ trong hồ sơ nhân viên trước khi thử lại.");
        job.setRecipient(address); job.setStatus(PayrollEmail.Status.PENDING); job.setErrorMessage(null);
        return status(payslipId, event);
    }

    @Transactional
    public void deliver(UUID id) {
        if (!mail.ready()) return;
        var job = emails.lockById(id).orElse(null);
        if (job == null || job.getStatus() != PayrollEmail.Status.PENDING) return;
        job.setAttempts(job.getAttempts() + 1);
        try {
            if (!validEmail(job.getRecipient())) throw new IllegalArgumentException("Invalid recipient");
            mail.send(job.getRecipient(), job.getSubject(), job.getContent());
            job.setStatus(PayrollEmail.Status.SENT); job.setErrorMessage(null);
            job.setSentAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        } catch (RuntimeException error) {
            job.setStatus(PayrollEmail.Status.FAILED);
            job.setErrorMessage("Máy chủ email chưa xác nhận gửi thành công. Kiểm tra cấu hình và hộp thư trước khi thử lại.");
        }
    }
    private Payslip requirePayslip(UUID id) {
        return payslips.findById(id).filter(slip -> !Boolean.TRUE.equals(slip.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Phiếu lương không tồn tại hoặc đã xóa"));
    }
    static boolean validEmail(String email) {
        return email != null && email.length() <= 254 && email.matches("^[^\\s@<>;,]+@[^\\s@<>;,]+\\.[^\\s@<>;,]+$");
    }
    /** Ưu tiên Gmail của nhân viên (email cá nhân trước, rồi email công ty); không có Gmail thì dùng email công ty, sau đó email cá nhân. */
    static String recipient(Staff staff) {
        String personal = Objects.toString(staff.getEmail(), "").trim();
        String company = Objects.toString(staff.getCompanyEmail(), "").trim();
        for (String address : List.of(personal, company)) {
            if (validEmail(address) && address.toLowerCase(Locale.ROOT).endsWith("@gmail.com")) return address;
        }
        return !company.isBlank() ? company : personal;
    }
    private String recipient(Payslip slip) {
        return recipient(slip.getStaff());
    }
}
