package hrm.model;

import hrm.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tbl_notification", indexes = {
        @Index(name = "idx_notification_recipient_created", columnList = "recipient_id,created_at"),
        @Index(name = "idx_notification_recipient_read", columnList = "recipient_id,read_at")
})
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // The approval belongs to this account permanently, even after its staff link changes.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "reference_date", nullable = false, updatable = false)
    private LocalDate referenceDate;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
}
