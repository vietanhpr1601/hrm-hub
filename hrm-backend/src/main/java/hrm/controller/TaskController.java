package hrm.controller;

import hrm.dto.request.TaskRequest;
import hrm.dto.response.ApiResponse;
import hrm.dto.response.TaskAttachmentResponse;
import hrm.dto.response.TaskResponse;
import hrm.dto.search.TaskSearchRequest;
import hrm.exception.ResourceNotFoundException;
import hrm.model.Task;
import hrm.model.TaskAttachment;
import hrm.repository.TaskAttachmentRepository;
import hrm.repository.TaskRepository;
import hrm.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAttachmentRepository taskAttachmentRepository;

    @PostMapping("/paging")
    @PreAuthorize("@projectUtils.hasProjectAccess(#request?.projectId)")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> searchTasks(
            @RequestBody(required = false) TaskSearchRequest request) {
        Page<TaskResponse> result = taskService.searchTasks(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công việc thành công", result));
    }

    @PostMapping("/kanban")
    @PreAuthorize("@projectUtils.hasProjectAccess(#request?.projectId)")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasksForKanban(
            @RequestBody(required = false) TaskSearchRequest request) {
        List<TaskResponse> result = taskService.getTasksForKanban(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Kanban thành công", result));
    }

    @PostMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getMyTasks(
            @RequestBody(required = false) TaskSearchRequest request) {
        Page<TaskResponse> result = taskService.getMyTasks(request);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công việc của tôi thành công", result));
    }

    @PostMapping("/project/{projectId}/count-by-status")
    @PreAuthorize("@projectUtils.hasProjectAccess(#projectId)")
    public ResponseEntity<ApiResponse<java.util.Map<UUID, Long>>> countTasksByStatus(
            @PathVariable UUID projectId,
            @RequestBody(required = false) TaskSearchRequest request) {
        if (request == null) {
            request = new TaskSearchRequest();
        }
        request.setProjectId(projectId);
        java.util.Map<UUID, Long> result = taskService.countTasksByStatus(request);
        return ResponseEntity.ok(ApiResponse.success("Đếm số lượng công việc theo trạng thái thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectAccessByTaskId(#id)")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskById(@PathVariable UUID id) {
        TaskResponse result = taskService.getTaskById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết công việc thành công", result));
    }

    @PostMapping
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#request?.projectId)")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@RequestBody TaskRequest request) {
        request.setId(null);
        TaskResponse result = taskService.saveTask(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo công việc thành công", result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccess(#request?.projectId)")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(@PathVariable UUID id,
            @RequestBody TaskRequest request) {
        request.setId(id);
        TaskResponse result = taskService.saveTask(request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật công việc thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectUtils.hasProjectManagerAccessByTaskId(#id)")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa công việc thành công", null));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@projectUtils.hasProjectWorkAccessByTaskId(#id)")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTaskStatus(
            @PathVariable UUID id,
            @RequestParam UUID statusId) {
        TaskResponse result = taskService.updateTaskStatus(id, statusId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái công việc thành công", result));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("@projectUtils.hasProjectWorkAccessByTaskId(#id)")
    public ResponseEntity<ApiResponse<TaskAttachmentResponse>> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + id));

        // Lưu file vật lý
        String uploadDir = "uploads/tasks/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String savedFilename = UUID.randomUUID().toString() + extension;
        String filePath = uploadDir + savedFilename;
        Files.copy(file.getInputStream(), Paths.get(filePath));

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setName(originalFilename);
        attachment.setSize(file.getSize());
        attachment.setFilePath(filePath);
        attachment.setIsDeleted(false);

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        return ResponseEntity
                .ok(ApiResponse.success("Tải tập tin lên thành công", new TaskAttachmentResponse(savedAttachment)));
    }

    @PostMapping("/{id}/attachments/link")
    @PreAuthorize("@projectUtils.hasProjectWorkAccessByTaskId(#id)")
    public ResponseEntity<ApiResponse<TaskAttachmentResponse>> addAttachmentLink(
            @PathVariable UUID id,
            @RequestParam("name") String name,
            @RequestParam("size") Long size,
            @RequestParam("filePath") String filePath) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + id));

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setName(name);
        attachment.setSize(size);
        attachment.setFilePath(filePath);
        attachment.setIsDeleted(false);

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        ApiResponse<TaskAttachmentResponse> response = ApiResponse.success("Lưu tập tin đính kèm thành công",
                new TaskAttachmentResponse(savedAttachment));
        return ResponseEntity.ok(response);
    }
}
