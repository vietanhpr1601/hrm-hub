package hrm.service.impl;

import hrm.dto.request.UserDto;
import hrm.dto.search.SearchDto;
import hrm.model.User;
import hrm.model.Role;
import hrm.model.UserRole;
import hrm.repository.StaffRepository;
import hrm.repository.UserRepository;
import hrm.repository.RoleRepository;
import hrm.security.PermissionPolicy;
import hrm.service.UserService;
import hrm.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public Page<UserDto> getAllUsers(SearchDto searchDto) {
        List<User> filteredList = userRepository.findAll().stream()
                .filter(user -> user.getIsDeleted() == null || !user.getIsDeleted())
                .filter(user -> {
                    if (searchDto != null) {
                        // 1. Keyword search (username, email, staff display name, staff code)
                        if (searchDto.getKeyword() != null && !searchDto.getKeyword().isEmpty()) {
                            String keyword = searchDto.getKeyword().toLowerCase();
                            boolean matches = (user.getUsername() != null && user.getUsername().toLowerCase().contains(keyword))
                                || (user.getStaff() != null && (
                                    (user.getStaff().getDisplayName() != null && user.getStaff().getDisplayName().toLowerCase().contains(keyword))
                                    || (user.getStaff().getStaffCode() != null && user.getStaff().getStaffCode().toLowerCase().contains(keyword))
                                ));
                            if (!matches) return false;
                        }
                        // 2. Active status filter
                        if (searchDto.getActive() != null) {
                            if (user.getActive() == null || !user.getActive().equals(searchDto.getActive())) {
                                return false;
                            }
                        }
                        // 3. Department filter (linked via staff)
                        if (searchDto.getDepartmentId() != null) {
                            if (user.getStaff() == null || user.getStaff().getDepartment() == null || !user.getStaff().getDepartment().getId().equals(searchDto.getDepartmentId())) {
                                return false;
                            }
                        }
                        // 4. Position filter (linked via staff)
                        if (searchDto.getPositionId() != null) {
                            if (user.getStaff() == null || user.getStaff().getPosition() == null || !user.getStaff().getPosition().getId().equals(searchDto.getPositionId())) {
                                return false;
                            }
                        }
                        // 5. Role filter
                        if (searchDto.getRoleId() != null) {
                            boolean hasRole = user.getUserRoles() != null && user.getUserRoles().stream()
                                .anyMatch(ur -> ur.getRole() != null && ur.getRole().getId().equals(searchDto.getRoleId()));
                            if (!hasRole) return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int total = filteredList.size();
        int pageNum = 0;
        int size = 10;

        if (searchDto != null) {
            pageNum = searchDto.getPageIndex() >= 1 ? searchDto.getPageIndex() - 1 : 0;
            size = searchDto.getPageSize() > 0 ? searchDto.getPageSize() : 10;
        }

        int fromIndex = pageNum * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<UserDto> pageContent = new java.util.ArrayList<>();
        if (fromIndex < total) {
            pageContent = filteredList.subList(fromIndex, toIndex).stream()
                    .map(UserDto::new)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, PageRequest.of(pageNum, size), total);
    }

    @Override
    public List<UserDto> getAllUsersUnpaginated() {
        return userRepository.findAll().stream()
                .filter(user -> user.getIsDeleted() == null || !user.getIsDeleted())
                .map(UserDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public UserDto getUserById(UUID id) {
        Optional<UserDto> user = userRepository.findById(id).map(UserDto::new);
        if (!user.isPresent()) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id);
        }
        return user.get();
    }

    @Override
    @Transactional
    public UserDto saveUser(UserDto userDto) {
        List<User> lockedUsers = userRepository.lockUsersForAccountChange();
        User previous = userDto.getId() == null ? null : lockedUsers.stream().filter(user -> userDto.getId().equals(user.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại"));
        if (previous != null && !PermissionPolicy.active(previous)) throw new IllegalArgumentException("Tài khoản đã bị xóa.");
        boolean wasAdmin = PermissionPolicy.isAdmin(previous);
        User user = toEntity(userDto);
        if (wasAdmin && !PermissionPolicy.isAdmin(user)) requireAnotherAdmin(lockedUsers, user.getId());
        if (PermissionPolicy.isAdmin(user)) user.getPermissionOverrides().clear();
        User savedUser = userRepository.save(user);
        return new UserDto(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        List<User> lockedUsers = userRepository.lockUsersForAccountChange();
        lockedUsers.stream().filter(user -> id.equals(user.getId())).findFirst().ifPresent(user -> {
            if (PermissionPolicy.isAdmin(user)) requireAnotherAdmin(lockedUsers, id);
            user.setIsDeleted(true);
            userRepository.save(user);
        });
    }

    @Override
    public boolean existsById(UUID id) {
        return userRepository.findById(id)
                .map(user -> user.getIsDeleted() == null || !user.getIsDeleted())
                .orElse(false);
    }

    private User toEntity(UserDto dto) {
        User user;
        if (dto.getId() != null) {
            user = userRepository.findById(dto.getId()).filter(PermissionPolicy::active).orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại"));
        } else {
            user = new User();
        }
        if (dto.getUsername() == null || dto.getUsername().isBlank() || dto.getUsername().length() > 255) throw new IllegalArgumentException("Tên đăng nhập là bắt buộc, tối đa 255 ký tự.");
        String username = dto.getUsername().trim();
        userRepository.findByUsername(username).filter(existing -> !existing.getId().equals(user.getId())).ifPresent(existing -> {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        });
        user.setUsername(username);
        if (user.getId() == null && (dto.getPassword() == null || dto.getPassword().isBlank())) throw new IllegalArgumentException("Mật khẩu là bắt buộc khi tạo tài khoản.");
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            // Encode password only if it is a new user or if password has changed
            if (user.getId() == null || !dto.getPassword().equals(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
        }
        if (dto.getActive() != null) user.setActive(dto.getActive());
        if (user.getUserRoles() == null) {
            user.setUserRoles(new HashSet<>());
        } else if (dto.getRoles() != null) {
            user.getUserRoles().clear();
        }
        if (dto.getRoles() != null) {
            for (Role role : dto.getRoles()) {
                if (role == null || role.getId() == null) throw new IllegalArgumentException("Nhóm quyền không hợp lệ.");
                Role assignedRole = roleRepository.findById(role.getId()).filter(PermissionPolicy::active)
                        .orElseThrow(() -> new IllegalArgumentException("Nhóm quyền không tồn tại hoặc đã bị xóa."));
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(assignedRole);
                user.getUserRoles().add(userRole);
            }
        }
        
        if (dto.getStaffId() != null) {
            hrm.model.Staff staff = staffRepository.findById(dto.getStaffId()).filter(PermissionPolicy::active)
                    .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc đã bị xóa."));
            boolean linked = userRepository.findAll().stream().anyMatch(existing -> existing.getStaff() != null
                    && dto.getStaffId().equals(existing.getStaff().getId()) && !existing.getId().equals(user.getId()));
            if (linked) throw new IllegalArgumentException("Nhân viên đã liên kết với tài khoản khác.");
            user.setStaff(staff);
        } else {
            user.setStaff(null);
        }
        return user;
    }

    private void requireAnotherAdmin(List<User> users, UUID excludedId) {
        if (users.stream().noneMatch(user -> !user.getId().equals(excludedId) && PermissionPolicy.isAdmin(user))) {
            throw new IllegalArgumentException("Hệ thống phải còn ít nhất một tài khoản Admin đang hoạt động.");
        }
    }
}
