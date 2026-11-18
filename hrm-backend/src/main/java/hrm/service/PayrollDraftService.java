package hrm.service;

import hrm.enums.*;
import hrm.model.*;
import hrm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class PayrollDraftService {
    public record Candidate(UUID id, String code, String name, boolean eligible, String reason) {}
    public record Preview(UUID periodId, String periodName, List<Candidate> employees) {}
    public record Selection(List<UUID> staffIds) {}
    private final PeriodRepository periods;
    private final PayrollRepository payrolls;
    private final StaffRepository staffs;
    private final StaffSalaryItemRepository configurations;
    private final PayrollService service;
    public PayrollDraftService(PeriodRepository periods, PayrollRepository payrolls, StaffRepository staffs,
            StaffSalaryItemRepository configurations, PayrollService service) {
        this.periods = periods; this.payrolls = payrolls; this.staffs = staffs;
        this.configurations = configurations; this.service = service;
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID periodId) {
        Period period = periods.findById(periodId).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Kỳ lương không tồn tại"));
        requireEmpty(periodId);
        return preview(period);
    }

    @Transactional
    public Payroll create(UUID periodId, Selection selection) {
        Period period = periods.findActiveByIdForUpdate(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Kỳ lương không tồn tại"));
        requireEmpty(periodId);
        if (selection == null || selection.staffIds() == null || selection.staffIds().isEmpty()
                || selection.staffIds().stream().anyMatch(Objects::isNull)
                || new HashSet<>(selection.staffIds()).size() != selection.staffIds().size())
            throw new IllegalArgumentException("Chọn nhân viên từ danh sách xem trước");
        Set<UUID> eligible = new HashSet<>();
        preview(period).employees().stream().filter(Candidate::eligible).forEach(row -> eligible.add(row.id()));
        if (!eligible.containsAll(selection.staffIds()))
            throw new IllegalArgumentException("Cấu hình nhân viên đã thay đổi. Hãy xem trước lại danh sách.");
        String name = "Bảng lương " + period.getName();
        return service.createPayrollWithStaffs(periodId, name.substring(0, Math.min(255, name.length())), "AUTO_" + periodId,
                "Tự lập từ cấu hình lương nhân viên và công đã duyệt; chờ người quản lý duyệt.", selection.staffIds());
    }

    private Preview preview(Period period) {
        YearMonth month = YearMonth.of(period.getYear(), period.getMonth());
        LocalDate from = period.getFromDate() == null ? month.atDay(1) : period.getFromDate();
        LocalDate to = period.getToDate() == null ? month.atEndOfMonth() : period.getToDate();
        if (to.isBefore(from)) throw new IllegalArgumentException("Khoảng ngày kỳ lương không hợp lệ");
        List<Candidate> rows = new ArrayList<>();
        for (Staff staff : staffs.findActiveStaffs().stream().sorted(Comparator.comparing(row -> Objects.toString(row.getStaffCode(), ""))).toList()) {
            String reason = null;
            if (staff.getStartDate() != null && staff.getStartDate().isAfter(to)) reason = "Chưa bắt đầu làm việc trong kỳ";
            else if (staff.getResignationDate() != null && staff.getResignationDate().isBefore(from)) reason = "Đã nghỉ việc trước kỳ lương";
            var items = configurations.findByStaffId(staff.getId()).stream().filter(item -> !Boolean.TRUE.equals(item.getIsDeleted())
                    && item.getSalaryItem() != null && !Boolean.TRUE.equals(item.getSalaryItem().getIsDeleted())).toList();
            if (reason == null && items.isEmpty()) reason = "Chưa cấu hình lương / phụ cấp";
            Set<UUID> definitions = new HashSet<>();
            for (var item : items) {
                if (reason != null) break;
                var definition = item.getSalaryItem();
                if (!definitions.add(definition.getId()) || item.getAmount() == null || !Double.isFinite(item.getAmount()) || item.getAmount() < 0
                        || definition.getType() == null || definition.getCalculationType() == null
                        || definition.getCalculationType() == SalaryCalculationType.DAILY_MULTIPLIED)
                    reason = "Cấu hình khoản lương chưa hợp lệ; cập nhật hồ sơ trước";
                else if (definition.getCalculationType() == SalaryCalculationType.BY_STANDARD_DAYS
                        && period.getStandardWorkDays() != null && (!Double.isFinite(period.getStandardWorkDays()) || period.getStandardWorkDays() <= 0))
                    reason = "Công chuẩn kỳ lương phải lớn hơn 0";
            }
            rows.add(new Candidate(staff.getId(), staff.getStaffCode(), staff.getDisplayName(), reason == null,
                    reason == null ? "Sẵn sàng lấy khoản lương và công đã duyệt" : reason));
        }
        return new Preview(period.getId(), period.getName(), List.copyOf(rows));
    }
    private void requireEmpty(UUID periodId) {
        if (payrolls.findByPeriodId(periodId).stream().anyMatch(row -> !Boolean.TRUE.equals(row.getIsDeleted())))
            throw new IllegalStateException("Kỳ này đã có bảng lương. Mở bảng hiện có để cập nhật hoặc xóa bản nháp trước khi tự lập lại.");
    }
}
