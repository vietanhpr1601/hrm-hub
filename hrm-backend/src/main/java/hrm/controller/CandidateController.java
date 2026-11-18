package hrm.controller;

import hrm.dto.request.CandidateDto;
import hrm.dto.request.StaffDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.search.SearchCandidateDto;
import hrm.enums.CandidateStatus;
import hrm.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/candidates")
@CrossOrigin(origins = "*")
public class CandidateController {

    @Autowired
    private CandidateService candidateService;

    @PostMapping("/paging")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_VIEW')")
    public ResponseEntity<ApiResponse<Page<CandidateDto>>> pagingCandidates(
            @RequestBody(required = false) SearchCandidateDto searchDto) {
        Page<CandidateDto> result = candidateService.pagingCandidates(searchDto);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ứng viên thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_VIEW')")
    public ResponseEntity<ApiResponse<CandidateDto>> getById(@PathVariable UUID id) {
        CandidateDto dto = candidateService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ứng viên thành công", dto));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateDto>> createCandidate(@RequestBody CandidateDto dto) {
        if (!candidateService.isValidCode(dto)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Mã ứng viên đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        CandidateDto result = candidateService.saveCandidate(dto);
        return ResponseEntity.ok(ApiResponse.success("Tạo ứng viên thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateDto>> updateCandidate(@PathVariable UUID id,
            @RequestBody CandidateDto dto) {
        dto.setId(id);
        if (!candidateService.isValidCode(dto)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Mã ứng viên đã tồn tại", HttpStatus.BAD_REQUEST));
        }
        CandidateDto result = candidateService.saveCandidate(dto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ứng viên thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(@PathVariable UUID id) {
        candidateService.deleteCandidate(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa ứng viên thành công", null));
    }

    @PostMapping("/delete-multiple")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteMultiple(@RequestBody List<UUID> ids) {
        candidateService.deleteMultiple(ids);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh sách ứng viên thành công", null));
    }

    @GetMapping("/generate-code")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<String>> generateCode() {
        String code = candidateService.generateCode();
        return ResponseEntity.ok(ApiResponse.success("Tạo mã ứng viên thành công", code));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE')")
    public ResponseEntity<ApiResponse<Boolean>> updateStatus(
            @PathVariable UUID id,
            @RequestParam CandidateStatus status,
            @RequestParam(required = false) String refusalReason) {
        Boolean result = candidateService.updateStatus(id, status, refusalReason);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái ứng viên thành công", result));
    }

    @PostMapping("/{id}/convert-to-staff")
    @PreAuthorize("@access.has(authentication, 'CANDIDATE_MANAGE') and @access.has(authentication, 'STAFF_CREATE')")
    public ResponseEntity<ApiResponse<StaffDto>> convertToStaff(@PathVariable UUID id) {
        StaffDto result = candidateService.convertToReceivedJob(id);
        return ResponseEntity.ok(ApiResponse.success("Tiếp nhận ứng viên thành nhân viên thành công", result));
    }
}
