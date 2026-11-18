package hrm.repository;

import hrm.model.Payroll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, UUID>, JpaSpecificationExecutor<Payroll> {
    List<Payroll> findByPeriodId(UUID periodId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payroll p WHERE p.id = :id AND (p.isDeleted IS NULL OR p.isDeleted = false)")
    Optional<Payroll> findActiveByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM Payroll p WHERE (p.isDeleted IS NULL OR p.isDeleted = false) AND p.code = :code")
    Optional<Payroll> findActiveByCode(@Param("code") String code);
}
