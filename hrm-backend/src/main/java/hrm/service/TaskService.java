package hrm.service;

import hrm.dto.request.TaskRequest;
import hrm.dto.response.TaskResponse;
import hrm.dto.search.TaskSearchRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface TaskService {
    Page<TaskResponse> searchTasks(TaskSearchRequest request);
    List<TaskResponse> getTasksForKanban(TaskSearchRequest request);
    TaskResponse getTaskById(UUID id);
    TaskResponse saveTask(TaskRequest request);
    void deleteTask(UUID id);
    TaskResponse updateTaskStatus(UUID taskId, UUID statusId);
    Page<TaskResponse> getMyTasks(TaskSearchRequest request);
    java.util.Map<UUID, Long> countTasksByStatus(TaskSearchRequest request);
}
