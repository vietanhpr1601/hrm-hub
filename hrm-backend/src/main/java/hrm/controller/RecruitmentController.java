package hrm.controller;

import hrm.dto.request.RecruitmentDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.search.SearchRecruitmentDto;
import hrm.exception.ResourceNotFoundException;
import hrm.service.RecruitmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/recruitments")
@CrossOrigin(origins = "*")
public class RecruitmentController {

    @Autowired
    private RecruitmentService recruitmentService;

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_VIEW')")
    public ResponseEntity<ApiResponse<Page<RecruitmentDto>>> pagingRecruitments(@RequestBody(required = false) SearchRecruitmentDto searchDto) {
        Page<RecruitmentDto> result = recruitmentService.pagingRecruitment(searchDto);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tin tuyển dụng thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_VIEW')")
    public ResponseEntity<ApiResponse<RecruitmentDto>> getById(@PathVariable UUID id) {
        RecruitmentDto dto = recruitmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tin tuyển dụng thành công", dto));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<RecruitmentDto>> createRecruitment(@RequestBody RecruitmentDto dto) {
        if (!recruitmentService.isValidCode(dto)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mã tin tuyển dụng đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        RecruitmentDto result = recruitmentService.saveRecruitment(dto);
        return ResponseEntity.ok(ApiResponse.success("Tạo tin tuyển dụng thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<RecruitmentDto>> updateRecruitment(@PathVariable UUID id, @RequestBody RecruitmentDto dto) {
        dto.setId(id);
        if (!recruitmentService.isValidCode(dto)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mã tin tuyển dụng đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        RecruitmentDto result = recruitmentService.saveRecruitment(dto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tin tuyển dụng thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteRecruitment(@PathVariable UUID id) {
        recruitmentService.deleteRecruitment(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa tin tuyển dụng thành công", null));
    }

    @PostMapping("/delete-multiple")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteMultiple(@RequestBody List<UUID> ids) {
        recruitmentService.deleteMultipleRecruitment(ids);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh sách tin tuyển dụng thành công", null));
    }

    @GetMapping("/generate-code")
    @PreAuthorize("@access.has(authentication, 'RECRUITMENT_MANAGE')")
    public ResponseEntity<ApiResponse<String>> generateCode() {
        String code = recruitmentService.generateCode();
        return ResponseEntity.ok(ApiResponse.success("Tạo mã tin tuyển dụng thành công", code));
    }
}
