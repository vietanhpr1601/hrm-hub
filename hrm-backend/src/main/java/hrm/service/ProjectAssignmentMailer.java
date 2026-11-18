package hrm.service;

import hrm.enums.ProjectRole;
import hrm.model.Project;
import hrm.model.Staff;
import hrm.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gửi email "Bạn được phân công vào dự án" tới Gmail (ưu tiên) của nhân viên, sau khi lưu dự án thành công. */
@Service
public class ProjectAssignmentMailer {
    private static final Logger log = LoggerFactory.getLogger(ProjectAssignmentMailer.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final PayrollMailGateway mail;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "project-assignment-mail");
        thread.setDaemon(true);
        return thread;
    });

    public ProjectAssignmentMailer(PayrollMailGateway mail) { this.mail = mail; }

    public static String roleText(ProjectRole role, String roleName) {
        String permission = role == ProjectRole.MANAGER ? "Quản lý dự án" : "Người thực hiện";
        return roleName == null || roleName.isBlank() ? permission : roleName.trim() + " (" + permission + ")";
    }

    public static String subject(Project project) {
        String subject = "HRM - Bạn được phân công vào dự án: " + project.getName();
        subject = subject.replace('\r', ' ').replace('\n', ' ');
        return subject.substring(0, Math.min(255, subject.length()));
    }

    public static String content(Project project, Staff staff, String role, String assignedBy) {
        StringBuilder text = new StringBuilder();
        text.append("Xin chào ").append(staff.getDisplayName()).append(",\n\n")
            .append("Bạn vừa được phân công vào một dự án trên hệ thống HRM.\n\n")
            .append("Dự án: ").append(project.getName());
        if (project.getCode() != null) text.append(" (").append(project.getCode()).append(")");
        text.append("\n");
        if (project.getCompanyName() != null) text.append("Công ty / khách hàng: ").append(project.getCompanyName()).append("\n");
        text.append("Vai trò của bạn: ").append(role).append("\n");
        if (project.getStartDate() != null || project.getEndDate() != null) {
            text.append("Thời gian: ").append(project.getStartDate() == null ? "?" : DATE.format(project.getStartDate()))
                .append(" - ").append(project.getEndDate() == null ? "chưa xác định" : DATE.format(project.getEndDate())).append("\n");
        }
        if (assignedBy != null && !assignedBy.isBlank()) text.append("Người phân công: ").append(assignedBy).append("\n");
        text.append("\nĐăng nhập HRM → Công việc & Dự án → Dự án để xem chi tiết và công việc được giao.\n\n")
            .append("Đây là email tự động từ hệ thống HRM.");
        return text.toString();
    }

    public static String taskContent(Task task, Staff staff, String assignedBy) {
        StringBuilder text = new StringBuilder();
        text.append("Xin chào ").append(staff.getDisplayName()).append(",\n\n")
            .append("Bạn vừa được giao một công việc trên hệ thống HRM.\n\n")
            .append("Công việc: ").append(task.getName());
        if (task.getCode() != null) text.append(" (").append(task.getCode()).append(")");
        text.append("\n");
        if (task.getProject() != null) {
            text.append("Dự án: ").append(task.getProject().getName()).append("\n");
            if (task.getProject().getCompanyName() != null) text.append("Công ty / khách hàng: ").append(task.getProject().getCompanyName()).append("\n");
        }
        if (task.getStatus() != null) text.append("Trạng thái: ").append(task.getStatus().getName()).append("\n");
        if (task.getEndTime() != null) text.append("Hạn hoàn thành: ").append(DATE.format(task.getEndTime())).append("\n");
        if (assignedBy != null && !assignedBy.isBlank()) text.append("Người giao: ").append(assignedBy).append("\n");
        text.append("\nĐăng nhập HRM → Công việc & Dự án → Công việc để xem và cập nhật tiến độ.\n\n")
            .append("Đây là email tự động từ hệ thống HRM.");
        return text.toString();
    }

    public void notifyTaskAssigned(Task task, Staff staff, String assignedBy) {
        if (task == null || staff == null) return;
        String subject = ("HRM - Bạn được giao công việc: " + task.getName()).replace('\r', ' ').replace('\n', ' ');
        queue(staff, subject.substring(0, Math.min(255, subject.length())), taskContent(task, staff, assignedBy));
    }

    /** Xếp email vào hàng đợi; chỉ gửi khi giao dịch lưu dự án đã thành công và email đã được cấu hình. */
    public void notifyAssigned(Project project, Staff staff, ProjectRole role, String roleName, String assignedBy) {
        if (staff == null) return;
        queue(staff, subject(project), content(project, staff, roleText(role, roleName), assignedBy));
    }

    private void queue(Staff staff, String subject, String body) {
        String to = PayrollEmailService.recipient(staff);
        if (!PayrollEmailService.validEmail(to)) {
            log.info("Không gửi email phân công: nhân viên {} chưa có email hợp lệ", staff.getStaffCode());
            return;
        }
        Runnable send = () -> executor.execute(() -> {
            if (!mail.ready()) { log.info("Email chưa được cấu hình; bỏ qua email phân công dự án cho {}", staff.getStaffCode()); return; }
            try { mail.send(to, subject, body); }
            catch (RuntimeException error) { log.warn("Gửi email phân công dự án cho {} thất bại: {}", staff.getStaffCode(), error.getClass().getSimpleName()); }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
