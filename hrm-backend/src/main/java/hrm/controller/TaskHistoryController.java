package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.dto.response.TaskHistoryResponse;
import hrm.service.TaskHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskHistoryController {

    @Autowired
    private TaskHistoryService taskHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{taskId}/history")
    @PreAuthorize("@projectUtils.hasProjectAccessByTaskId(#taskId)")
    public ResponseEntity<ApiResponse<List<TaskHistoryResponse>>> getHistoryByTaskId(@PathVariable UUID taskId) {
        List<TaskHistoryResponse> result = taskHistoryService.getHistoryByTaskId(taskId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử công việc thành công", result));
    }

    @PostMapping("/{taskId}/comments")
    @PreAuthorize("@projectUtils.hasProjectWorkAccessByTaskId(#taskId)")
    public ResponseEntity<ApiResponse<TaskHistoryResponse>> addComment(
            @PathVariable UUID taskId,
            @RequestBody String comment) {
        String commentText = comment;
        try {
            if (comment.trim().startsWith("{")) {
                JsonNode node = objectMapper.readTree(comment);
                if (node.has("comment")) {
                    commentText = node.get("comment").asText();
                }
            }
        } catch (Exception e) {
            // Fallback to raw string
        }

        TaskHistoryResponse result = taskHistoryService.addComment(taskId, commentText);
        return ResponseEntity.ok(ApiResponse.success("Thêm bình luận thành công", result));
    }

    // Quyền sửa/xóa được kiểm tra trong service: chỉ người viết bình luận hoặc Admin/Quản lý nhân sự.
    @PutMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskHistoryResponse>> updateComment(
            @PathVariable UUID id,
            @RequestBody String comment) {
        String commentText = comment;
        try {
            if (comment.trim().startsWith("{")) {
                JsonNode node = objectMapper.readTree(comment);
                if (node.has("comment")) {
                    commentText = node.get("comment").asText();
                }
            }
        } catch (Exception e) {
            // Fallback to raw string
        }

        TaskHistoryResponse result = taskHistoryService.updateComment(id, commentText);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bình luận thành công", result));
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID id) {
        taskHistoryService.deleteComment(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa bình luận thành công", null));
    }
}
