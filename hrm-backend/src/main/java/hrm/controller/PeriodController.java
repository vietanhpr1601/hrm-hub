package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.model.Period;
import hrm.service.PeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import hrm.dto.request.PeriodDto;
import hrm.dto.search.PeriodSearchRequest;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/periods")
@CrossOrigin(origins = "*")
public class PeriodController {
    @Autowired private hrm.service.PeriodCreationService creationService;

    @Autowired
    private PeriodService periodService;

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'PERIOD_MANAGE') and @access.has(authentication, 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<hrm.service.PeriodCreationService.Result>> createPeriod(@RequestBody PeriodDto dto) {
        var result = creationService.create(dto);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo kỳ lương và tự lập bảng lương nháp", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'PERIOD_MANAGE')")
    public ResponseEntity<ApiResponse<Period>> updatePeriod(@PathVariable UUID id, @RequestBody PeriodDto dto) {
        Period result = periodService.updatePeriod(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật kỳ tính lương thành công", result));
    }

    @GetMapping
    @PreAuthorize("@access.any(authentication, 'PERIOD_VIEW', 'PERIOD_MANAGE', 'PAYROLL_VIEW', 'PAYROLL_MANAGE', 'PAYROLL_SELF', 'ATTENDANCE_VIEW', 'ATTENDANCE_APPROVE')")
    public ResponseEntity<ApiResponse<List<Period>>> getAllPeriods() {
        List<Period> result = periodService.getAllPeriods();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kỳ lương thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'PERIOD_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(@PathVariable UUID id) {
        periodService.deletePeriod(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa kỳ lương thành công", null));
    }

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'PERIOD_VIEW')")
    public ResponseEntity<ApiResponse<Page<Period>>> getPeriods(@RequestBody PeriodSearchRequest request) {
        Page<Period> result = periodService.getPeriods(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kỳ lương thành công", result));
    }
}
