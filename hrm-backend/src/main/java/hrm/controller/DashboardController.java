package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.dto.response.DashboardSummaryResponse;
import hrm.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("@access.has(authentication, 'DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        DashboardSummaryResponse result = dashboardService.getSummary();
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu tổng quan dashboard thành công", result));
    }
}
