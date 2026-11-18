package hrm.controller;

import hrm.dto.request.DepartmentDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.search.SearchDto;
import hrm.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/departments")
@CrossOrigin(origins = "*")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_VIEW')")
    public ResponseEntity<ApiResponse<Page<DepartmentDto>>> pagingDepartments(
            @RequestBody(required = false) SearchDto searchDto) {
        Page<DepartmentDto> result = departmentService.pagingDepartments(searchDto);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phòng ban thành công", result));
    }

    @GetMapping("/all")
    @PreAuthorize("@access.any(authentication, 'DEPARTMENT_VIEW', 'DEPARTMENT_MANAGE', 'POSITION_VIEW', 'POSITION_MANAGE', 'STAFF_VIEW', 'STAFF_CREATE', 'STAFF_UPDATE', 'STAFF_IMPORT', 'RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE', 'CANDIDATE_VIEW', 'CANDIDATE_MANAGE', 'ATTENDANCE_VIEW', 'LEAVE_VIEW')")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAllDepartments() {
        List<DepartmentDto> result = departmentService.getAllDepartments();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách phòng ban thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_VIEW')")
    public ResponseEntity<ApiResponse<DepartmentDto>> getById(@PathVariable UUID id) {
        DepartmentDto dto = departmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin phòng ban thành công", dto));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(@RequestBody DepartmentDto dto) {
        if (!departmentService.isValidCode(dto)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Mã phòng ban đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        DepartmentDto result = departmentService.saveDepartment(dto);
        return ResponseEntity.ok(ApiResponse.success("Tạo phòng ban thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(@PathVariable UUID id,
            @RequestBody DepartmentDto dto) {
        dto.setId(id);
        if (!departmentService.isValidCode(dto)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Mã phòng ban đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        DepartmentDto result = departmentService.saveDepartment(dto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phòng ban thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa phòng ban thành công", null));
    }

    @PostMapping("/delete-multiple")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteMultiple(@RequestBody List<UUID> ids) {
        departmentService.deleteMultiple(ids);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh sách phòng ban thành công", null));
    }

    @GetMapping("/generate-code")
    @PreAuthorize("@access.has(authentication, 'DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<String>> generateCode() {
        String code = departmentService.generateCode();
        return ResponseEntity.ok(ApiResponse.success("Tạo mã phòng ban thành công", code));
    }
}
