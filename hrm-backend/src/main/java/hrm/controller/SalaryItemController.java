package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.model.SalaryItem;
import hrm.service.SalaryItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/salary-items")
@CrossOrigin(origins = "*")
public class SalaryItemController {

    @Autowired
    private SalaryItemService salaryItemService;

    @GetMapping
    @PreAuthorize("@access.any(authentication, 'SALARY_CONFIG_VIEW', 'SALARY_CONFIG_MANAGE', 'PAYROLL_MANAGE')")
    public ResponseEntity<ApiResponse<List<SalaryItem>>> getAllSalaryItems() {
        List<SalaryItem> result = salaryItemService.getAllSalaryItems();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khoản lương thành công", result));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'SALARY_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<SalaryItem>> saveSalaryItem(@RequestBody SalaryItem item) {
        SalaryItem result = salaryItemService.saveSalaryItem(item);
        return ResponseEntity.ok(ApiResponse.success("Lưu khoản lương thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'SALARY_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteSalaryItem(@PathVariable UUID id) {
        salaryItemService.deleteSalaryItem(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa khoản lương thành công", null));
    }
}
