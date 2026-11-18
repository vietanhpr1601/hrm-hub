package hrm.service;

import hrm.dto.request.ProjectCreateRequest;
import hrm.dto.request.StaffDto;
import hrm.dto.response.ProjectResponse;
import hrm.dto.search.ProjectSearchRequest;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

public interface ProjectService {
    Page<ProjectResponse> getAllProjects(ProjectSearchRequest request);

    List<ProjectResponse> getAllProjectsUnpaginated();

    ProjectResponse getProjectById(UUID id);

    ProjectResponse saveProject(ProjectCreateRequest request);

    void deleteProject(UUID id);

    void finishProject(UUID id);

    void unfinishProject(UUID id);

    List<StaffDto> getProjectStaffs(UUID projectId);
}
