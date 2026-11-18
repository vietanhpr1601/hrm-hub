package hrm.service.impl;

import hrm.dto.request.ProjectCreateRequest;
import hrm.dto.request.ProjectStaffRequest;
import hrm.dto.request.StaffDto;
import hrm.dto.response.ProjectResponse;
import hrm.dto.search.ProjectSearchRequest;
import hrm.exception.ResourceNotFoundException;
import hrm.enums.ProjectRole;
import hrm.model.*;
import hrm.repository.*;
import hrm.security.SecurityUtils;
import hrm.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectStaffRepository projectStaffRepository;

    @Autowired
    private ProjectWorkingStatusRepository projectWorkingStatusRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private hrm.service.ProjectAssignmentMailer projectAssignmentMailer;

    @Autowired
    private TaskRepository taskRepository;

    /** Tiến độ = số công việc ở cột cuối cùng (Hoàn thành) / tổng số công việc; dự án chưa có công việc thì dùng tiến độ nhập tay. */
    private ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse(project);
        List<Task> tasks = taskRepository.findByProjectIdAndIsDeletedFalse(project.getId());
        response.setTaskTotal(tasks.size());
        ProjectWorkingStatus doneStatus = projectWorkingStatusRepository.findByProjectIdOrderByDisplayOrderAsc(project.getId()).stream()
                .filter(ws -> !Boolean.TRUE.equals(ws.getIsDeleted()))
                .reduce((first, second) -> second).orElse(null);
        int done = doneStatus == null ? 0 : (int) tasks.stream()
                .filter(task -> task.getStatus() != null && doneStatus.getId().equals(task.getStatus().getId())).count();
        response.setTaskDone(done);
        // Tiến độ của từng người: số việc được giao đã hoàn thành / tổng số việc được giao.
        for (hrm.dto.response.ProjectStaffDto member : response.getStaffs()) {
            List<Task> mine = tasks.stream().filter(task -> task.getAssignee() != null && task.getAssignee().getId().equals(member.getStaffId())).toList();
            member.setTaskTotal(mine.size());
            member.setTaskDone(doneStatus == null ? 0 : (int) mine.stream()
                    .filter(task -> task.getStatus() != null && doneStatus.getId().equals(task.getStatus().getId())).count());
        }
        if (!Boolean.TRUE.equals(project.getIsFinished()) && !tasks.isEmpty()) {
            response.setProgress((int) Math.round(done * 100.0 / tasks.size()));
        }
        return response;
    }

    @Override
    public Page<ProjectResponse> getAllProjects(ProjectSearchRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        boolean isManager = securityUtils.canViewAllProjects(currentUser);
        Staff currentStaff = (currentUser != null) ? currentUser.getStaff() : null;

        List<Project> filteredList = projectRepository.findAll().stream()
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .filter(p -> {
                    // Phân quyền: Employee chỉ xem được dự án họ tham gia
                    if (!isManager) {
                        if (currentStaff == null) {
                            return false;
                        }
                        boolean isMember = p.getProjectStaffs().stream()
                                .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                                .anyMatch(ps -> ps.getStaff() != null
                                        && ps.getStaff().getId().equals(currentStaff.getId()));
                        if (!isMember) {
                            return false;
                        }
                    }
                    return true;
                })
                .filter(p -> {
                    if (request != null) {
                        // 1. Tìm kiếm theo keyword (tên hoặc mã dự án)
                        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                            String kw = request.getKeyword().toLowerCase();
                            boolean match = (p.getName() != null && p.getName().toLowerCase().contains(kw))
                                    || (p.getCode() != null && p.getCode().toLowerCase().contains(kw))
                                    || (p.getCompanyName() != null && p.getCompanyName().toLowerCase().contains(kw));
                            if (!match)
                                return false;
                        }
                        // 2. Lọc theo khoảng ngày bắt đầu
                        if (request.getStartDate() != null) {
                            if (p.getStartDate() == null || p.getStartDate().isBefore(request.getStartDate())) {
                                return false;
                            }
                        }
                        // 3. Lọc theo khoảng ngày kết thúc
                        if (request.getEndDate() != null) {
                            if (p.getEndDate() == null || p.getEndDate().isAfter(request.getEndDate())) {
                                return false;
                            }
                        }
                        // 4. Lọc theo trạng thái kết thúc
                        if (request.getIsFinished() != null) {
                            if (!request.getIsFinished().equals(p.getIsFinished())) {
                                return false;
                            }
                        }
                        // 5. Lọc theo một nhân viên cụ thể
                        if (request.getStaffId() != null) {
                            boolean hasStaff = p.getProjectStaffs().stream()
                                    .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                                    .anyMatch(ps -> ps.getStaff() != null
                                            && ps.getStaff().getId().equals(request.getStaffId()));
                            if (!hasStaff)
                                return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int total = filteredList.size();
        int pageNum = 0;
        int size = 10;

        if (request != null) {
            pageNum = request.getPageIndex() >= 1 ? request.getPageIndex() - 1 : 0;
            size = request.getPageSize() > 0 ? request.getPageSize() : 10;
        }

        int fromIndex = pageNum * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<ProjectResponse> pageContent = new ArrayList<>();
        if (fromIndex < total) {
            pageContent = filteredList.subList(fromIndex, toIndex).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, PageRequest.of(pageNum, size), total);
    }

    @Override
    public List<ProjectResponse> getAllProjectsUnpaginated() {
        User currentUser = securityUtils.getCurrentUser();
        boolean isManager = securityUtils.canViewAllProjects(currentUser);
        Staff currentStaff = (currentUser != null) ? currentUser.getStaff() : null;

        return projectRepository.findAll().stream()
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .filter(p -> {
                    if (!isManager) {
                        if (currentStaff == null) {
                            return false;
                        }
                        boolean isMember = p.getProjectStaffs().stream()
                                .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                                .anyMatch(ps -> ps.getStaff() != null
                                        && ps.getStaff().getId().equals(currentStaff.getId()));
                        if (!isMember) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectResponse getProjectById(UUID id) {
        Project project = projectRepository.findById(id)
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + id));
        return toResponse(project);
    }

    @Override
    public ProjectResponse saveProject(ProjectCreateRequest request) {
        if (request.getCode() != null) {
            String finalCode = request.getCode().trim().toUpperCase();
            java.util.Optional<Project> existing = projectRepository.findActiveByCode(finalCode);
            if (existing.isPresent() && (request.getId() == null || !existing.get().getId().equals(request.getId()))) {
                throw new IllegalArgumentException("Mã dự án '" + finalCode + "' đã tồn tại");
            }
        }

        Project project;
        if (request.getId() != null) {
            project = projectRepository.findById(request.getId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + request.getId()));
        } else {
            project = new Project();
            project.setIsFinished(false);
        }

        project.setName(request.getName());
        if (request.getCode() != null) {
            project.setCode(request.getCode().trim().toUpperCase());
        } else {
            project.setCode(null);
        }
        project.setDescription(request.getDescription());
        project.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        project.setEndDate(request.getEndDate());
        project.setCompanyName(request.getCompanyName() == null || request.getCompanyName().isBlank()
                ? null : request.getCompanyName().trim());
        if (request.getProgress() != null) {
            if (request.getProgress() < 0 || request.getProgress() > 100)
                throw new IllegalArgumentException("Tiến độ phải từ 0 đến 100%");
            project.setProgress(request.getProgress());
        } else if (project.getProgress() == null) {
            project.setProgress(0);
        }

        Project saved = projectRepository.save(project);

        // Lưu thành viên dự án nếu được truyền lên
        if (request.getStaffs() != null) {
            // Đánh dấu các thành viên cũ là isDeleted
            List<ProjectStaff> oldMembers = projectStaffRepository.findByProjectId(saved.getId());
            // Ghi nhớ ai đang ở dự án với vai trò gì, để chỉ gửi email cho người mới được phân công hoặc đổi vai trò.
            Map<UUID, String> activeBefore = new HashMap<>();
            for (ProjectStaff om : oldMembers) {
                if (om.getStaff() != null && !Boolean.TRUE.equals(om.getIsDeleted()))
                    activeBefore.put(om.getStaff().getId(), om.getProjectRole() + "|" + Objects.toString(om.getRoleName(), ""));
            }
            User assigner = securityUtils.getCurrentUser();
            String assignedBy = assigner == null ? null
                    : assigner.getStaff() != null && assigner.getStaff().getDisplayName() != null ? assigner.getStaff().getDisplayName() : assigner.getUsername();
            for (ProjectStaff om : oldMembers) {
                om.setIsDeleted(true);
                projectStaffRepository.save(om);
            }

            // Thêm mới hoặc kích hoạt lại thành viên
            for (ProjectStaffRequest pmReq : request.getStaffs()) {
                if (pmReq.getStaffId() == null)
                    continue;

                Staff staff = staffRepository.findById(pmReq.getStaffId()).orElse(null);
                if (staff != null) {
                    ProjectStaff member = oldMembers.stream()
                            .filter(om -> om.getStaff().getId().equals(pmReq.getStaffId()))
                            .findFirst()
                            .orElse(new ProjectStaff());

                    member.setProject(saved);
                    member.setStaff(staff);
                    member.setProjectRole(pmReq.getProjectRole() != null ? pmReq.getProjectRole() : ProjectRole.MEMBER);
                    member.setRoleName(pmReq.getRoleName() == null || pmReq.getRoleName().isBlank()
                            ? null : pmReq.getRoleName().trim());
                    member.setJoinedDate(pmReq.getJoinedDate() != null ? pmReq.getJoinedDate() : LocalDate.now());
                    member.setIsDeleted(false);
                    projectStaffRepository.save(member);
                    String assignment = member.getProjectRole() + "|" + Objects.toString(member.getRoleName(), "");
                    if (!assignment.equals(activeBefore.get(staff.getId()))) {
                        projectAssignmentMailer.notifyAssigned(saved, staff, member.getProjectRole(), member.getRoleName(), assignedBy);
                    }
                }
            }
        }

        // Tạo mặc định các trạng thái Kanban nếu là dự án mới và chưa cấu hình
        List<ProjectWorkingStatus> existingStatuses = projectWorkingStatusRepository.findByProjectId(saved.getId());
        if (existingStatuses.isEmpty()) {
            // Quy trình đơn giản: Chưa làm → Đang làm → Hoàn thành (cột cuối = xong, dùng để tính tiến độ).
            String[][] defaultStatuses = {
                    { "Chưa làm", "TODO", "#757575", "1" },
                    { "Đang làm", "DOING", "#ed6c02", "2" },
                    { "Hoàn thành", "DONE", "#2e7d32", "3" }
            };
            for (String[] ds : defaultStatuses) {
                ProjectWorkingStatus ws = new ProjectWorkingStatus();
                ws.setProject(saved);
                ws.setName(ds[0]);
                ws.setCode(ds[1]);
                ws.setColor(ds[2]);
                ws.setDisplayOrder(Integer.parseInt(ds[3]));
                ws.setIsDeleted(false);
                projectWorkingStatusRepository.save(ws);
            }
        }

        return toResponse(projectRepository.findById(saved.getId()).orElse(saved));
    }

    @Override
    public void deleteProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + id));
        project.setIsDeleted(true);
        projectRepository.save(project);
    }

    @Override
    public void finishProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + id));
        project.setIsFinished(true);
        project.setEndDate(LocalDate.now());
        projectRepository.save(project);
    }

    @Override
    public void unfinishProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + id));
        project.setIsFinished(false);
        project.setEndDate(null);
        projectRepository.save(project);
    }

    @Override
    public List<StaffDto> getProjectStaffs(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getIsDeleted() == null || !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            return new ArrayList<>();
        }

        boolean isSystemManager = securityUtils.canViewAllProjects(currentUser);
        Staff currentStaff = currentUser.getStaff();

        boolean isProjectManager = false;
        if (currentStaff != null) {
            isProjectManager = project.getProjectStaffs().stream()
                    .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                    .anyMatch(ps -> ps.getStaff() != null 
                            && ps.getStaff().getId().equals(currentStaff.getId()) 
                            && ps.getProjectRole() == ProjectRole.MANAGER);
        }

        if (isSystemManager || isProjectManager) {
            return project.getProjectStaffs().stream()
                    .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                    .map(ps -> ps.getStaff() != null ? new StaffDto(ps.getStaff()) : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            List<StaffDto> staffs = new ArrayList<>();
            if (currentStaff != null) {
                boolean isMemberOfProject = project.getProjectStaffs().stream()
                        .filter(ps -> ps.getIsDeleted() == null || !ps.getIsDeleted())
                        .anyMatch(ps -> ps.getStaff() != null 
                                && ps.getStaff().getId().equals(currentStaff.getId()));
                if (isMemberOfProject) {
                    staffs.add(new StaffDto(currentStaff));
                }
            }
            return staffs;
        }
    }

}
