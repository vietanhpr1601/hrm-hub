package hrm.service;

import hrm.model.Payslip;
import hrm.model.PayslipItem;
import hrm.model.Payroll;
import hrm.model.Staff;
import hrm.model.StaffSalaryItem;

import java.time.LocalDate;
import java.util.List;

public interface PayslipService {

    Payslip calculateFromStaffConfiguration(Payslip payslip, LocalDate start, LocalDate end,
            double standardWorkDays, List<StaffSalaryItem> staffSalaryItems);

    Payslip calculateWithItems(Payslip payslip, LocalDate start, LocalDate end,
            double standardWorkDays, List<PayslipItem> items);
    Payslip calculateStaffPayslip(
            Staff staff, 
            Payroll payroll, 
            LocalDate start, 
            LocalDate end, 
            double standardWorkDays, 
            List<StaffSalaryItem> staffSalaryItems
    );
}
