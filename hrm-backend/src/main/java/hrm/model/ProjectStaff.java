package hrm.model;

import hrm.enums.ProjectRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "tbl_project_staff")
@Getter
@Setter
public class ProjectStaff extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role")
    private ProjectRole projectRole;

    /** Vai trò cụ thể trong dự án, ví dụ "Lập trình viên", "Kiểm thử", "BA". */
    @Column(name = "role_name")
    private String roleName;


    @Column(name = "joined_date")
    private LocalDate joinedDate;
}
