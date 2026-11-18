package hrm.service.impl;

import hrm.enums.PaidStatus;
import hrm.enums.SalaryCalculationType;
import hrm.enums.SalaryItemType;
import hrm.enums.TimesheetStatus;
import hrm.model.*;
import hrm.repository.TimesheetRepository;
import hrm.service.PayslipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class PayslipServiceImpl implements PayslipService {
    @Autowired private TimesheetRepository timesheetRepository;

    @Override
    public Payslip calculateStaffPayslip(Staff staff, Payroll payroll, LocalDate start, LocalDate end,
            double standardWorkDays, List<StaffSalaryItem> staffSalaryItems) {
        Payslip payslip = new Payslip();
        payslip.setPayroll(payroll);
        payslip.setStaff(staff);
        payslip.setPaidStatus(PaidStatus.UNPAID);
        return calculateFromStaffConfiguration(payslip, start, end, standardWorkDays, staffSalaryItems);
    }

    @Override
    public Payslip calculateFromStaffConfiguration(Payslip payslip, LocalDate start, LocalDate end,
            double standardWorkDays, List<StaffSalaryItem> staffSalaryItems) {
        List<PayslipItem> items = new ArrayList<>();
        Set<UUID> selected = new HashSet<>();
        for (StaffSalaryItem configuration : staffSalaryItems) {
            SalaryItem salaryItem = configuration.getSalaryItem();
            if (Boolean.TRUE.equals(configuration.getIsDeleted()) || salaryItem == null
                    || Boolean.TRUE.equals(salaryItem.getIsDeleted())) continue;
            if (!selected.add(salaryItem.getId()))
                throw new IllegalArgumentException("Nhân viên " + payslip.getStaff().getDisplayName()
                        + " có khoản lương bị trùng. Hãy kiểm tra mục Khoản lương trong hồ sơ nhân viên");
            PayslipItem item = new PayslipItem();
            item.setSalaryItem(salaryItem);
            item.setName(salaryItem.getName());
            item.setAmount(configuration.getAmount());
            item.setCalculationType(salaryItem.getCalculationType());
            item.setType(salaryItem.getType());
            items.add(item);
        }
        if (items.isEmpty())
            throw new IllegalArgumentException("Nhân viên " + payslip.getStaff().getDisplayName()
                    + " (" + payslip.getStaff().getStaffCode()
                    + ") chưa có khoản lương đang sử dụng. Hãy bổ sung và lưu tại mục Khoản lương trong hồ sơ nhân viên");
        return calculateWithItems(payslip, start, end, standardWorkDays, items);
    }

    @Override
    public Payslip calculateWithItems(Payslip payslip, LocalDate start, LocalDate end,
            double standardWorkDays, List<PayslipItem> items) {
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Khoảng thời gian kỳ lương không hợp lệ");
        if (!Double.isFinite(standardWorkDays) || standardWorkDays < 0)
            throw new IllegalArgumentException("Công chuẩn kỳ lương không hợp lệ");
        if (items == null) throw new IllegalArgumentException("Danh sách khoản lương là bắt buộc");
        items = new ArrayList<>(items);
        List<Timesheet> timesheets = timesheetRepository
                .findActiveTimesheetsByStaffIdAndWorkingDateBetweenAndStatus(
                        payslip.getStaff().getId(), start, end, TimesheetStatus.APPROVED);
        BigDecimal workDays = BigDecimal.ZERO;
        BigDecimal overtime = BigDecimal.ZERO;
        for (Timesheet timesheet : timesheets) {
            double work = timesheet.getTotalWorkRatio() != null ? timesheet.getTotalWorkRatio() : 0.0;
            double ot = timesheet.getOvertimeHours() != null ? timesheet.getOvertimeHours() : 0.0;
            if (!Double.isFinite(work) || work < 0 || !Double.isFinite(ot) || ot < 0)
                throw new IllegalArgumentException("Dữ liệu công đã duyệt không hợp lệ");
            workDays = workDays.add(BigDecimal.valueOf(work));
            overtime = overtime.add(BigDecimal.valueOf(ot));
        }
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal deduction = BigDecimal.ZERO;
        for (PayslipItem item : items) {
            Double configuredAmount = item.getAmount();
            if (configuredAmount == null || !Double.isFinite(configuredAmount) || configuredAmount < 0)
                throw new IllegalArgumentException("Số tiền khoản lương phải là số không âm hợp lệ");
            SalaryCalculationType calculationType = item.getCalculationType();
            SalaryItemType type = item.getType();
            if (calculationType == null || type == null)
                throw new IllegalArgumentException("Cách tính và loại khoản lương là bắt buộc");
            BigDecimal amount = BigDecimal.valueOf(configuredAmount);
            BigDecimal value;
            switch (calculationType) {
                case FIXED -> value = amount;
                case DAILY_MULTIPLIED -> throw new IllegalArgumentException(
                        "Cách tính theo đơn giá ngày đã ngừng dùng. Hãy cập nhật khoản lương trong hồ sơ nhân viên sang Cố định hoặc Theo công chuẩn");
                case BY_STANDARD_DAYS -> {
                    if (standardWorkDays <= 0)
                        throw new IllegalArgumentException("Công chuẩn phải lớn hơn 0 khi tính theo công chuẩn");
                    value = amount.multiply(workDays).divide(BigDecimal.valueOf(standardWorkDays), 0, RoundingMode.HALF_UP);
                }
                default -> throw new IllegalArgumentException("Cách tính khoản lương không hợp lệ");
            }
            // Settle Vietnamese dong in whole units, matching the amount displayed and encoded in payment QR.
            value = value.setScale(0, RoundingMode.HALF_UP);
            if (!Double.isFinite(value.doubleValue())) throw new IllegalArgumentException("Số tiền khoản lương vượt giới hạn");
            item.setPayslip(payslip);
            item.setCalculationType(calculationType);
            item.setType(type);
            item.setCalculatedValue(value.doubleValue());
            if (type == SalaryItemType.INCOME) income = income.add(value);
            else deduction = deduction.add(value);
        }
        if (!Double.isFinite(income.doubleValue()) || !Double.isFinite(deduction.doubleValue())
                || !Double.isFinite(workDays.doubleValue()) || !Double.isFinite(overtime.doubleValue()))
            throw new IllegalArgumentException("Tổng tiền phiếu lương vượt giới hạn");
        payslip.setTotalWorkDays(workDays.doubleValue());
        payslip.setTotalOtHours(overtime.doubleValue());
        payslip.setStandardWorkDays(standardWorkDays);
        payslip.setTotalIncome(income.doubleValue());
        payslip.setTotalDeduction(deduction.doubleValue());
        payslip.setNetSalary(income.subtract(deduction).doubleValue());
        // Keep the managed collection so refreshed profile snapshots remove obsolete rows.
        payslip.getItems().clear();
        payslip.getItems().addAll(items);
        return payslip;
    }
}
