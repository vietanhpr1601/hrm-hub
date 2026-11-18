package hrm.controller;

import hrm.dto.request.LeaveRequestDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.response.StaffAnnualLeaveBalanceDto;
import hrm.dto.search.LeaveRequestSearchRequest;
import hrm.model.User;
import hrm.security.SecurityUtils;
import hrm.service.LeaveRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import java.time.LocalDate;


@RestController
@RequestMapping("/api/leave-requests")
@CrossOrigin(origins = "*")
public class LeaveRequestController {
    @Autowired
    private hrm.service.BatchApprovalService batchApprovalService;

    @PutMapping("/approve-batch")
    @PreAuthorize("@access.has(authentication, 'LEAVE_APPROVE')")
    public ApiResponse<hrm.service.BatchApprovalService.Result> approveBatch(
            @RequestBody hrm.service.BatchApprovalService.Request request) {
        return ApiResponse.success("Đã xử lý duyệt hàng loạt", batchApprovalService.approve(request, true));
    }

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/related-attendance")
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_VIEW', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> relatedAttendance(
            @RequestParam UUID staffId, @RequestParam LocalDate date) {
        securityUtils.requireStaffAccess(staffId, "ATTENDANCE_VIEW", "ATTENDANCE_SELF");
        return ResponseEntity.ok(ApiResponse.success("Lấy đơn nghỉ và ngày làm bù liên quan thành công",
                leaveRequestService.getRelatedToAttendance(staffId, date)));
    }

    @PostMapping("/paging")
    @PreAuthorize("@access.any(authentication, 'LEAVE_VIEW', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<Page<LeaveRequestDto>>> search(@RequestBody LeaveRequestSearchRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED));
        }

        // A self-service grant never exposes another employee's leave requests.
        boolean isEmployeeOnly = !securityUtils.hasPermission(currentUser, "LEAVE_VIEW");

        if (isEmployeeOnly) {
            if (currentUser.getStaff() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Tài khoản chưa liên kết với nhân sự", HttpStatus.BAD_REQUEST));
            }
            request.setStaffId(currentUser.getStaff().getId());
        }

        Page<LeaveRequestDto> result = leaveRequestService.search(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn nghỉ phép thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.any(authentication, 'LEAVE_VIEW', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getById(@PathVariable UUID id) {
        LeaveRequestDto result = leaveRequestService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        securityUtils.requireStaffAccess(result.getRequestStaffId(), "LEAVE_VIEW", "LEAVE_SELF");

        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn nghỉ phép thành công", result));
    }

    @PostMapping
    @PreAuthorize("@access.any(authentication, 'LEAVE_MANAGE', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> create(@RequestBody LeaveRequestDto dto) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED));
        }

        boolean isEmployeeOnly = !securityUtils.hasPermission(currentUser, "LEAVE_MANAGE");

        if (isEmployeeOnly) {
            if (currentUser.getStaff() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Tài khoản chưa liên kết với nhân sự", HttpStatus.BAD_REQUEST));
            }
            // Nhân viên thường chỉ được tạo đơn cho chính mình
            dto.setRequestStaffId(currentUser.getStaff().getId());
        }

        LeaveRequestDto result = leaveRequestService.create(dto);
        return ResponseEntity.ok(ApiResponse.success("Tạo đơn nghỉ phép thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.any(authentication, 'LEAVE_MANAGE', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> update(@PathVariable UUID id, @RequestBody LeaveRequestDto dto) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED));
        }

        LeaveRequestDto existing = leaveRequestService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        securityUtils.requireStaffAccess(existing.getRequestStaffId(), "LEAVE_MANAGE", "LEAVE_SELF");
        boolean isEmployeeOnly = !securityUtils.hasPermission(currentUser, "LEAVE_MANAGE");

        if (isEmployeeOnly) {
            // Không cho phép nhân viên thường tự ý thay đổi requestStaffId
            dto.setRequestStaffId(securityUtils.requireCurrentStaffId(currentUser));
        }

        LeaveRequestDto result = leaveRequestService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật đơn nghỉ phép thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.any(authentication, 'LEAVE_MANAGE', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED));
        }

        LeaveRequestDto existing = leaveRequestService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        securityUtils.requireStaffAccess(existing.getRequestStaffId(), "LEAVE_MANAGE", "LEAVE_SELF");

        leaveRequestService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa/Hủy đơn nghỉ phép thành công", null));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("@access.has(authentication, 'LEAVE_APPROVE')")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> approve(
            @PathVariable UUID id,
            @RequestParam(required = false) String rejectReason) {
        LeaveRequestDto result = leaveRequestService.approve(id, rejectReason);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt đơn nghỉ phép thành công", result));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("@access.has(authentication, 'LEAVE_APPROVE')")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> reject(
            @PathVariable UUID id,
            @RequestParam(required = false) String rejectReason) {
        LeaveRequestDto result = leaveRequestService.reject(id, rejectReason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối đơn nghỉ phép thành công", result));
    }

    @GetMapping("/balance/{staffId}/{year}")
    @PreAuthorize("@access.any(authentication, 'LEAVE_VIEW', 'LEAVE_SELF')")
    public ResponseEntity<ApiResponse<StaffAnnualLeaveBalanceDto>> getLeaveBalance(
            @PathVariable UUID staffId,
            @PathVariable int year) {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED));
        }

        securityUtils.requireStaffAccess(staffId, "LEAVE_VIEW", "LEAVE_SELF");

        StaffAnnualLeaveBalanceDto result = leaveRequestService.getLeaveBalance(staffId, year);
        return ResponseEntity.ok(ApiResponse.success("Lấy số dư phép thành công", result));
    }

    @PostMapping("/balance/paging")
    @PreAuthorize("@access.has(authentication, 'LEAVE_VIEW')")
    public ResponseEntity<ApiResponse<Page<StaffAnnualLeaveBalanceDto>>> getLeaveBalances(
            @RequestBody(required = false) hrm.dto.search.SearchDto searchDto,
            @RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
        Page<StaffAnnualLeaveBalanceDto> result = leaveRequestService.getLeaveBalances(searchDto, targetYear);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách số dư phép năm thành công", result));
    }
}
