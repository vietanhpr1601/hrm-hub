package hrm.dto.request;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String username;
    private String password;
    private Boolean active;
    private Set<Role> roles;
    private UUID staffId;
    private String staffName;
    private String staffCode;
    private String avatarUrl;
    private Set<String> permissions;
    private java.util.Map<String, String> permissionOverrides;

    public UserDto(User entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.username = entity.getUsername();
            this.active = entity.getActive();
            this.permissions = hrm.security.PermissionPolicy.effective(entity);
            java.util.Map<String, String> overrides = entity.getPermissionOverrides() == null
                    ? new java.util.HashMap<>() : new java.util.HashMap<>(entity.getPermissionOverrides());
            overrides.keySet().retainAll(hrm.security.Permission.codes());
            this.permissionOverrides = java.util.Map.copyOf(overrides);
            if (entity.getUserRoles() != null) {
                this.roles = entity.getUserRoles().stream()
                        .filter(hrm.security.PermissionPolicy::active).map(UserRole::getRole)
                        .filter(hrm.security.PermissionPolicy::active).collect(Collectors.toSet());
            }
            if (entity.getStaff() != null) {
                this.staffId = entity.getStaff().getId();
                this.staffName = entity.getStaff().getDisplayName();
                this.staffCode = entity.getStaff().getStaffCode();
                this.avatarUrl = entity.getStaff().getAvatarUrl();
            }
        }
    }
}
