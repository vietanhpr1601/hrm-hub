package hrm.service;

import hrm.enums.RoleType;
import hrm.model.*;
import hrm.repository.*;
import hrm.security.Permission;
import hrm.security.PermissionPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("access")
public class AccessControlService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public AccessControlService(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !Permission.codes().contains(permission)) return false;
        if (authentication.getPrincipal() instanceof User user) return PermissionPolicy.has(user, permission);
        Set<String> authorities = new HashSet<>();
        authentication.getAuthorities().forEach(item -> authorities.add(item.getAuthority()));
        if (PermissionPolicy.adminOnly(permission)) return authorities.contains("ROLE_ADMIN");
        if (authorities.contains("PERMISSIONS_RESOLVED")) return authorities.contains(permission);
        // Non-entity principals support method-security tests and explicit authority integrations.
        return authorities.contains(permission) || authorities.stream().anyMatch(role -> PermissionPolicy.defaults(role).contains(permission));
    }
    public boolean any(Authentication authentication, String... permissions) {
        return Arrays.stream(permissions).anyMatch(permission -> has(authentication, permission));
    }

    public record CatalogItem(String code, String label, String module, boolean adminOnly) {}
    public record RoleView(UUID id, String name, String description, Set<String> permissions, boolean system) {}
    public record Groups(List<RoleView> roles) {}
    public record UserAccess(UUID userId, String username, boolean admin, List<RoleView> roles,
                             Set<String> groupPermissions, Map<String, String> permissionOverrides, Set<String> permissions) {}
    public record PermissionUpdate(Set<String> permissions) {}
    public record RoleCreate(String name, String description, Set<String> permissions) {}
    public record UserAccessUpdate(Set<UUID> roleIds, Map<String, String> permissionOverrides) {}

    public List<CatalogItem> catalog() {
        return Arrays.stream(Permission.values()).map(item -> new CatalogItem(item.name(), item.getLabel(), item.getModule(), PermissionPolicy.adminOnly(item.name()))).toList();
    }
    @Transactional(readOnly = true)
    public Groups groups() {
        return new Groups(roleRepository.findAll().stream().filter(PermissionPolicy::active).map(this::roleView).toList());
    }
    @Transactional
    public RoleView createRole(RoleCreate request) {
        if (request.name() == null || !request.name().trim().toUpperCase(Locale.ROOT).matches("[A-Z][A-Z0-9_]{2,49}")) {
            throw new IllegalArgumentException("Mã nhóm gồm 3–50 ký tự, bắt đầu bằng chữ, chỉ dùng chữ không dấu, số và dấu gạch dưới.");
        }
        String name = request.name().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.findByName(name).isPresent() || systemRole(name) || Permission.codes().contains(name)
                || "PERMISSIONS_RESOLVED".equals(name)) throw new IllegalArgumentException("Mã nhóm quyền đã tồn tại hoặc được dành riêng cho hệ thống.");
        if (request.description() == null || request.description().isBlank() || request.description().length() > 255) {
            throw new IllegalArgumentException("Tên hiển thị nhóm là bắt buộc và tối đa 255 ký tự.");
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(request.description().trim());
        role.setPermissions(validatePermissions(request.permissions()));
        role.setPermissionsConfigured(true);
        return roleView(roleRepository.save(role));
    }
    @Transactional
    public RoleView updateRole(UUID id, PermissionUpdate request) {
        userRepository.lockUsersForAccountChange();
        Role role = activeRole(id);
        if ("ROLE_ADMIN".equals(role.getName())) throw new IllegalArgumentException("Nhóm Admin luôn có toàn quyền và không thể bị giới hạn.");
        role.setPermissions(validatePermissions(request.permissions()));
        role.setPermissionsConfigured(true);
        return roleView(roleRepository.save(role));
    }
    @Transactional
    public void deleteRole(UUID id) {
        List<User> lockedUsers = userRepository.lockUsersForAccountChange();
        Role role = activeRole(id);
        if ("ROLE_ADMIN".equals(role.getName())) {
            throw new IllegalArgumentException("Không thể xóa vai trò Admin.");
        }
        boolean assigned = lockedUsers.stream().filter(PermissionPolicy::active).anyMatch(user -> user.getUserRoles().stream()
                .anyMatch(link -> PermissionPolicy.active(link) && link.getRole() != null && id.equals(link.getRole().getId())));
        if (assigned) throw new IllegalArgumentException("Nhóm đang được gán cho tài khoản. Hãy bỏ gán trước khi xóa.");
        role.setIsDeleted(true);
        roleRepository.save(role);
    }
    @Transactional(readOnly = true)
    public UserAccess getUserAccess(UUID id) { return userAccess(activeUser(id)); }

    @Transactional
    public UserAccess updateUserAccess(UUID id, UserAccessUpdate request) {
        userRepository.lockUsersForAccountChange();
        User user = activeUser(id);
        if (PermissionPolicy.isAdmin(user)) throw new IllegalArgumentException("Tài khoản Admin luôn có toàn quyền; không áp dụng ngoại lệ quyền riêng.");
        if (request.roleIds() == null || request.permissionOverrides() == null) throw new IllegalArgumentException("Thiếu danh sách nhóm hoặc cấu hình quyền riêng.");
        Set<Role> roles = new HashSet<>();
        for (UUID roleId : request.roleIds()) roles.add(activeRole(roleId));
        Map<String, String> overrides = new HashMap<>();
        request.permissionOverrides().forEach((permission, effect) -> {
            if (PermissionPolicy.adminOnly(permission)) {
                throw new IllegalArgumentException("Nhập Excel nhân viên chỉ dành cho Admin, không thể cấp hoặc sửa thành quyền riêng.");
            }
            if (!Permission.codes().contains(permission) || !("ALLOW".equals(effect) || "DENY".equals(effect))) {
                throw new IllegalArgumentException("Quyền riêng không hợp lệ. Chỉ chấp nhận ALLOW hoặc DENY cho mã quyền trong danh mục.");
            }
            overrides.put(permission, effect);
        });
        if (roles.stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName())) && !overrides.isEmpty()) {
            throw new IllegalArgumentException("Nhóm Admin luôn toàn quyền; hãy bỏ quyền riêng khi cấp nhóm Admin.");
        }
        user.getUserRoles().clear();
        for (Role role : roles) {
            UserRole link = new UserRole();
            link.setUser(user);
            link.setRole(role);
            user.getUserRoles().add(link);
        }
        user.setPermissionOverrides(overrides);
        userRepository.saveAndFlush(user);
        return userAccess(user);
    }

    private Role activeRole(UUID id) {
        if (id == null) throw new IllegalArgumentException("Nhóm quyền là bắt buộc.");
        return roleRepository.findById(id).filter(PermissionPolicy::active).orElseThrow(() -> new IllegalArgumentException("Nhóm quyền không tồn tại hoặc đã bị xóa."));
    }
    private User activeUser(UUID id) {
        return userRepository.findById(id).filter(PermissionPolicy::active).orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại hoặc đã bị xóa."));
    }
    private Set<String> validatePermissions(Set<String> permissions) {
        if (permissions == null || !Permission.codes().containsAll(permissions)) throw new IllegalArgumentException("Danh sách mã quyền không hợp lệ.");
        if (permissions.stream().anyMatch(PermissionPolicy::adminOnly)) {
            throw new IllegalArgumentException("Nhập Excel nhân viên chỉ dành cho Admin, không thể cấp cho nhóm quyền khác.");
        }
        return new HashSet<>(permissions);
    }
    private boolean systemRole(String name) { return Arrays.stream(RoleType.values()).anyMatch(role -> role.getName().equals(name)); }
    private RoleView roleView(Role role) {
        return new RoleView(role.getId(), role.getName(), role.getDescription(), new TreeSet<>(PermissionPolicy.rolePermissions(role)), systemRole(role.getName()));
    }
    private UserAccess userAccess(User user) {
        Map<String, String> overrides = new HashMap<>(user.getPermissionOverrides());
        overrides.keySet().removeIf(permission -> !Permission.codes().contains(permission) || PermissionPolicy.adminOnly(permission));
        return new UserAccess(user.getId(), user.getUsername(), PermissionPolicy.isAdmin(user), user.getUserRoles().stream()
                .filter(PermissionPolicy::active).map(UserRole::getRole).filter(PermissionPolicy::active).map(this::roleView).toList(),
                new TreeSet<>(PermissionPolicy.groupPermissions(user)), Map.copyOf(overrides), new TreeSet<>(PermissionPolicy.effective(user)));
    }
}
