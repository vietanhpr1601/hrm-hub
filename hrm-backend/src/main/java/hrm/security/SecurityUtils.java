package hrm.security;

import hrm.enums.RoleType;
import hrm.model.User;
import hrm.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.UUID;

@Component
public class SecurityUtils {

    @Autowired
    private UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
        return null;
    }

    /** Quản lý mọi dự án và công việc (quyền PROJECT_MANAGE; Admin luôn có). */
    public boolean isManagerOrAdmin(User user) {
        return user != null && hasPermission(user, "PROJECT_MANAGE");
    }

    /** Xem mọi dự án và công việc, kể cả dự án không tham gia (PROJECT_VIEW hoặc PROJECT_MANAGE). */
    public boolean canViewAllProjects(User user) {
        return user != null && (hasPermission(user, "PROJECT_VIEW") || hasPermission(user, "PROJECT_MANAGE"));
    }

    public boolean hasPermission(User user, String permission) {
        return PermissionPolicy.has(user, permission);
    }

    public UUID requireCurrentStaffId(User user) {
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập lại");
        }
        if (user.getStaff() == null) {
            throw new IllegalArgumentException("Tài khoản chưa liên kết với nhân viên");
        }
        return user.getStaff().getId();
    }

    /** Global access and self service are separate permissions, including explicit denials. */
    public void requireStaffAccess(UUID staffId, String globalPermission, String selfPermission) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập lại");
        }
        if (staffId == null) {
            throw new IllegalArgumentException("Nhân viên là bắt buộc");
        }
        if (hasPermission(currentUser, globalPermission)) {
            return;
        }
        UUID ownStaffId = requireCurrentStaffId(currentUser);
        if (!hasPermission(currentUser, selfPermission) || !staffId.equals(ownStaffId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác dữ liệu của nhân viên này");
        }
    }
}
