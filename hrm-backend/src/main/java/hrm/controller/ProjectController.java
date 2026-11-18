package hrm.controller;

import hrm.dto.request.ProjectCreateRequest;
import hrm.dto.request.ProjectStaffRequest;
import hrm.dto.request.StaffDto;
import hrm.dto.response.ApiResponse;
import hrm.dto.response.ProjectResponse;
import hrm.dto.response.ProjectStaffDto;
import hrm.dto.search.ProjectSearchRequest;
import hrm.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static hrm.enums.RoleType.Constants.*;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @PostMapping("/paging")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getAllProjects(
            @RequestBody(required = false) ProjectSearchRequest request) {
        Page<ProjectResponse> result = projectService.getAllProjects(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách dự án thành công", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getAllProjectsUnpaginated() {
        List<ProjectResponse> result = projectService.getAllProjectsUnpaginated();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách dự án thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectAccess(#id)")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(@PathVariable UUID id) {
        ProjectResponse result = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin dự án thành công", result));
    }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'PROJECT_MANAGE')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(@RequestBody ProjectCreateRequest request) {
        ProjectResponse result = projectService.saveProject(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm dự án thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#id)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(@PathVariable UUID id,
            @RequestBody ProjectCreateRequest request) {
        request.setId(id);
        ProjectResponse result = projectService.saveProject(request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật dự án thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#id)")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa dự án thành công", null));
    }

    @PutMapping("/{id}/finish")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#id)")
    public ResponseEntity<ApiResponse<Void>> finishProject(@PathVariable UUID id) {
        projectService.finishProject(id);
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành dự án thành công", null));
    }

    @PutMapping("/{id}/unfinish")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#id)")
    public ResponseEntity<ApiResponse<Void>> unfinishProject(@PathVariable UUID id) {
        projectService.unfinishProject(id);
        return ResponseEntity.ok(ApiResponse.success("Bỏ đánh dấu hoàn thành dự án thành công", null));
    }

    @GetMapping("/{id}/get-staffs")
    public ResponseEntity<ApiResponse<List<StaffDto>>> getProjectStaffs(@PathVariable UUID id) {
        List<StaffDto> result = projectService.getProjectStaffs(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành viên dự án thành công", result));
    }

}
