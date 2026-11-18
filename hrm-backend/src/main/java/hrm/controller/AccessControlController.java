package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.service.AccessControlService;
import hrm.service.AccessControlService.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/access-control")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AccessControlController {
    private final AccessControlService service;
    public AccessControlController(AccessControlService service) { this.service = service; }
    @GetMapping("/catalog")
    public ApiResponse<List<CatalogItem>> catalog() { return ApiResponse.success("Danh mục quyền", service.catalog()); }
    @GetMapping("/groups")
    public ApiResponse<Groups> groups() { return ApiResponse.success("Nhóm vai trò", service.groups()); }
    @PostMapping("/roles")
    public ApiResponse<RoleView> createRole(@RequestBody RoleCreate request) { return ApiResponse.success("Đã tạo nhóm quyền", service.createRole(request)); }
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RoleView> updateRole(@PathVariable UUID id, @RequestBody PermissionUpdate request) { return ApiResponse.success("Đã cập nhật quyền của nhóm", service.updateRole(id, request)); }
    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable UUID id) { service.deleteRole(id); return ApiResponse.success("Đã xóa nhóm quyền", null); }
    @GetMapping("/users/{id}")
    public ApiResponse<UserAccess> user(@PathVariable UUID id) { return ApiResponse.success("Quyền của tài khoản", service.getUserAccess(id)); }
    @PutMapping("/users/{id}")
    public ApiResponse<UserAccess> updateUser(@PathVariable UUID id, @RequestBody UserAccessUpdate request) { return ApiResponse.success("Đã cập nhật quyền tài khoản", service.updateUserAccess(id, request)); }
}
