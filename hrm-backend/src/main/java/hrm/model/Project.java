package hrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "tbl_project")
@Getter
@Setter
public class Project extends BaseModel {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_finished")
    private Boolean isFinished = false;

    /** Công ty / khách hàng sở hữu dự án. */
    @Column(name = "company_name")
    private String companyName;

    /** Tiến độ dự án (0-100%). */
    @Column(name = "progress")
    private Integer progress = 0;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectStaff> projectStaffs;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProjectActivity> activities;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProjectWorkingStatus> workingStatuses;
}
