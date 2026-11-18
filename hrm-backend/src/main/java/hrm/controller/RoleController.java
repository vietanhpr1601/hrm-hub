package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.model.Role;
import hrm.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@CrossOrigin(origins = "*")
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RoleController {

    @Autowired
    private RoleRepository roleRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles() {
        List<Role> result = roleRepository.findAll().stream().filter(hrm.security.PermissionPolicy::active).toList();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ danh sách vai trò thành công", result));
    }
}
