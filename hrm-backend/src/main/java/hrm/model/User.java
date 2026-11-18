package hrm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "tbl_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseModel implements UserDetails {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tbl_user_permission", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "permission")
    @Column(name = "effect", nullable = false)
    private java.util.Map<String, String> permissionOverrides = new java.util.HashMap<>();

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "active")
    private Boolean active = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        java.util.List<GrantedAuthority> authorities = (userRoles == null ? java.util.stream.Stream.<UserRole>empty() : userRoles.stream())
                .filter(ur -> hrm.security.PermissionPolicy.active(ur) && hrm.security.PermissionPolicy.active(ur.getRole()))
                .map(ur -> new SimpleGrantedAuthority(ur.getRole().getName()))
                .collect(Collectors.toList());
        hrm.security.PermissionPolicy.effective(this).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        authorities.add(new SimpleGrantedAuthority("PERMISSIONS_RESOLVED"));
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active != null && active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active != null && active;
    }
}
