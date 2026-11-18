package hrm.service.impl;

import hrm.dto.request.PayslipDraftRequest;
import hrm.dto.request.PayslipPaymentRequest;
import hrm.enums.PaidStatus;
import hrm.enums.PaymentMethod;
import hrm.enums.PayrollStatus;
import hrm.enums.SalaryCalculationType;
import hrm.model.*;
import hrm.repository.*;
import hrm.service.PayrollService;
import hrm.service.PayslipService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayrollServiceImpl implements PayrollService {
    @Autowired private PeriodRepository periodRepository;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private StaffSalaryItemRepository staffSalaryItemRepository;
    @Autowired private PayslipService payslipService;
    @Autowired private EntityManager entityManager;
    @Autowired private hrm.service.PayrollEmailService payrollEmails;

    @Override
    public Payroll updateDetails(UUID id, hrm.dto.request.PayrollDetailsRequest request) {
        Payroll payroll = lockedPayroll(id);
        requireDraft(payroll);
        activePayslips(id).forEach(this::requireUnpaid);
        if (request == null || request.name() == null || request.name().isBlank())
            throw new IllegalArgumentException("Tên bảng lương là bắt buộc");
        String name = request.name().trim();
        String code = request.code() == null ? "" : request.code().trim().toUpperCase(Locale.ROOT);
        if (name.length() > 255 || code.length() > 255 || request.description() != null && request.description().length() > 255)
            throw new IllegalArgumentException("Tên, mã và mô tả không được quá 255 ký tự");
        if (!code.isEmpty() && payrollRepository.findActiveByCode(code).filter(other -> !id.equals(other.getId())).isPresent())
            throw new IllegalArgumentException("Mã bảng lương đã tồn tại");
        payroll.setName(name);
        if (!code.isEmpty()) payroll.setCode(code);
        payroll.setDescription(request.description());
        return payrollRepository.saveAndFlush(payroll);
    }

    @Override
    public Payroll createPayroll(UUID periodId, String name, String code, String description) {
        Period period = periodRepository.findActiveByIdForUpdate(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Kỳ lương không tồn tại hoặc đã xóa"));
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Tên bảng lương là bắt buộc");
        String finalCode = code == null || code.trim().isEmpty() ? generatePayrollCode(name) : code.toUpperCase().trim();
        if (payrollRepository.findActiveByCode(finalCode).isPresent())
            throw new IllegalArgumentException("Mã bảng lương '" + finalCode + "' đã tồn tại");
        Payroll payroll = new Payroll();
        payroll.setName(name.trim());
        payroll.setPeriod(period);
        payroll.setCode(finalCode);
        payroll.setDescription(description);
        payroll.setStatus(PayrollStatus.DRAFT);
        return payrollRepository.save(payroll);
    }

    @Override
    public Payroll createPayrollWithStaffs(UUID periodId, String name, String code, String description, List<UUID> staffIds) {
        validateStaffSelection(staffIds);
        // One transaction includes both creation and enrollment, so failed salary
        // inputs cannot leave an empty payroll behind after the request fails.
        Payroll payroll = createPayroll(periodId, name, code, description);
        enrollStaffs(payroll.getId(), staffIds);
        return payroll;
    }

    private String generatePayrollCode(String name) {
        String clean = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "_").toUpperCase();
        if (clean.isEmpty()) clean = "PAYROLL";
        if (clean.length() > 50) clean = clean.substring(0, 50);
        return clean + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public List<Payroll> getPayrollsByPeriod(UUID periodId) {
        return payrollRepository.findByPeriodId(periodId).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted())).collect(Collectors.toList());
    }

    @Override
    public List<Payroll> getAllPayrolls() {
        return payrollRepository.findAll().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted())).collect(Collectors.toList());
    }

    @Override
    public List<Payslip> enrollStaffs(UUID payrollId, List<UUID> staffIds) {
        Payroll payroll = lockedPayroll(payrollId);
        requireDraft(payroll);
        validateStaffSelection(staffIds);
        Set<UUID> enrolled = activePayslips(payrollId).stream().map(p -> p.getStaff().getId()).collect(Collectors.toSet());
        PeriodInputs inputs = periodInputs(payroll.getPeriod());
        List<Payslip> added = new ArrayList<>();
        for (UUID staffId : staffIds) {
            if (enrolled.contains(staffId)) throw new IllegalArgumentException("Nhân viên đã có trong bảng lương");
            Staff staff = activeStaff(staffId);
            Payslip slip = payslipService.calculateStaffPayslip(staff, payroll, inputs.start(), inputs.end(),
                    inputs.standardDays(), staffSalaryItemRepository.findByStaffId(staffId));
            added.add(slip);
        }
        return payslipRepository.saveAll(added);
    }

    @Override
    public List<Payslip> calculatePayroll(UUID payrollId) {
        Payroll payroll = lockedPayroll(payrollId);
        requireDraft(payroll);
        List<Payslip> slips = activePayslips(payrollId);
        if (slips.isEmpty()) throw new IllegalArgumentException("Hãy thêm nhân viên trước khi tính bảng lương");
        PeriodInputs inputs = periodInputs(payroll.getPeriod());
        for (Payslip slip : slips) {
            refreshFromStaffConfiguration(slip, inputs);
        }
        return payslipRepository.saveAll(slips);
    }

    @Override
    public Payslip calculatePayslip(UUID id) {
        Payslip slip = lockedPayslip(id);
        requireDraft(slip.getPayroll());
        refreshFromStaffConfiguration(slip, periodInputs(slip.getPayroll().getPeriod()));
        return payslipRepository.saveAndFlush(slip);
    }

    @Override
    public Payslip updateDraftPayslip(UUID id, PayslipDraftRequest request) {
        // Retain an explicit error for old clients rather than allowing an override
        // that would disagree with the employee's authoritative salary configuration.
        throw new IllegalStateException("Không chỉnh khoản lương trên phiếu dự thảo. Hãy sửa và lưu tại mục Khoản lương trong hồ sơ nhân viên, rồi tính lại bảng lương");
    }

    @Override
    public void removePayslip(UUID id) {
        Payslip slip = lockedPayslip(id);
        requireDraft(slip.getPayroll());
        requireUnpaid(slip);
        slip.setIsDeleted(true);
        payslipRepository.saveAndFlush(slip);
    }

    @Override
    public List<Payslip> getPayrollDetails(UUID payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Bảng lương không tồn tại hoặc đã xóa"));
        return activePayslips(payroll.getId());
    }

    @Override
    public Payroll confirmPayroll(UUID payrollId) {
        Payroll payroll = lockedPayroll(payrollId);
        if (payroll.getStatus() == PayrollStatus.CONFIRMED) return payroll;
        requireDraft(payroll);
        List<Payslip> slips = activePayslips(payrollId);
        if (slips.isEmpty()) throw new IllegalArgumentException("Không thể xác nhận bảng lương chưa có nhân viên");
        PeriodInputs inputs = periodInputs(payroll.getPeriod());
        for (Payslip slip : slips) {
            // Approval snapshots the current saved employee configuration and approved
            // attendance, including configuration added after this payroll was created.
            refreshFromStaffConfiguration(slip, inputs);
            if (slip.getStandardWorkDays() == null)
                throw new IllegalArgumentException("Phiếu lương cũ chưa có dữ liệu tính được chốt. Hãy cập nhật công và tính lại trước khi xác nhận");
            for (PayslipItem item : slip.getItems()) {
                if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
                requireCurrentFormula(item.getCalculationType());
                if (item.getCalculationType() == null || item.getType() == null || item.getAmount() == null
                        || !Double.isFinite(item.getAmount()) || item.getAmount() < 0
                        || item.getCalculatedValue() == null || !Double.isFinite(item.getCalculatedValue())
                        || item.getCalculatedValue() < 0)
                    throw new IllegalArgumentException("Khoản lương chưa hợp lệ. Hãy cập nhật công và tính lại trước khi xác nhận");
                // Freeze the calculation metadata used for this approved snapshot.
                item.setCalculationType(item.getCalculationType());
                item.setType(item.getType());
            }
            if (slip.getNetSalary() == null || !Double.isFinite(slip.getNetSalary()) || slip.getNetSalary() < 0)
                throw new IllegalArgumentException("Thực lĩnh phải là số không âm trước khi xác nhận bảng lương");
        }
        boolean alreadyConfirmed = payrollRepository.findByPeriodId(payroll.getPeriod().getId()).stream()
                .anyMatch(other -> !payrollId.equals(other.getId()) && !Boolean.TRUE.equals(other.getIsDeleted())
                        && other.getStatus() == PayrollStatus.CONFIRMED);
        if (alreadyConfirmed) throw new IllegalArgumentException("Kỳ lương đã có bảng lương được xác nhận");
        payslipRepository.saveAll(slips);
        payroll.setStatus(PayrollStatus.CONFIRMED);
        Payroll confirmed = payrollRepository.saveAndFlush(payroll);
        slips.forEach(payrollEmails::enqueueConfirmation);
        return confirmed;
    }

    @Override
    public void deletePayroll(UUID payrollId) {
        Payroll payroll = lockedPayroll(payrollId);
        requireDraft(payroll);
        List<Payslip> slips = activePayslips(payrollId);
        slips.forEach(this::requireUnpaid);
        payroll.setIsDeleted(true);
        slips.forEach(slip -> slip.setIsDeleted(true));
        payslipRepository.saveAll(slips);
        payrollRepository.save(payroll);
    }

    @Override
    public Payslip recordPayment(UUID id, PayslipPaymentRequest request) {
        Payslip slip = lockedPayslip(id);
        if (slip.getPayroll().getStatus() != PayrollStatus.CONFIRMED)
            throw new IllegalStateException("Chỉ được thanh toán bảng lương đã xác nhận");
        requireUnpaid(slip);
        if (slip.getNetSalary() == null || !Double.isFinite(slip.getNetSalary()) || slip.getNetSalary() <= 0)
            throw new IllegalArgumentException("Chỉ thanh toán phiếu lương có thực lĩnh lớn hơn 0");
        if (request == null || request.getPaidDate() == null || request.getPaymentMethod() == null)
            throw new IllegalArgumentException("Ngày thanh toán và phương thức là bắt buộc");
        validateNote(request.getNote());
        if (request.getPaidDate().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new IllegalArgumentException("Ngày thanh toán không được ở tương lai");
        String reference = request.getPaymentReference() == null ? null : request.getPaymentReference().trim();
        if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && (reference == null || reference.isEmpty()))
            throw new IllegalArgumentException("Chuyển khoản phải có mã giao dịch hoặc chứng từ");
        if (reference != null && reference.length() > 255)
            throw new IllegalArgumentException("Mã giao dịch không được quá 255 ký tự");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String paidBy = authentication != null && authentication.isAuthenticated() ? authentication.getName() : "admin";
        if (paidBy.length() > 100) throw new IllegalArgumentException("Tên người thanh toán vượt giới hạn");
        slip.setPaidDate(request.getPaidDate());
        slip.setPaidAmount(slip.getNetSalary());
        slip.setPaymentMethod(request.getPaymentMethod());
        slip.setPaymentReference(reference == null || reference.isEmpty() ? null : reference);
        slip.setPaidBy(paidBy);
        if (request.getNote() != null) slip.setNote(request.getNote());
        slip.setPaidStatus(PaidStatus.PAID);
        Payslip saved = payslipRepository.saveAndFlush(slip);
        payrollEmails.enqueuePayment(saved);
        return saved;
    }

    @Override
    public Payslip updatePayslip(UUID id, PaidStatus paidStatus, String note) {
        Payslip slip = lockedPayslip(id);
        if (slip.getPayroll().getStatus() != PayrollStatus.CONFIRMED)
            throw new IllegalStateException("Hãy sửa khoản lương trong hồ sơ nhân viên và tính lại bảng lương trước khi xác nhận");
        if (paidStatus == null || paidStatus != slip.getPaidStatus())
            throw new IllegalStateException("Hãy dùng chức năng ghi nhận thanh toán; không thể đảo trạng thái đã thanh toán");
        validateNote(note);
        slip.setNote(note);
        return payslipRepository.saveAndFlush(slip);
    }

    @Override
    public Payslip getMyPayslip(UUID periodId, User currentUser) {
        if (currentUser == null || currentUser.getStaff() == null)
            throw new IllegalArgumentException("Người dùng hiện tại chưa liên kết với nhân viên nào");
        List<Payslip> slips = payslipRepository.findActiveByStaffAndPeriodAndStatus(
                currentUser.getStaff().getId(), periodId, PayrollStatus.CONFIRMED);
        if (slips.isEmpty()) throw new IllegalArgumentException("Không tìm thấy phiếu lương đã xác nhận cho kỳ lương này");
        if (slips.size() > 1) throw new IllegalArgumentException("Kỳ lương có nhiều phiếu lương đã xác nhận. Vui lòng liên hệ nhân sự để kiểm tra");
        return slips.get(0);
    }

    private Payroll lockedPayroll(UUID id) {
        Payroll initial = payrollRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bảng lương không tồn tại"));
        if (initial.getPeriod() == null) throw new IllegalArgumentException("Kỳ lương không tồn tại");
        // A single lock order also serializes period edit/delete and concurrent settlement.
        Period period = periodRepository.findActiveByIdForUpdate(initial.getPeriod().getId())
                .orElseThrow(() -> new IllegalArgumentException("Kỳ lương không tồn tại hoặc đã xóa"));
        entityManager.refresh(period);
        Payroll payroll = payrollRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Bảng lương không tồn tại hoặc đã xóa"));
        entityManager.refresh(payroll);
        return payroll;
    }

    private Payslip lockedPayslip(UUID id) {
        Payslip slip = payslipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Phiếu lương không tồn tại"));
        lockedPayroll(slip.getPayroll().getId());
        entityManager.refresh(slip);
        if (Boolean.TRUE.equals(slip.getIsDeleted())) throw new IllegalArgumentException("Phiếu lương đã xóa");
        return slip;
    }

    private Staff activeStaff(UUID id) {
        return staffRepository.findById(id).filter(staff -> !Boolean.TRUE.equals(staff.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc đã xóa"));
    }

    private List<Payslip> activePayslips(UUID payrollId) {
        return payslipRepository.findByPayrollId(payrollId).stream()
                .filter(slip -> !Boolean.TRUE.equals(slip.getIsDeleted())).collect(Collectors.toList());
    }

    private void refreshFromStaffConfiguration(Payslip slip, PeriodInputs inputs) {
        requireUnpaid(slip);
        Staff staff = activeStaff(slip.getStaff().getId());
        payslipService.calculateFromStaffConfiguration(slip, inputs.start(), inputs.end(),
                inputs.standardDays(), staffSalaryItemRepository.findByStaffId(staff.getId()));
    }

    private void requireDraft(Payroll payroll) {
        if (payroll.getStatus() != PayrollStatus.DRAFT)
            throw new IllegalStateException("Bảng lương đã xác nhận, không thể thay đổi dữ liệu");
    }

    private void validateStaffSelection(List<UUID> staffIds) {
        if (staffIds == null || staffIds.isEmpty() || staffIds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Vui lòng chọn nhân viên cần thêm vào bảng lương");
        if (new HashSet<>(staffIds).size() != staffIds.size())
            throw new IllegalArgumentException("Danh sách nhân viên bị trùng");
    }

    private void requireUnpaid(Payslip slip) {
        if (slip.getPaidStatus() == PaidStatus.PAID)
            throw new IllegalStateException("Phiếu lương đã thanh toán, không thể thay đổi hoặc thanh toán lại");
    }

    private void validateNote(String note) {
        if (note != null && note.length() > 255)
            throw new IllegalArgumentException("Ghi chú không được quá 255 ký tự");
    }

    private void requireCurrentFormula(SalaryCalculationType formula) {
        if (formula == SalaryCalculationType.DAILY_MULTIPLIED)
            throw new IllegalArgumentException("Cách tính theo đơn giá ngày đã ngừng dùng. Hãy sửa khoản lương trong hồ sơ nhân viên và chọn Cố định hoặc Theo công chuẩn");
        if (formula == null)
            throw new IllegalArgumentException("Hãy chọn Cố định hoặc Theo công thực tế / công chuẩn");
    }

    private PeriodInputs periodInputs(Period period) {
        LocalDate start = period.getFromDate();
        LocalDate end = period.getToDate();
        if ((start == null) != (end == null)) throw new IllegalArgumentException("Kỳ lương phải có đủ ngày bắt đầu và kết thúc");
        if (start == null) {
            if (period.getMonth() == null || period.getYear() == null)
                throw new IllegalArgumentException("Tháng và năm kỳ lương là bắt buộc");
            try {
                start = LocalDate.of(period.getYear(), period.getMonth(), 1);
            } catch (java.time.DateTimeException error) {
                throw new IllegalArgumentException("Tháng hoặc năm kỳ lương không hợp lệ");
            }
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        if (end.isBefore(start)) throw new IllegalArgumentException("Ngày kết thúc kỳ lương phải từ ngày bắt đầu");
        double standard = period.getStandardWorkDays() != null ? period.getStandardWorkDays() : 26.0;
        return new PeriodInputs(start, end, standard);
    }

    private record PeriodInputs(LocalDate start, LocalDate end, double standardDays) {}
}
