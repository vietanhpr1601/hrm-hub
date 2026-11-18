package hrm.controller;

import hrm.dto.request.TimesheetDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.search.TimesheetSearchRequest;
import hrm.enums.TimesheetStatus;
import hrm.service.TimesheetService;
import hrm.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/timesheets")
@CrossOrigin(origins = "*")
public class TimesheetController {
    @Autowired
    private hrm.service.BatchApprovalService batchApprovalService;

    @PutMapping("/approve-batch")
    @PreAuthorize("@access.has(authentication, 'ATTENDANCE_APPROVE')")
    public ApiResponse<hrm.service.BatchApprovalService.Result> approveBatch(
            @RequestBody hrm.service.BatchApprovalService.Request request) {
        return ApiResponse.success("Đã xử lý duyệt hàng loạt", batchApprovalService.approve(request, false));
    }

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'ATTENDANCE_VIEW')")
    public ResponseEntity<ApiResponse<Page<TimesheetDto>>> search(@RequestBody TimesheetSearchRequest request) {
        Page<TimesheetDto> result = timesheetService.search(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ngày công thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_VIEW', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<TimesheetDto>> getById(@PathVariable UUID id) {
        TimesheetDto result = timesheetService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        securityUtils.requireStaffAccess(result.getStaffId(), "ATTENDANCE_VIEW", "ATTENDANCE_SELF");
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ngày công chi tiết thành công", result));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("@access.has(authentication, 'ATTENDANCE_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @RequestParam TimesheetStatus status,
            @RequestParam(required = false) String note) {
        boolean success = timesheetService.approve(id, status, note);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái ngày công thành công", null));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Không tìm thấy ngày công để cập nhật", org.springframework.http.HttpStatus.BAD_REQUEST));
        }
    }

    @GetMapping("/staff-range")
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_VIEW', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<List<TimesheetDto>>> getByStaffAndRange(
            @RequestParam UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        securityUtils.requireStaffAccess(staffId, "ATTENDANCE_VIEW", "ATTENDANCE_SELF");
        List<TimesheetDto> result = timesheetService.getByStaffAndDateRange(staffId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử chấm công nhân viên thành công", result));
    }
}
