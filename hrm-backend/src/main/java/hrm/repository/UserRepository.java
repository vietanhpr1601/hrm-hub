package hrm.repository;

import hrm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    @org.springframework.data.jpa.repository.Query("select u from User u where u.staff.id = :staffId "
            + "and (u.isDeleted = false or u.isDeleted is null)")
    java.util.List<User> findNotificationRecipients(@org.springframework.data.repository.query.Param("staffId") UUID staffId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u order by u.id")
    java.util.List<User> lockUsersForAccountChange();
}
