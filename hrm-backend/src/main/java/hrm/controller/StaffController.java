package hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import hrm.dto.request.StaffDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.response.StaffImportResult;
import hrm.dto.response.StaffProfileDto;
import hrm.dto.search.SearchDto;
import hrm.exception.ResourceNotFoundException;
import hrm.service.StaffService;
import hrm.service.StaffExcelExportService;
import hrm.service.StaffExcelImportService;
import hrm.service.StaffExcelConfirmationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;
import java.io.IOException;

@RestController
@RequestMapping("/api/staffs")
@CrossOrigin(origins = "*")
public class StaffController {

    @Autowired
    private StaffService staffService;

    @Autowired
    private StaffExcelExportService staffExcelExportService;

    @Autowired
    private StaffExcelImportService staffExcelImportService;

    @Autowired
    private StaffExcelConfirmationService staffExcelConfirmationService;

    @Autowired
    private hrm.repository.DepartmentRepository departmentRepository;

    @Autowired
    private hrm.repository.PositionRepository positionRepository;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StaffProfileDto>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin cá nhân thành công", staffService.getCurrentStaffProfile()));
    }

    @GetMapping("/departments")
    @PreAuthorize("@access.any(authentication, 'DEPARTMENT_VIEW', 'POSITION_VIEW', 'POSITION_MANAGE', 'STAFF_VIEW', 'STAFF_CREATE', 'STAFF_UPDATE', 'STAFF_IMPORT', 'ATTENDANCE_VIEW', 'ATTENDANCE_APPROVE', 'LEAVE_VIEW', 'PAYROLL_VIEW', 'RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<List<hrm.model.Department>>> getAllDepartments() {
        List<hrm.model.Department> result = departmentRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách phòng ban thành công", result));
    }

    @GetMapping("/positions")
    @PreAuthorize("@access.any(authentication, 'POSITION_VIEW', 'STAFF_VIEW', 'STAFF_CREATE', 'STAFF_UPDATE', 'STAFF_IMPORT', 'LEAVE_VIEW', 'RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<List<hrm.model.Position>>> getAllPositions() {
        List<hrm.model.Position> result = positionRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách chức danh thành công", result));
    }

    @GetMapping
    @PreAuthorize("@access.has(authentication, 'STAFF_VIEW')")
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaffsUnpaginated() {
        List<StaffDto> result = staffService.getAllStaffsUnpaginated();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách nhân viên thành công", result));
    }

    public record StaffLookup(UUID id, String staffCode, String displayName, UUID departmentId, String departmentName, String positionName) {}

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or @access.any(authentication, 'STAFF_VIEW', 'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE', 'ATTENDANCE_APPROVE', 'LEAVE_VIEW', 'LEAVE_MANAGE', 'LEAVE_APPROVE', 'SALARY_CONFIG_VIEW', 'SALARY_CONFIG_MANAGE', 'PAYROLL_MANAGE', 'RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<List<StaffLookup>>> lookupStaffs() {
        List<StaffLookup> result = staffService.getAllStaffsUnpaginated().stream()
                .map(staff -> new StaffLookup(staff.getId(), staff.getStaffCode(), staff.getDisplayName(), staff.getDepartmentId(), staff.getDepartmentName(), staff.getPositionName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chọn nhân viên thành công", result));
    }

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'STAFF_VIEW')")
    public ResponseEntity<ApiResponse<Page<StaffDto>>> getAllStaffs(@RequestBody(required = false) SearchDto searchDto) {
        Page<StaffDto> result = staffService.getAllStaffs(searchDto);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhân viên thành công", result));
    }

    @PostMapping("/export-excel")
    @PreAuthorize("@access.has(authentication, 'STAFF_EXPORT')")
    public ResponseEntity<byte[]> exportStaffs(@RequestBody(required = false) SearchDto searchDto,
            @RequestHeader(value = "X-Confirmation-Password", required = false) String encodedPassword) throws IOException {
        staffExcelConfirmationService.confirmEncodedHeader(encodedPassword);
        byte[] content = staffExcelExportService.export(searchDto);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StaffExcelExportService.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Danh_sach_nhan_vien.xlsx").build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/generate-staff-code")
    @PreAuthorize("@access.has(authentication, 'STAFF_CREATE')")
    public ResponseEntity<ApiResponse<String>> generateStaffCode() {
        String code = staffService.generateStaffCode();
        return ResponseEntity.ok(ApiResponse.success("Tạo mã nhân viên thành công", code));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        byte[] content = staffExcelImportService.template();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StaffExcelExportService.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Mau_nhap_nhan_vien_moi.xlsx").build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<StaffImportResult>> importNewStaffs(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) throws IOException {
        staffExcelConfirmationService.confirmPassword(password);
        try {
            StaffImportResult result = staffExcelImportService.importFile(file);
            if (!result.errors().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.custom(
                        "File có dữ liệu chưa hợp lệ. Chưa thêm nhân viên nào; hãy sửa các dòng được báo và nhập lại.", result, HttpStatus.BAD_REQUEST));
            }
            return ResponseEntity.ok(ApiResponse.success("Đã thêm " + result.importedCount() + " nhân viên mới.", result));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.custom(
                    "Dữ liệu bị trùng hoặc đã thay đổi trong lúc nhập. Chưa lưu đợt nhập; hãy kiểm tra lại mã nhân viên và email.",
                    new StaffImportResult(0, 0, List.of()), HttpStatus.BAD_REQUEST));
        }
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleImportUpload(Exception ex) {
        String message = ex instanceof MaxUploadSizeExceededException
                ? "File vượt quá dung lượng 1 MB." : "Vui lòng chọn file Excel để nhập nhân viên.";
        return ResponseEntity.badRequest().body(ApiResponse.error(message, HttpStatus.BAD_REQUEST));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'STAFF_CREATE')")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(@RequestBody StaffDto staffDto) {
        staffDto.setId(null);
        StaffDto result = staffService.saveStaff(staffDto);
        return ResponseEntity.ok(ApiResponse.success("Thêm nhân viên thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'STAFF_VIEW')")
    public ResponseEntity<ApiResponse<StaffDto>> getStaffById(@PathVariable UUID id) {
        StaffDto staffDto = staffService.getStaffById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + id));
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin nhân viên thành công", staffDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<StaffDto>> updateStaff(@PathVariable UUID id, @RequestBody StaffDto staffDto) {
        if (!staffService.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên để cập nhật với ID: " + id);
        }
        staffDto.setId(id);
        StaffDto result = staffService.saveStaff(staffDto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhân viên thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'STAFF_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable UUID id) {
        if (!staffService.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên để xóa với ID: " + id);
        }
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhân viên thành công", null));
    }
}

