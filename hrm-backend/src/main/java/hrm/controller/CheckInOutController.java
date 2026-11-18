package hrm.controller;

import hrm.dto.request.CheckInOutRecordDto;
import hrm.dto.response.ApiResponse;
import hrm.security.SecurityUtils;
import hrm.service.CheckInOutService;
import hrm.service.AttendanceLocationPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/check-in-out")
@CrossOrigin(origins = "*")
public class CheckInOutController {

    @Autowired
    private CheckInOutService checkInOutService;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private AttendanceLocationPolicy locationPolicy;

    @GetMapping("/location-policy")
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_MANAGE', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<AttendanceLocationPolicy.Snapshot>> getLocationPolicy() {
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình định vị chấm công thành công", locationPolicy.snapshot()));
    }

    @PostMapping
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_MANAGE', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<CheckInOutRecordDto>> logCheckInOut(@RequestBody CheckInOutRecordDto dto) {
        securityUtils.requireStaffAccess(dto.getStaffId(), "ATTENDANCE_MANAGE", "ATTENDANCE_SELF");
        if (!securityUtils.hasPermission(securityUtils.getCurrentUser(), "ATTENDANCE_MANAGE")) {
            dto.setRecordTime(null);
        }
        var attributes = RequestContextHolder.getRequestAttributes();
        dto.setIpAddress(attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getRemoteAddr() : null);
        CheckInOutRecordDto result = checkInOutService.save(dto);
        return ResponseEntity.ok(ApiResponse.success("Ghi nhận lượt chấm công thành công", result));
    }

    @GetMapping("/raw-logs")
    @PreAuthorize("@access.any(authentication, 'ATTENDANCE_VIEW', 'ATTENDANCE_SELF')")
    public ResponseEntity<ApiResponse<List<CheckInOutRecordDto>>> getRawLogs(
            @RequestParam UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        securityUtils.requireStaffAccess(staffId, "ATTENDANCE_VIEW", "ATTENDANCE_SELF");
        List<CheckInOutRecordDto> result = checkInOutService.getRawLogs(staffId, date);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch sử quẹt thẻ thành công", result));
    }

}
