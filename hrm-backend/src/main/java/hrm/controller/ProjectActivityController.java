package hrm.controller;

import hrm.dto.request.ProjectActivityRequest;
import hrm.dto.response.ApiResponse;
import hrm.dto.response.ProjectActivityResponse;
import hrm.service.ProjectActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static hrm.enums.RoleType.Constants.*;

@RestController
@RequestMapping("/api/projects/{projectId}/activities")
@CrossOrigin(origins = "*")
public class ProjectActivityController {

    @Autowired
    private ProjectActivityService projectActivityService;

    @GetMapping
    @PreAuthorize("@projectUtils.hasProjectAccess(#projectId)")
    public ResponseEntity<ApiResponse<List<ProjectActivityResponse>>> getActivities(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String keyword) {
        List<ProjectActivityResponse> result = projectActivityService.getProjectActivities(projectId, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hoạt động thành công", result));
    }

    @PostMapping
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#projectId)")
    public ResponseEntity<ApiResponse<ProjectActivityResponse>> addActivity(
            @PathVariable UUID projectId,
            @RequestBody ProjectActivityRequest request) {
        ProjectActivityResponse result = projectActivityService.addProjectActivity(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Thêm hoạt động thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#projectId)")
    public ResponseEntity<ApiResponse<ProjectActivityResponse>> updateActivity(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestBody ProjectActivityRequest request) {
        ProjectActivityResponse result = projectActivityService.updateProjectActivity(projectId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hoạt động thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#projectId)")
    public ResponseEntity<ApiResponse<Void>> deleteActivity(@PathVariable UUID projectId, @PathVariable UUID id) {
        projectActivityService.deleteProjectActivity(projectId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa hoạt động thành công", null));
    }

    @PutMapping("/reorder")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#projectId)")
    public ResponseEntity<ApiResponse<List<ProjectActivityResponse>>> reorderActivities(
            @PathVariable UUID projectId,
            @RequestBody List<UUID> activityIds) {
        List<ProjectActivityResponse> result = projectActivityService.reorderProjectActivities(projectId, activityIds);
        return ResponseEntity.ok(ApiResponse.success("Sắp xếp hoạt động thành công", result));
    }
}
