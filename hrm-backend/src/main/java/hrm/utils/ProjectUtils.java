package hrm.utils;

import hrm.model.Project;
import hrm.model.Staff;
import hrm.model.Task;
import hrm.model.User;
import hrm.enums.ProjectRole;
import hrm.repository.ProjectRepository;
import hrm.repository.TaskRepository;
import hrm.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProjectUtils {

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    /**
     * Checks if the current authenticated user has manager access to the project
     * (assigned as MANAGER of this project).
     */
    public boolean hasProjectManagerAccess(UUID projectId) {
        if (projectId == null) {
            return false;
        }
        User user = securityUtils.getCurrentUser();
        if (user == null) {
            return false;
        }
        if (securityUtils.isManagerOrAdmin(user)) {
            return projectExists(projectId);
        }
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .orElse(null);
        if (project == null) {
            return false;
        }
        Staff staff = user.getStaff();
        if (staff == null) {
            return false;
        }
        return project.getProjectStaffs().stream()
                .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                .anyMatch(ps -> ps.getStaff() != null
                        && ps.getStaff().getId().equals(staff.getId())
                        && ProjectRole.MANAGER.equals(ps.getProjectRole()));
    }

    /**
     * Checks if the current authenticated user has general view/access to the
     * project
     * (assigned as any role of this project).
     */
    public boolean hasProjectAccess(UUID projectId) {
        if (projectId == null) {
            return false;
        }
        User user = securityUtils.getCurrentUser();
        if (user == null) {
            return false;
        }
        if (securityUtils.canViewAllProjects(user)) {
            return projectExists(projectId);
        }
        return isMember(user, projectId);
    }

    /** Làm việc trên công việc (đổi trạng thái, đính kèm, bình luận): thành viên dự án hoặc PROJECT_MANAGE. */
    public boolean hasProjectWorkAccess(UUID projectId) {
        if (projectId == null) {
            return false;
        }
        User user = securityUtils.getCurrentUser();
        if (user == null) {
            return false;
        }
        if (securityUtils.isManagerOrAdmin(user)) {
            return projectExists(projectId);
        }
        return isMember(user, projectId);
    }

    public boolean hasProjectWorkAccessByTaskId(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .orElse(null);
        return task != null && task.getProject() != null && hasProjectWorkAccess(task.getProject().getId());
    }

    private boolean projectExists(UUID projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .isPresent();
    }

    private boolean isMember(User user, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .orElse(null);
        if (project == null) {
            return false;
        }
        Staff staff = user.getStaff();
        if (staff == null) {
            return false;
        }
        return project.getProjectStaffs().stream()
                .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                .anyMatch(ps -> ps.getStaff() != null
                        && ps.getStaff().getId().equals(staff.getId()));
    }

    /**
     * Checks if the current authenticated user has manager access to the project
     * associated with the given task ID.
     */
    public boolean hasProjectManagerAccessByTaskId(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .orElse(null);
        if (task == null || task.getProject() == null) {
            return false;
        }
        return hasProjectManagerAccess(task.getProject().getId());
    }

    /**
     * Checks if the current authenticated user has general view/access to the
     * project
     * associated with the given task ID.
     */
    public boolean hasProjectAccessByTaskId(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .orElse(null);
        if (task == null || task.getProject() == null) {
            return false;
        }
        return hasProjectAccess(task.getProject().getId());
    }
}
