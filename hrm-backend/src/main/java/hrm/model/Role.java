package hrm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseModel {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tbl_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false)
    private java.util.Set<String> permissions = new java.util.HashSet<>();

    @Column(name = "permissions_configured")
    private Boolean permissionsConfigured = false;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
