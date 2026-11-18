package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.dto.request.PayrollStaffRequest;
import hrm.dto.request.PayslipDraftRequest;
import hrm.dto.request.PayslipPaymentRequest;
import hrm.model.Period;
import hrm.model.Payroll;
import hrm.model.Payslip;
import hrm.model.SalaryItem;
import hrm.model.StaffSalaryItem;
import hrm.security.SecurityUtils;
import hrm.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;


@RestController
@RequestMapping("/api/payrolls")
@CrossOrigin(origins = "*")
public class PayrollController {
    @Autowired private hrm.service.PayrollEmailService payrollEmails;
    @Autowired private hrm.service.PayrollDraftService payrollDrafts;

    @GetMapping("/auto-draft/{periodId}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ApiResponse<hrm.service.PayrollDraftService.Preview> previewAutoDraft(@PathVariable UUID periodId) {
        return ApiResponse.success("Danh sách tự lập bảng nháp", payrollDrafts.preview(periodId));
    }

    @PostMapping("/auto-draft/{periodId}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ApiResponse<Payroll> createAutoDraft(@PathVariable UUID periodId, @RequestBody hrm.service.PayrollDraftService.Selection selection) {
        return ApiResponse.success("Đã tự lập bảng lương chờ duyệt", payrollDrafts.create(periodId, selection));
    }

    @PutMapping("/{id}/details")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ApiResponse<Payroll> updateDetails(@PathVariable UUID id, @RequestBody hrm.dto.request.PayrollDetailsRequest request) {
        return ApiResponse.success("Đã cập nhật thông tin bảng lương", payrollService.updateDetails(id, request));
    }

    @GetMapping("/payslip/{id}/email")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_VIEW')")
    public ApiResponse<hrm.service.PayrollEmailService.Status> emailStatus(@PathVariable UUID id) {
        return ApiResponse.success("Trạng thái email lương", payrollEmails.status(id));
    }

    @GetMapping("/payslip/{id}/emails")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_VIEW')")
    public ApiResponse<List<hrm.service.PayrollEmailService.Status>> emailStatuses(@PathVariable UUID id) {
        return ApiResponse.success("Trạng thái email duyệt lương và thanh toán", payrollEmails.statuses(id));
    }

    @PostMapping("/payslip/{id}/email/retry")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_PAY')")
    public ApiResponse<hrm.service.PayrollEmailService.Status> retryEmail(@PathVariable UUID id,
            @RequestParam(defaultValue = "PAYMENT") hrm.model.PayrollEmail.EventType eventType) {
        return ApiResponse.success("Đã đưa email vào hàng chờ", payrollEmails.retry(id, eventType));
    }

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<Payroll>> createPayroll(
            @RequestParam UUID periodId,
            @RequestParam String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String description,
            @RequestBody(required = false) PayrollStaffRequest request) {
        Payroll result = request == null
                ? payrollService.createPayroll(periodId, name, code, description)
                : payrollService.createPayrollWithStaffs(periodId, name, code, description, request.getStaffIds());
        return ResponseEntity.ok(ApiResponse.success("Tạo bảng lương mới thành công", result));
    }

    @GetMapping
    @PreAuthorize("@access.has(authentication, 'PAYROLL_VIEW')")
    public ResponseEntity<ApiResponse<List<Payroll>>> getAllPayrolls() {
        List<Payroll> result = payrollService.getAllPayrolls();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tất cả bảng lương thành công", result));
    }

    @GetMapping("/by-period/{periodId}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_VIEW')")
    public ResponseEntity<ApiResponse<List<Payroll>>> getPayrollsByPeriod(@PathVariable UUID periodId) {
        List<Payroll> result = payrollService.getPayrollsByPeriod(periodId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bảng lương thành công", result));
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<List<Payslip>>> calculatePayroll(@PathVariable UUID id) {
        List<Payslip> result = payrollService.calculatePayroll(id);
        return ResponseEntity.ok(ApiResponse.success("Tính lương thành công", result));
    }

    @PostMapping("/{id}/staffs")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<List<Payslip>>> enrollStaffs(
            @PathVariable UUID id, @RequestBody PayrollStaffRequest request) {
        List<Payslip> result = payrollService.enrollStaffs(id, request.getStaffIds());
        return ResponseEntity.ok(ApiResponse.success("Thêm nhân viên vào bảng lương thành công", result));
    }

    @PutMapping("/payslip/{id}/draft")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<Payslip>> updateDraftPayslip(
            @PathVariable UUID id, @RequestBody PayslipDraftRequest request) {
        Payslip result = payrollService.updateDraftPayslip(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật khoản lương nháp thành công", result));
    }

    @PostMapping("/payslip/{id}/calculate")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<Payslip>> calculatePayslip(@PathVariable UUID id) {
        Payslip result = payrollService.calculatePayslip(id);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật lương từ hồ sơ nhân viên thành công", result));
    }

    @DeleteMapping("/payslip/{id}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removePayslip(@PathVariable UUID id) {
        payrollService.removePayslip(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhân viên khỏi bảng lương nháp thành công", null));
    }

    @PostMapping("/payslip/{id}/payment")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_PAY')")
    public ResponseEntity<ApiResponse<Payslip>> recordPayment(
            @PathVariable UUID id, @RequestBody PayslipPaymentRequest request) {
        Payslip result = payrollService.recordPayment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Ghi nhận thanh toán lương thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_VIEW')")
    public ResponseEntity<ApiResponse<List<Payslip>>> getPayrollDetails(@PathVariable UUID id) {
        List<Payslip> result = payrollService.getPayrollDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bảng lương thành công", result));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_CONFIRM')")
    public ResponseEntity<ApiResponse<Payroll>> confirmPayroll(@PathVariable UUID id) {
        Payroll result = payrollService.confirmPayroll(id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận bảng lương thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deletePayroll(@PathVariable UUID id) {
        payrollService.deletePayroll(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa bảng lương thành công", null));
    }

    @GetMapping("/my-payslip")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_SELF')")
    public ResponseEntity<ApiResponse<Payslip>> getMyPayslip(@RequestParam UUID periodId) {
        hrm.model.User currentUser = securityUtils.getCurrentUser();
        Payslip result = payrollService.getMyPayslip(periodId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Lấy phiếu lương của tôi thành công", result));
    }

    @PutMapping("/payslip/{id}")
    @PreAuthorize("@access.has(authentication, 'PAYROLL_PAY')")
    public ResponseEntity<ApiResponse<Payslip>> updatePayslip(
            @PathVariable UUID id,
            @RequestParam hrm.enums.PaidStatus paidStatus,
            @RequestParam(required = false) String note) {
        Payslip result = payrollService.updatePayslip(id, paidStatus, note);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phiếu lương thành công", result));
    }
}
