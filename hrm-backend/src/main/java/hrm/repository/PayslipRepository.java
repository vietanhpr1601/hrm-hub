package hrm.repository;

import hrm.model.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import hrm.enums.PayrollStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID>, JpaSpecificationExecutor<Payslip> {
    void deleteByPayrollId(UUID payrollId);
    List<Payslip> findByPayrollId(UUID payrollId);
    Optional<Payslip> findByStaffIdAndPayrollId(UUID staffId, UUID payrollId);
    @Query("SELECT p FROM Payslip p WHERE p.staff.id = :staffId AND p.payroll.period.id = :periodId "
            + "AND p.payroll.status = :status AND (p.isDeleted IS NULL OR p.isDeleted = false) "
            + "AND (p.payroll.isDeleted IS NULL OR p.payroll.isDeleted = false)")
    List<Payslip> findActiveByStaffAndPeriodAndStatus(@Param("staffId") UUID staffId,
            @Param("periodId") UUID periodId, @Param("status") PayrollStatus status);
}
