package hrm.service;

import hrm.dto.response.NotificationDto;
import hrm.enums.NotificationType;
import hrm.exception.ResourceNotFoundException;
import hrm.model.LeaveRequest;
import hrm.model.Notification;
import hrm.model.Timesheet;
import hrm.model.User;
import hrm.repository.NotificationRepository;
import hrm.repository.UserRepository;
import hrm.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final SecurityUtils securityUtils;

    public NotificationService(NotificationRepository notifications, UserRepository users, SecurityUtils securityUtils) {
        this.notifications = notifications;
        this.users = users;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(int pageIndex, int pageSize, boolean unreadOnly) {
        UUID userId = currentRecipientId();
        if (pageIndex < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Trang phải từ 1 và số thông báo mỗi trang từ 1 đến 100");
        }
        PageRequest page = PageRequest.of(pageIndex - 1, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNull(userId, page)
                : notifications.findByRecipientId(userId, page);
        return result.map(NotificationDto::new);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientIdAndReadAtIsNull(currentRecipientId());
    }

    public NotificationDto markRead(UUID id) {
        Notification notification = notifications.findOwnedForUpdate(id, currentRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        if (notification.getReadAt() == null) notification.setReadAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        return new NotificationDto(notifications.save(notification));
    }

    public void markAllRead() {
        notifications.markAllRead(currentRecipientId(), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    // MANDATORY keeps the persisted event in the same transaction as the approval.
    @Transactional(propagation = Propagation.MANDATORY)
    public void attendanceApproved(Timesheet timesheet) {
        if (timesheet.getStaff() == null) return;
        send(timesheet.getStaff().getId(), NotificationType.ATTENDANCE_APPROVED,
                "Chấm công đã được phê duyệt",
                "Chấm công ngày " + DATE_FORMAT.format(timesheet.getWorkingDate()) + " của bạn đã được phê duyệt.",
                timesheet.getId(), timesheet.getWorkingDate());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void leaveApproved(LeaveRequest leave) {
        if (leave.getRequestStaff() == null) return;
        String dates = leave.getFromDate().equals(leave.getToDate())
                ? "ngày " + DATE_FORMAT.format(leave.getFromDate())
                : "từ ngày " + DATE_FORMAT.format(leave.getFromDate()) + " đến ngày " + DATE_FORMAT.format(leave.getToDate());
        send(leave.getRequestStaff().getId(), NotificationType.LEAVE_APPROVED,
                "Đơn nghỉ phép đã được phê duyệt",
                "Đơn nghỉ phép " + dates + " của bạn đã được phê duyệt.", leave.getId(), leave.getFromDate());
    }

    private void send(UUID staffId, NotificationType type, String title, String message,
                      UUID referenceId, LocalDate referenceDate) {
        // Locked accounts still receive history; their authentication is denied until reactivation.
        for (User recipient : users.findNotificationRecipients(staffId)) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setReferenceId(referenceId);
            notification.setReferenceDate(referenceDate);
            notifications.save(notification);
        }
    }

    private UUID currentRecipientId() {
        User user = securityUtils.getCurrentUser();
        if (user == null) throw new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập lại");
        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new DisabledException("Tài khoản đã bị khóa hoặc vô hiệu hóa");
        }
        return user.getId();
    }
}
