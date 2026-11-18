package hrm.dto.response;

import hrm.enums.NotificationType;
import hrm.model.Notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NotificationDto(UUID id, NotificationType type, String title, String message,
                              Instant createdAt, Instant readAt, UUID referenceId, LocalDate referenceDate) {
    public NotificationDto(Notification notification) {
        this(notification.getId(), notification.getType(), notification.getTitle(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt(), notification.getReferenceId(),
                notification.getReferenceDate());
    }
}
