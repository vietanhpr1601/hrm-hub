package hrm;

import hrm.enums.PayrollStatus;
import hrm.enums.SalaryCalculationType;
import hrm.enums.SalaryItemType;
import hrm.model.Payroll;
import hrm.model.Payslip;
import hrm.model.Period;
import hrm.model.Staff;
import hrm.model.User;
import hrm.model.SalaryItem;
import hrm.model.StaffSalaryItem;
import hrm.repository.PayrollRepository;
import hrm.repository.PayslipRepository;
import hrm.repository.PeriodRepository;
import hrm.repository.StaffRepository;
import hrm.repository.SalaryItemRepository;
import hrm.repository.StaffSalaryItemRepository;
import hrm.service.PayrollService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@HrmIntegrationTest
class PayrollFlowTests {
    @Autowired private PayrollService payrollService;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private SalaryItemRepository salaryItemRepository;
    @Autowired private StaffSalaryItemRepository staffSalaryItemRepository;

    @Test
    void onlyOnePayrollCanBeConfirmedForTheSamePeriod() {
        Period period = period();
        Payroll first = validDraft(period);
        Payroll second = validDraft(period);
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(first.getId()).getStatus());
        assertThrows(IllegalArgumentException.class, () -> payrollService.confirmPayroll(second.getId()));
        assertEquals(PayrollStatus.DRAFT, second.getStatus());
    }

    @Test
    void confirmingSamePayrollAgainIsIdempotent() {
        Payroll payroll = validDraft(period());
        payrollService.confirmPayroll(payroll.getId());
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(payroll.getId()).getStatus());
    }

    @Test
    void deletedConfirmedPayrollDoesNotBlockItsReplacement() {
        Period period = period();
        payroll(period, PayrollStatus.CONFIRMED, true);
        Payroll replacement = validDraft(period);
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(replacement.getId()).getStatus());
    }

    @Test
    void deletedPayrollCannotBeConfirmed() {
        Payroll deleted = payroll(period(), PayrollStatus.DRAFT, true);
        assertThrows(IllegalArgumentException.class, () -> payrollService.confirmPayroll(deleted.getId()));
    }

    @Test
    void myPayslipSelectsOnlyOwnConfirmedActiveData() {
        Period period = period();
        Staff staff = staff();
        Payroll confirmed = payroll(period, PayrollStatus.CONFIRMED, false);
        Payslip expected = payslip(staff, confirmed, false);
        payslip(staff(), confirmed, false);
        payslip(staff, payroll(period, PayrollStatus.DRAFT, false), false);
        payslip(staff, payroll(period, PayrollStatus.CONFIRMED, true), false);
        payslip(staff, confirmed, true);
        assertEquals(expected.getId(), payrollService.getMyPayslip(period.getId(), user(staff)).getId());
    }

    @Test
    void historicalDuplicatesProduceActionableValidationError() {
        Period period = period();
        Staff staff = staff();
        payslip(staff, payroll(period, PayrollStatus.CONFIRMED, false), false);
        payslip(staff, payroll(period, PayrollStatus.CONFIRMED, false), false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> payrollService.getMyPayslip(period.getId(), user(staff)));
        assertTrue(error.getMessage().contains("nhiều phiếu lương"));
    }

    @Test
    void employeeWithoutConfirmedPayslipGetsValidationError() {
        Period period = period();
        Staff staff = staff();
        payslip(staff, payroll(period, PayrollStatus.DRAFT, false), false);
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.getMyPayslip(period.getId(), user(staff)));
    }

    private Period period() {
        Period period = new Period();
        period.setName("Regression payroll period");
        period.setCode(UUID.randomUUID().toString());
        period.setMonth(9);
        period.setYear(2026);
        period.setStandardWorkDays(26.0);
        return periodRepository.saveAndFlush(period);
    }

    private Payroll payroll(Period period, PayrollStatus status, boolean deleted) {
        Payroll payroll = new Payroll();
        payroll.setName("Regression payroll");
        payroll.setCode(UUID.randomUUID().toString());
        payroll.setPeriod(period);
        payroll.setStatus(status);
        payroll.setIsDeleted(deleted);
        return payrollRepository.saveAndFlush(payroll);
    }

    private Staff staff() {
        Staff staff = new Staff();
        staff.setStaffCode(UUID.randomUUID().toString());
        staff.setDisplayName("Payroll test employee");
        return staffRepository.saveAndFlush(staff);
    }

    private Payslip payslip(Staff staff, Payroll payroll, boolean deleted) {
        Payslip payslip = new Payslip();
        payslip.setStaff(staff);
        payslip.setPayroll(payroll);
        payslip.setIsDeleted(deleted);
        return payslipRepository.saveAndFlush(payslip);
    }

    private User user(Staff staff) {
        User user = new User();
        user.setStaff(staff);
        return user;
    }

    private Payroll validDraft(Period period) {
        Payroll payroll = payroll(period, PayrollStatus.DRAFT, false);
        Staff staff = staff();
        SalaryItem item = new SalaryItem();
        item.setCode(UUID.randomUUID().toString());
        item.setName("Fixed salary");
        item.setCalculationType(SalaryCalculationType.FIXED);
        item.setType(SalaryItemType.INCOME);
        item = salaryItemRepository.saveAndFlush(item);
        StaffSalaryItem configuration = new StaffSalaryItem();
        configuration.setStaff(staff);
        configuration.setSalaryItem(item);
        configuration.setAmount(1000.0);
        staffSalaryItemRepository.saveAndFlush(configuration);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        return payroll;
    }
}
