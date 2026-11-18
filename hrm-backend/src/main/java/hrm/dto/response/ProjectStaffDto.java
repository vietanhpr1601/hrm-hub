package hrm.dto.response;

import hrm.enums.ProjectRole;
import hrm.model.ProjectStaff;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ProjectStaffDto {
    private UUID id;
    private UUID staffId;
    private String staffCode;
    private String displayName;
    private String email;
    private ProjectRole projectRole;
    private String roleName;
    /** Số việc được giao trong dự án và số việc đã hoàn thành. */
    private Integer taskTotal = 0;
    private Integer taskDone = 0;
    private LocalDate joinedDate;

    public ProjectStaffDto() {}

    public ProjectStaffDto(ProjectStaff entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.projectRole = entity.getProjectRole();
            this.roleName = entity.getRoleName();
            this.joinedDate = entity.getJoinedDate();
            if (entity.getStaff() != null) {
                this.staffId = entity.getStaff().getId();
                this.staffCode = entity.getStaff().getStaffCode();
                this.displayName = entity.getStaff().getDisplayName();
                this.email = entity.getStaff().getEmail();
            }
        }
    }
}
