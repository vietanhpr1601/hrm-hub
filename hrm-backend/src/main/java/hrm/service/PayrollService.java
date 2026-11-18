package hrm.service;

import hrm.model.*;
import hrm.dto.request.PayslipDraftRequest;
import hrm.dto.request.PayslipPaymentRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollService {
    Payroll updateDetails(UUID id, hrm.dto.request.PayrollDetailsRequest request);

    Payroll createPayroll(UUID periodId, String name, String code, String description);

    Payroll createPayrollWithStaffs(UUID periodId, String name, String code, String description, List<UUID> staffIds);

    List<Payroll> getPayrollsByPeriod(UUID periodId);

    List<Payroll> getAllPayrolls();

    List<Payslip> calculatePayroll(UUID payrollId);

    Payslip calculatePayslip(UUID id);

    List<Payslip> enrollStaffs(UUID payrollId, List<UUID> staffIds);

    Payslip updateDraftPayslip(UUID id, PayslipDraftRequest request);

    void removePayslip(UUID id);

    Payslip recordPayment(UUID id, PayslipPaymentRequest request);

    List<Payslip> getPayrollDetails(UUID payrollId);

    Payroll confirmPayroll(UUID payrollId);

    void deletePayroll(UUID payrollId);

    Payslip getMyPayslip(UUID periodId, User currentUser);

    Payslip updatePayslip(UUID id, hrm.enums.PaidStatus paidStatus, String note);
}
