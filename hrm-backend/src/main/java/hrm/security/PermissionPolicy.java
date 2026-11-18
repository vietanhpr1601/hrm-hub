package hrm.security;

import hrm.model.*;
import java.util.*;

/** Resolves current database-backed permissions, never permissions from JWT claims. */
public final class PermissionPolicy {
    private PermissionPolicy() {}
    public static boolean adminOnly(String permission) { return "STAFF_IMPORT".equals(permission); }
    public static boolean active(BaseModel item) { return item != null && !Boolean.TRUE.equals(item.getIsDeleted()); }
    public static boolean isAdmin(User user) {
        return active(user) && Boolean.TRUE.equals(user.getActive()) && user.getUserRoles() != null
                && user.getUserRoles().stream().anyMatch(link -> active(link) && active(link.getRole()) && "ROLE_ADMIN".equals(link.getRole().getName()));
    }
    public static Set<String> rolePermissions(Role role) {
        if (!active(role)) return Set.of();
        if ("ROLE_ADMIN".equals(role.getName())) return Permission.codes();
        if (Boolean.TRUE.equals(role.getPermissionsConfigured())) return known(role.getPermissions());
        return defaults(role.getName());
    }
    public static Set<String> groupPermissions(User user) {
        Set<String> permissions = new HashSet<>();
        if (!active(user) || !Boolean.TRUE.equals(user.getActive())) return permissions;
        if (isAdmin(user)) return Permission.codes();
        if (user.getUserRoles() != null) for (UserRole link : user.getUserRoles()) {
            if (active(link)) permissions.addAll(rolePermissions(link.getRole()));
        }
        return permissions;
    }
    public static Set<String> effective(User user) {
        Set<String> permissions = groupPermissions(user);
        if (!active(user) || !Boolean.TRUE.equals(user.getActive())) return Set.of();
        if (isAdmin(user)) return Permission.codes();
        if (user.getPermissionOverrides() != null) user.getPermissionOverrides().forEach((permission, effect) -> {
            if (Permission.codes().contains(permission)) {
                if ("ALLOW".equals(effect)) permissions.add(permission);
                else if ("DENY".equals(effect)) permissions.remove(permission);
            }
        });
        permissions.removeIf(PermissionPolicy::adminOnly);
        return permissions;
    }
    public static boolean has(User user, String permission) { return effective(user).contains(permission); }
    private static Set<String> known(Collection<String> values) {
        Set<String> result = values == null ? new HashSet<>() : new HashSet<>(values);
        result.retainAll(Permission.codes());
        result.removeIf(PermissionPolicy::adminOnly);
        return result;
    }
    public static Set<String> defaults(String role) {
        if (role == null) return Set.of();
        if ("ROLE_ADMIN".equals(role)) return Permission.codes();
        Set<String> permissions = new HashSet<>();
        switch (role) {
            case "ROLE_USER" -> permissions.addAll(Set.of("ATTENDANCE_SELF", "LEAVE_SELF", "PAYROLL_SELF"));
            case "HR_MANAGER" -> {
                permissions.addAll(Permission.codes());
                permissions.removeAll(Set.of("DEPARTMENT_MANAGE", "POSITION_MANAGE"));
            }
            case "HR_EMPLOYEE" -> permissions.addAll(Set.of("STAFF_VIEW", "STAFF_CREATE", "STAFF_UPDATE", "STAFF_DELETE", "STAFF_EXPORT",
                    "DEPARTMENT_VIEW", "POSITION_VIEW", "ATTENDANCE_SELF", "LEAVE_SELF", "PAYROLL_SELF", "PERIOD_VIEW", "SHIFT_VIEW", "AI_CHAT"));
            case "HR_RECRUITMENT" -> permissions.addAll(Set.of("RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "CANDIDATE_VIEW", "CANDIDATE_MANAGE", "STAFF_CREATE", "LEAVE_SELF", "AI_CHAT"));
            case "HR_COMPENSATION_BENEFIT" -> permissions.addAll(Set.of("PAYROLL_VIEW", "PAYROLL_MANAGE", "PAYROLL_CONFIRM", "PAYROLL_PAY", "PAYROLL_SELF",
                    "SALARY_CONFIG_VIEW", "SALARY_CONFIG_MANAGE", "PERIOD_VIEW", "PERIOD_MANAGE", "LEAVE_SELF", "AI_CHAT"));
            case "HR_TIMEKEEPING_MANAGER" -> permissions.addAll(Set.of("ATTENDANCE_VIEW", "ATTENDANCE_MANAGE", "ATTENDANCE_APPROVE", "SHIFT_VIEW", "SHIFT_MANAGE", "LEAVE_SELF", "AI_CHAT"));
            default -> { return Set.of(); }
        }
        permissions.add("NOTIFICATION_VIEW");
        permissions.removeIf(PermissionPolicy::adminOnly);
        return permissions;
    }
}
