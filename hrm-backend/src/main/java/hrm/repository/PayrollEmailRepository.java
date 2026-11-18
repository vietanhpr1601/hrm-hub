package hrm.repository;

import hrm.model.PayrollEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PayrollEmailRepository extends JpaRepository<PayrollEmail, UUID> {
    Optional<PayrollEmail> findByPayslipIdAndEventType(UUID payslipId, PayrollEmail.EventType eventType);
    default Optional<PayrollEmail> findByPayslipId(UUID payslipId) {
        return findByPayslipIdAndEventType(payslipId, PayrollEmail.EventType.PAYMENT);
    }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PayrollEmail e where e.id = :id")
    Optional<PayrollEmail> lockById(@Param("id") UUID id);
    @Query("select e.id from PayrollEmail e where e.status = :status order by e.createDate, e.id")
    List<UUID> findIdsByStatus(@Param("status") PayrollEmail.Status status, Pageable page);
}
