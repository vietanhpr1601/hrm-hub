package hrm.service;

import hrm.dto.request.PeriodDto;
import hrm.model.Payroll;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class PeriodCreationService {
    public static class Result extends PeriodDto {
        private final UUID payrollId;
        private final int employeeCount;
        private final List<PayrollDraftService.Candidate> skippedEmployees;
        public Result(hrm.model.Period period, Payroll payroll, int count, List<PayrollDraftService.Candidate> skipped) {
            super(period); this.payrollId = payroll.getId(); this.employeeCount = count; this.skippedEmployees = skipped;
        }
        public UUID getPayrollId() { return payrollId; }
        public int getEmployeeCount() { return employeeCount; }
        public List<PayrollDraftService.Candidate> getSkippedEmployees() { return skippedEmployees; }
    }
    private final PeriodService periods;
    private final PayrollDraftService drafts;
    private final PayrollService payrolls;
    public PeriodCreationService(PeriodService periods, PayrollDraftService drafts, PayrollService payrolls) {
        this.periods = periods; this.drafts = drafts; this.payrolls = payrolls;
    }
    @Transactional
    public Result create(PeriodDto request) {
        if (request == null || request.getMonth() == null || request.getYear() == null
                || request.getMonth() < 1 || request.getMonth() > 12 || request.getYear() < 1900 || request.getYear() > 9999
                || request.getName() == null || request.getName().isBlank())
            throw new IllegalArgumentException("Tên, tháng và năm kỳ lương phải hợp lệ");
        if ((request.getFromDate() == null) != (request.getToDate() == null)
                || request.getFromDate() != null && request.getToDate().isBefore(request.getFromDate()))
            throw new IllegalArgumentException("Khoảng ngày kỳ lương phải đầy đủ và đúng thứ tự");
        var period = periods.createPeriod(request);
        var preview = drafts.preview(period.getId());
        List<UUID> selected = preview.employees().stream().filter(PayrollDraftService.Candidate::eligible)
                .map(PayrollDraftService.Candidate::id).toList();
        Payroll payroll;
        if (selected.isEmpty()) {
            String name = "Bảng lương " + period.getName();
            payroll = payrolls.createPayroll(period.getId(), name.substring(0, Math.min(255, name.length())), "AUTO_" + period.getId(),
                    "Bản nháp tự tạo cùng kỳ; chưa có nhân viên đủ cấu hình. Bổ sung hồ sơ rồi thêm nhân viên và tính lại.");
        } else payroll = drafts.create(period.getId(), new PayrollDraftService.Selection(selected));
        return new Result(period, payroll, selected.size(), preview.employees().stream().filter(row -> !row.eligible()).toList());
    }
}
