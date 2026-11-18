package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Department;
import hrm.model.Role;
import hrm.model.Staff;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.RoleRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import hrm.security.Permission;
import hrm.security.PermissionPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permission management and refresh behavior against the isolated integration database. */
@HrmIntegrationTest
class AccessControlIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private hrm.config.DatabaseInitializer initializer;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void newUserRoleStartsWithPersonalBusinessAndHomePermissions() throws Exception {
        Role basic = roleRepository.findByName("ROLE_USER").orElseThrow();
        assertTrue(basic.getPermissionsConfigured());
        assertEquals(Set.of("ATTENDANCE_SELF", "LEAVE_SELF", "PAYROLL_SELF", "NOTIFICATION_VIEW"), PermissionPolicy.rolePermissions(basic));
        User account = account(basic);
        entityManager.flush();
        String jwt = tokens.generateAccessToken(account);
        entityManager.clear();
        jwtStaffList(jwt, 403);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/shifts").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/access-control/groups").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
    }

    @Test
    void startupPreservesExplicitUserRoleConfigurationAndExistingAssignments() throws Exception {
        Role basic = roleRepository.findByName("ROLE_USER").orElseThrow();
        basic.setPermissions(new HashSet<>());
        basic.setPermissionsConfigured(true);
        Role custom = role("STAFF_VIEW");
        User existing = account(custom);
        entityManager.flush();
        initializer.run();
        flushAndClear();
        assertEquals(Set.of(), PermissionPolicy.rolePermissions(roleRepository.findByName("ROLE_USER").orElseThrow()));
        assertEquals(Set.of(custom.getId()), roleIds(reload(existing)));
    }

    @Test
    void promotingAccountToAdminThroughAccountFormClearsOldExceptions() throws Exception {
        User account = account(roleRepository.findByName("ROLE_USER").orElseThrow());
        account.setPermissionOverrides(new HashMap<>(Map.of("PAYROLL_SELF", "DENY")));
        flushAndClear();
        Role administrator = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        admin(json(put("/api/users/" + account.getId()), Map.of("username", account.getUsername(),
                "roles", List.of(Map.of("id", administrator.getId())))), 200);
        flushAndClear();
        assertTrue(PermissionPolicy.isAdmin(reload(account)));
        assertTrue(reload(account).getPermissionOverrides().isEmpty());
    }

    @Test
    void unionsMultipleRoleGroupsWithoutUsingTheLinkedEmployeesDepartment() throws Exception {
        Role readers = role("STAFF_VIEW");
        Role importers = role("STAFF_EXPORT");
        Department department = department();
        User account = account(readers, importers);
        account.setStaff(staff(department));
        flushAndClear();

        JsonNode result = admin(get("/api/access-control/users/" + account.getId()), 200);
        Set<String> expected = Set.of("STAFF_VIEW", "STAFF_EXPORT");
        assertEquals(expected, strings(result.path("groupPermissions")));
        assertEquals(expected, strings(result.path("permissions")));
        assertFalse(result.has("departmentId"));
        assertFalse(result.has("departmentName"));
        assertEquals(2, result.path("roles").size());
    }

    @Test
    void perAccountDenyOverridesRoleAndAllowAddsASeparatePermission() throws Exception {
        Role group = role("STAFF_VIEW", "STAFF_EXPORT");
        Department department = department();
        User account = account(group);
        account.setStaff(staff(department));
        entityManager.flush();
        Map<String, String> overrides = Map.of("STAFF_VIEW", "DENY", "PAYROLL_SELF", "ALLOW");
        JsonNode result = admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(group.getId()), "permissionOverrides", overrides)), 200);
        assertEquals(Set.of("STAFF_VIEW", "STAFF_EXPORT"), strings(result.path("groupPermissions")));
        assertEquals(Set.of("STAFF_EXPORT", "PAYROLL_SELF"), strings(result.path("permissions")));
        flushAndClear();
        assertEquals(overrides, reload(account).getPermissionOverrides());
        assertEquals(Set.of("STAFF_EXPORT", "PAYROLL_SELF"), PermissionPolicy.effective(reload(account)));
    }

    @Test
    void standaloneAccountCanReceiveDirectPermissionAndRemovingOverrideRestoresInheritedPermission() throws Exception {
        User account = account();
        entityManager.flush();
        JsonNode granted = admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(), "permissionOverrides", Map.of("STAFF_VIEW", "ALLOW"))), 200);
        assertEquals(Set.of("STAFF_VIEW"), strings(granted.path("permissions")));
        Role inherited = role("STAFF_VIEW");
        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(inherited.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "DENY"))), 200);
        JsonNode restored = admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(inherited.getId()), "permissionOverrides", Map.of())), 200);
        assertEquals(Set.of("STAFF_VIEW"), strings(restored.path("permissions")));
        assertEquals(0, restored.path("permissionOverrides").size());
    }

    @Test
    void explicitlyEmptyPermissionsRemoveLegacyDefaultsWithoutFallingBack() throws Exception {
        Role legacy = roleRepository.findByName("HR_EMPLOYEE").orElseThrow();
        legacy.setPermissionsConfigured(false);
        User account = account(legacy);
        assertTrue(PermissionPolicy.has(account, "STAFF_VIEW"));
        entityManager.flush();
        admin(json(put("/api/access-control/roles/" + legacy.getId() + "/permissions"),
                Map.of("permissions", Set.of())), 200);
        flushAndClear();
        assertTrue(roleRepository.findById(legacy.getId()).orElseThrow().getPermissionsConfigured());
        assertEquals(Set.of(), PermissionPolicy.effective(reload(account)));
    }

    @Test
    void movingStaffOrDeletingTheirDepartmentDoesNotChangeRoleAndAccountGrants() {
        Role group = role("STAFF_VIEW");
        Department oldDepartment = department();
        Department newDepartment = department();
        User account = account(group);
        account.setPermissionOverrides(new HashMap<>(Map.of("PAYROLL_SELF", "ALLOW")));
        account.setStaff(staff(oldDepartment));
        flushAndClear();
        User loaded = reload(account);
        assertEquals(Set.of("STAFF_VIEW", "PAYROLL_SELF"), PermissionPolicy.effective(loaded));

        loaded.getStaff().setDepartment(entityManager.find(Department.class, newDepartment.getId()));
        flushAndClear();
        assertEquals(Set.of("STAFF_VIEW", "PAYROLL_SELF"), PermissionPolicy.effective(reload(account)));
        entityManager.find(Department.class, newDepartment.getId()).setIsDeleted(true);
        flushAndClear();
        assertEquals(Set.of("STAFF_VIEW", "PAYROLL_SELF"), PermissionPolicy.effective(reload(account)));
        assertEquals(Map.of("PAYROLL_SELF", "ALLOW"), reload(account).getPermissionOverrides());
        roleRepository.findById(group.getId()).orElseThrow().setIsDeleted(true);
        flushAndClear();
        assertEquals(Set.of("PAYROLL_SELF"), PermissionPolicy.effective(reload(account)));
    }

    @Test
    void deletingStaffPreservesRoleRightsButDeletedRoleMembershipRemovesThem() {
        Role group = role("STAFF_VIEW");
        User account = account(group);
        account.setStaff(staff(department()));
        account.getStaff().setIsDeleted(true);
        flushAndClear();
        assertEquals(Set.of("STAFF_VIEW"), PermissionPolicy.effective(reload(account)));
        reload(account).getUserRoles().iterator().next().setIsDeleted(true);
        flushAndClear();
        assertEquals(Set.of(), PermissionPolicy.effective(reload(account)));
    }

    @Test
    void adminCanCreateConfigureAndDeleteAnUnassignedCustomGroup() throws Exception {
        String code = token();
        JsonNode created = admin(json(post("/api/access-control/roles"),
                Map.of("name", code.toLowerCase(), "description", "Nhóm kiểm thử phân quyền",
                        "permissions", Set.of("STAFF_VIEW"))), 200);
        UUID id = UUID.fromString(created.path("id").asText());
        assertEquals(code, created.path("name").asText());
        assertFalse(created.path("system").asBoolean());
        assertEquals(Set.of("STAFF_VIEW"), strings(created.path("permissions")));
        admin(json(put("/api/access-control/roles/" + id + "/permissions"),
                Map.of("permissions", Set.of("STAFF_EXPORT"))), 200);
        assertEquals(Set.of("STAFF_EXPORT"), roleRepository.findById(id).orElseThrow().getPermissions());
        admin(delete("/api/access-control/roles/" + id), 200);
        flushAndClear();
        assertTrue(roleRepository.findById(id).orElseThrow().getIsDeleted());
    }

    @Test
    void assignedGroupsAndAdminCannotBeDeleted() throws Exception {
        Role group = role("STAFF_VIEW");
        account(group);
        entityManager.flush();
        admin(delete("/api/access-control/roles/" + group.getId()), 400);
        Role system = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        admin(delete("/api/access-control/roles/" + system.getId()), 400);
        entityManager.clear();
        assertTrue(PermissionPolicy.active(roleRepository.findById(group.getId()).orElseThrow()));
        assertTrue(PermissionPolicy.active(roleRepository.findById(system.getId()).orElseThrow()));
    }

    @Test
    void invalidPermissionEffectOrGroupIsRejectedBeforeAnyAccountChanges() throws Exception {
        Role original = role("STAFF_VIEW");
        Role replacement = role("STAFF_EXPORT");
        User account = account(original);
        account.setPermissionOverrides(new HashMap<>(Map.of("PAYROLL_SELF", "ALLOW")));
        entityManager.flush();
        List<Map<String, Object>> requests = List.of(
                Map.of("roleIds", Set.of(replacement.getId()), "permissionOverrides", Map.of("UNKNOWN_PERMISSION", "ALLOW")),
                Map.of("roleIds", Set.of(replacement.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "INVALID")),
                Map.of("roleIds", Set.of(UUID.randomUUID()), "permissionOverrides", Map.of()),
                Map.of("roleIds", Set.of(replacement.getId())));
        for (Map<String, Object> request : requests) {
            admin(json(put("/api/access-control/users/" + account.getId()), request), 400);
            entityManager.clear();
            User unchanged = reload(account);
            assertEquals(Set.of(original.getId()), roleIds(unchanged));
            assertEquals(Map.of("PAYROLL_SELF", "ALLOW"), unchanged.getPermissionOverrides());
        }
    }

    @Test
    void unknownPermissionsDoNotReplaceExistingRolePermissions() throws Exception {
        Role group = role("STAFF_VIEW");
        entityManager.flush();
        admin(json(put("/api/access-control/roles/" + group.getId() + "/permissions"),
                Map.of("permissions", Set.of("INVALID"))), 400);
        entityManager.clear();
        assertEquals(Set.of("STAFF_VIEW"), roleRepository.findById(group.getId()).orElseThrow().getPermissions());
    }

    @Test
    void permissionManagementExposesOnlyRolesAndRemovedDepartmentEndpointReturnsNotFound() throws Exception {
        Department department = department();
        entityManager.flush();
        JsonNode groups = admin(get("/api/access-control/groups"), 200);
        assertTrue(groups.path("roles").isArray());
        assertFalse(groups.has("departments"));
        mvc.perform(json(put("/api/access-control/departments/" + department.getId() + "/permissions"),
                Map.of("permissions", Set.of("PAYROLL_VIEW")))
                .with(user("integration-admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordinaryAccountUpdateCannotOverwriteGroupPermissionsOrInjectAccountOverrides() throws Exception {
        Role group = role("STAFF_VIEW", "STAFF_EXPORT");
        User account = account(group);
        account.setPermissionOverrides(new HashMap<>(Map.of("STAFF_EXPORT", "DENY")));
        entityManager.flush();
        Map<String, Object> forgedRole = Map.of("id", group.getId(), "name", "ROLE_ADMIN",
                "permissionsConfigured", false, "permissions", Permission.codes());
        admin(json(put("/api/users/" + account.getId()), Map.of(
                "username", account.getUsername(), "active", true, "roles", List.of(forgedRole),
                "permissions", Permission.codes(), "permissionOverrides", Map.of("PAYROLL_VIEW", "ALLOW"))), 200);
        flushAndClear();
        User unchanged = reload(account);
        assertFalse(PermissionPolicy.isAdmin(unchanged));
        assertEquals(Set.of(group.getId()), roleIds(unchanged));
        assertEquals(Map.of("STAFF_EXPORT", "DENY"), unchanged.getPermissionOverrides());
        assertEquals(Set.of("STAFF_VIEW"), PermissionPolicy.effective(unchanged));
        Role unchangedGroup = roleRepository.findById(group.getId()).orElseThrow();
        assertEquals(group.getName(), unchangedGroup.getName());
        assertTrue(unchangedGroup.getPermissionsConfigured());
        assertEquals(Set.of("STAFF_VIEW", "STAFF_EXPORT"), unchangedGroup.getPermissions());
    }

    @Test
    void evenHrManagerCannotGrantPermissionsOrManageAccountsOrReadTheRoleList() throws Exception {
        UUID id = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>(List.of(
                get("/api/access-control/catalog"), get("/api/access-control/groups"),
                get("/api/access-control/users/" + id), get("/api/roles"),
                get("/api/users/unpaginated"), get("/api/users/" + id),
                json(post("/api/users/paging"), Map.of()),
                json(post("/api/users"), Map.of("username", "forbidden", "password", "test-only")),
                json(put("/api/users/" + id), Map.of("username", "forbidden")), delete("/api/users/" + id),
                json(post("/api/access-control/roles"), Map.of("name", "FORBIDDEN", "description", "Forbidden", "permissions", Set.of())),
                json(put("/api/access-control/users/" + id), Map.of("roleIds", Set.of(), "permissionOverrides", Map.of("STAFF_VIEW", "ALLOW"))),
                json(put("/api/access-control/roles/" + id + "/permissions"), Map.of("permissions", Set.of())),
                delete("/api/access-control/roles/" + id)));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.with(user("non-admin").authorities(new SimpleGrantedAuthority("HR_MANAGER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anExistingJwtUsesTheCurrentGroupPermissionsWithoutLoggingInAgain() throws Exception {
        Role group = role("STAFF_VIEW");
        User account = account(group);
        entityManager.flush();
        String jwt = tokens.generateAccessToken(account);
        entityManager.clear();
        jwtStaffList(jwt, 200);

        admin(json(put("/api/access-control/roles/" + group.getId() + "/permissions"),
                Map.of("permissions", Set.of())), 200);
        flushAndClear();
        jwtStaffList(jwt, 403);

        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(group.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "ALLOW"))), 200);
        flushAndClear();
        jwtStaffList(jwt, 200);

        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(group.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "DENY"))), 200);
        flushAndClear();
        jwtStaffList(jwt, 403);
    }

    @Test
    void disablingAnAccountImmediatelyRejectsItsExistingJwt() throws Exception {
        User account = account(role("STAFF_VIEW"));
        entityManager.flush();
        String jwt = tokens.generateAccessToken(account);
        entityManager.clear();
        jwtStaffList(jwt, 200);
        reload(account).setActive(false);
        flushAndClear();
        jwtStaffList(jwt, 401);
    }

    @Test
    void adminAlwaysHasAllPermissionsAndCannotBeRestrictedThroughAccessManagement() throws Exception {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        User administrator = account(adminRole);
        administrator.setPermissionOverrides(new HashMap<>(Map.of("STAFF_VIEW", "DENY")));
        entityManager.flush();
        assertEquals(Permission.codes(), PermissionPolicy.effective(administrator));
        admin(json(put("/api/access-control/roles/" + adminRole.getId() + "/permissions"),
                Map.of("permissions", Set.of())), 400);
        admin(json(put("/api/access-control/users/" + administrator.getId()),
                Map.of("roleIds", Set.of(adminRole.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "DENY"))), 400);
        entityManager.clear();
        assertEquals(Permission.codes(), PermissionPolicy.effective(reload(administrator)));
    }

    @Test
    void adminGroupCannotBeGrantedTogetherWithAccountRestrictions() throws Exception {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        Role original = role("STAFF_VIEW");
        User account = account(original);
        entityManager.flush();
        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(adminRole.getId()), "permissionOverrides", Map.of("STAFF_VIEW", "DENY"))), 400);
        entityManager.clear();
        assertEquals(Set.of(original.getId()), roleIds(reload(account)));
        assertFalse(PermissionPolicy.isAdmin(reload(account)));
    }

    @Test
    void lastActiveAdminCannotBeDisabled() throws Exception {
        User administrator = soleAdmin();
        admin(json(put("/api/users/" + administrator.getId()),
                Map.of("username", administrator.getUsername(), "active", false)), 400);
        entityManager.clear();
        assertTrue(PermissionPolicy.isAdmin(reload(administrator)));
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        User administrator = soleAdmin();
        admin(json(put("/api/users/" + administrator.getId()),
                Map.of("username", administrator.getUsername(), "active", true, "roles", List.of())), 400);
        entityManager.clear();
        assertTrue(PermissionPolicy.isAdmin(reload(administrator)));
    }

    @Test
    void lastActiveAdminCannotBeDeleted() throws Exception {
        User administrator = soleAdmin();
        admin(delete("/api/users/" + administrator.getId()), 400);
        entityManager.clear();
        assertTrue(PermissionPolicy.isAdmin(reload(administrator)));
        assertFalse(Boolean.TRUE.equals(reload(administrator).getIsDeleted()));
    }

    @Test
    void anAdminCanBeDisabledWhenAnotherActiveAdminRemains() throws Exception {
        User first = soleAdmin();
        User second = account(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        entityManager.flush();
        admin(json(put("/api/users/" + first.getId()),
                Map.of("username", first.getUsername(), "active", false)), 200);
        flushAndClear();
        assertFalse(reload(first).getActive());
        assertTrue(PermissionPolicy.isAdmin(reload(second)));
    }

    private JsonNode admin(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        byte[] body = mvc.perform(request.with(user("integration-admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
        return mapper.readTree(body).path("data");
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private void jwtStaffList(String token, int expectedStatus) throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/staffs").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private Role role(String... permissions) {
        Role role = new Role();
        role.setName(token());
        role.setDescription("Nhóm kiểm thử");
        role.setPermissionsConfigured(true);
        role.setPermissions(new HashSet<>(List.of(permissions)));
        return roleRepository.save(role);
    }

    private Department department() {
        Department department = new Department();
        department.setCode(token());
        department.setName("Phòng kiểm thử");
        entityManager.persist(department);
        return department;
    }

    private Staff staff(Department department) {
        Staff staff = new Staff();
        staff.setStaffCode(token());
        staff.setDisplayName("Nhân viên kiểm thử phân quyền");
        staff.setDepartment(department);
        entityManager.persist(staff);
        return staff;
    }

    private User account(Role... roles) {
        User account = new User();
        account.setUsername(token());
        account.setPassword("unused-test-password-hash");
        account.setActive(true);
        for (Role role : roles) {
            UserRole link = new UserRole();
            link.setRole(role);
            link.setUser(account);
            account.getUserRoles().add(link);
        }
        return userRepository.save(account);
    }

    private User soleAdmin() {
        userRepository.findAll().stream().filter(PermissionPolicy::isAdmin).forEach(user -> user.setActive(false));
        User administrator = account(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        flushAndClear();
        return reload(administrator);
    }

    private Set<UUID> roleIds(User user) {
        Set<UUID> ids = new HashSet<>();
        user.getUserRoles().forEach(link -> ids.add(link.getRole().getId()));
        return ids;
    }

    private Set<String> strings(JsonNode values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private User reload(User user) { return userRepository.findById(user.getId()).orElseThrow(); }
    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    private String token() { return "ACL_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(); }
}
