package hrm.repository;

import hrm.model.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id and n.recipient.id = :recipientId")
    Optional<Notification> findOwnedForUpdate(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
