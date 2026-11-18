package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.config.DatabaseInitializer;
import hrm.dto.request.UserDto;
import hrm.enums.NotificationType;
import hrm.model.Notification;
import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.NotificationRepository;
import hrm.repository.RoleRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import hrm.security.Permission;
import hrm.security.PermissionPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class HomePermissionIntegrationTests {
    private static final Set<String> HOME_PERMISSIONS = Set.of("NOTIFICATION_VIEW");
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired NotificationRepository notifications;
    @Autowired JwtTokenProvider tokens;
    @Autowired DatabaseInitializer initializer;
    @Autowired EntityManager entityManager;
    @Value("${app.admin.username}") String adminUsername;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void catalogOmitsRetiredBulletinAndKeepsPersonalNotificationPermission() throws Exception {
        JsonNode catalog = admin(get("/api/access-control/catalog")).path("data");
        assertFalse(Permission.codes().contains("BULLETIN_VIEW"));
        for (JsonNode entry : catalog) {
            assertNotEquals("BULLETIN_VIEW", entry.path("code").asText());
            assertNotEquals("Bảng tin", entry.path("module").asText());
        }
        JsonNode notification = findCode(catalog, "NOTIFICATION_VIEW");
        assertEquals("Thông báo", notification.path("module").asText());
        assertEquals("Xem thông báo của mình", notification.path("label").asText());
        assertFalse(notification.path("adminOnly").asBoolean());
    }

    @Test
    void roleGrantAndRevokeTakeEffectWithTheSameAccessToken() throws Exception {
        Role group = role(Set.of());
        User employee = account(group);
        String token = tokens.generateAccessToken(employee);
        call(get("/api/notifications"), token, 403);
        updateRole(group, HOME_PERMISSIONS);
        call(get("/api/notifications"), token, 200);
        assertEquals(HOME_PERMISSIONS, effective(employee));
        updateRole(group, Set.of());
        call(get("/api/notifications"), token, 403);
        assertTrue(effective(employee).isEmpty());
    }

    @Test
    void accountDenyOverridesRoleAndAccountAllowWorksWithoutRoleGrant() throws Exception {
        Role group = role(HOME_PERMISSIONS);
        User employee = account(group);
        String token = tokens.generateAccessToken(employee);
        updateAccount(employee, group, Map.of("NOTIFICATION_VIEW", "DENY"));
        call(get("/api/notifications"), token, 403);
        assertTrue(effective(employee).isEmpty());
        updateAccount(employee, group, Map.of());
        call(get("/api/notifications"), token, 200);
        assertEquals(HOME_PERMISSIONS, effective(employee));
        updateRole(group, Set.of());
        updateAccount(employee, group, Map.of("NOTIFICATION_VIEW", "ALLOW"));
        call(get("/api/notifications"), token, 200);
        assertEquals(HOME_PERMISSIONS, effective(employee));
    }

    @Test
    void noNotificationGrantBlocksAllFourEndpointsWithoutChangingReadState() throws Exception {
        User employee = account(role(Set.of("BULLETIN_VIEW")));
        employee.getPermissionOverrides().put("BULLETIN_VIEW", "ALLOW");
        Notification message = new Notification();
        message.setRecipient(employee);
        message.setType(NotificationType.ATTENDANCE_APPROVED);
        message.setTitle("Thông báo kiểm thử");
        message.setMessage("Chấm công đã được duyệt");
        message.setReferenceId(UUID.randomUUID());
        message.setReferenceDate(LocalDate.of(2026, 10, 8));
        notifications.saveAndFlush(message);
        String token = tokens.generateAccessToken(employee);
        call(get("/api/notifications"), token, 403);
        call(get("/api/notifications/unread-count"), token, 403);
        call(put("/api/notifications/" + message.getId() + "/read"), token, 403);
        call(put("/api/notifications/read-all"), token, 403);
        flush();
        assertNull(notifications.findById(message.getId()).orElseThrow().getReadAt());
        assertEquals(1, notifications.countByRecipientIdAndReadAtIsNull(employee.getId()));
    }

    @Test
    void onlyUnconfiguredBuiltInRolesReceiveNotificationDefaults() {
        for (String name : new String[]{"ROLE_USER", "HR_MANAGER", "HR_EMPLOYEE", "HR_RECRUITMENT",
                "HR_COMPENSATION_BENEFIT", "HR_TIMEKEEPING_MANAGER"}) {
            Role role = new Role();
            role.setName(name);
            role.setPermissionsConfigured(false);
            assertTrue(PermissionPolicy.rolePermissions(role).containsAll(HOME_PERMISSIONS), name);
            assertFalse(PermissionPolicy.rolePermissions(role).contains("BULLETIN_VIEW"), name);
            role.setPermissionsConfigured(true);
            role.setPermissions(new HashSet<>(Set.of("ATTENDANCE_SELF")));
            assertEquals(Set.of("ATTENDANCE_SELF"), PermissionPolicy.rolePermissions(role), name);
        }
        assertTrue(PermissionPolicy.defaults("CUSTOM_UNCONFIGURED_ROLE").isEmpty());
        assertTrue(PermissionPolicy.defaults("ROLE_ADMIN").containsAll(HOME_PERMISSIONS));
        assertFalse(PermissionPolicy.defaults("ROLE_ADMIN").contains("BULLETIN_VIEW"));
    }

    @Test
    void storedRetiredGrantsAndOverridesAreSuppressedWithoutChangingOtherPermissions() throws Exception {
        for (String effect : new String[]{"ALLOW", "DENY"}) {
            Role group = role(Set.of("BULLETIN_VIEW", "NOTIFICATION_VIEW"));
            User employee = account(group);
            employee.setPermissionOverrides(new HashMap<>(Map.of("BULLETIN_VIEW", effect, "NOTIFICATION_VIEW", "DENY")));
            flush();

            JsonNode groups = admin(get("/api/access-control/groups")).path("data").path("roles");
            for (JsonNode entry : groups) {
                if (group.getId().toString().equals(entry.path("id").asText())) {
                    assertEquals(Set.of("NOTIFICATION_VIEW"), strings(entry.path("permissions")));
                }
            }
            JsonNode details = admin(get("/api/access-control/users/" + employee.getId())).path("data");
            assertEquals(Set.of("NOTIFICATION_VIEW"), strings(details.path("groupPermissions")));
            assertTrue(strings(details.path("permissions")).isEmpty());
            assertFalse(details.path("permissionOverrides").has("BULLETIN_VIEW"));
            assertEquals("DENY", details.path("permissionOverrides").path("NOTIFICATION_VIEW").asText());
            User loaded = users.findById(employee.getId()).orElseThrow();
            assertEquals(Map.of("NOTIFICATION_VIEW", "DENY"), new UserDto(loaded).getPermissionOverrides());
            assertEquals(Map.of("BULLETIN_VIEW", effect, "NOTIFICATION_VIEW", "DENY"), loaded.getPermissionOverrides());
            assertEquals(Set.of("BULLETIN_VIEW", "NOTIFICATION_VIEW"), roles.findById(group.getId()).orElseThrow().getPermissions());
            call(get("/api/notifications"), tokens.generateAccessToken(loaded), 403);

            // The projected configuration can be submitted unchanged while editing other account settings.
            updateAccount(employee, group, mapper.convertValue(details.path("permissionOverrides"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
            assertTrue(effective(employee).isEmpty());
        }
    }

    @Test
    void newRetiredRoleAndAccountGrantsAreRejectedBeforeChangingExistingConfiguration() throws Exception {
        Role group = role(Set.of("NOTIFICATION_VIEW"));
        User employee = account(group);
        employee.setPermissionOverrides(new HashMap<>(Map.of("NOTIFICATION_VIEW", "DENY")));
        flush();
        String newRoleName = "RETIRED_" + UUID.randomUUID().toString().replace("-", "");
        admin(json(post("/api/access-control/roles"), Map.of("name", newRoleName,
                "description", "Nhóm kiểm thử quyền đã bỏ", "permissions", Set.of("BULLETIN_VIEW"))), 400);
        admin(json(put("/api/access-control/roles/" + group.getId() + "/permissions"),
                Map.of("permissions", Set.of("BULLETIN_VIEW"))), 400);
        for (String effect : new String[]{"ALLOW", "DENY"}) {
            admin(json(put("/api/access-control/users/" + employee.getId()), Map.of("roleIds", Set.of(),
                    "permissionOverrides", Map.of("BULLETIN_VIEW", effect))), 400);
        }
        flush();
        assertTrue(roles.findByName(newRoleName).isEmpty());
        assertEquals(Set.of("NOTIFICATION_VIEW"), roles.findById(group.getId()).orElseThrow().getPermissions());
        User unchanged = users.findById(employee.getId()).orElseThrow();
        assertEquals(Set.of(group.getId()), unchanged.getUserRoles().stream().map(link -> link.getRole().getId())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Map.of("NOTIFICATION_VIEW", "DENY"), unchanged.getPermissionOverrides());
        assertTrue(PermissionPolicy.effective(unchanged).isEmpty());
    }

    @Test
    void startupDoesNotAddHomeGrantsToExistingConfiguredRoles() throws Exception {
        Set<String> previousPermissions = Set.of("ATTENDANCE_SELF", "LEAVE_SELF", "PAYROLL_SELF");
        Role basic = roles.findByName("ROLE_USER").orElseThrow();
        basic.setPermissionsConfigured(true);
        basic.setPermissions(new HashSet<>(previousPermissions));
        Role custom = role(Set.of());
        User employee = account(basic);
        flush();
        initializer.run();
        flush();
        assertEquals(previousPermissions, PermissionPolicy.rolePermissions(roles.findById(basic.getId()).orElseThrow()));
        assertTrue(PermissionPolicy.rolePermissions(roles.findById(custom.getId()).orElseThrow()).isEmpty());
        call(get("/api/notifications"), tokens.generateAccessToken(employee), 403);
    }

    private JsonNode findCode(JsonNode values, String code) {
        for (JsonNode value : values) if (code.equals(value.path("code").asText())) return value;
        fail("Missing permission: " + code);
        return null;
    }

    private Role role(Set<String> permissions) {
        Role role = new Role();
        role.setName("HOME_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Vai trò kiểm thử Trang chủ");
        role.setPermissionsConfigured(true);
        role.setPermissions(new HashSet<>(permissions));
        return roles.saveAndFlush(role);
    }

    private User account(Role role) {
        User user = new User();
        user.setUsername("home-permission-" + UUID.randomUUID());
        user.setPassword("unused-test-password-hash");
        user.setActive(true);
        UserRole link = new UserRole();
        link.setUser(user);
        link.setRole(role);
        user.getUserRoles().add(link);
        return users.saveAndFlush(user);
    }

    private void updateRole(Role role, Set<String> permissions) throws Exception {
        admin(json(put("/api/access-control/roles/" + role.getId() + "/permissions"), Map.of("permissions", permissions)));
        flush();
    }

    private void updateAccount(User employee, Role role, Map<String, String> overrides) throws Exception {
        admin(json(put("/api/access-control/users/" + employee.getId()), Map.of("roleIds", Set.of(role.getId()), "permissionOverrides", overrides)));
        flush();
    }

    private Set<String> effective(User employee) throws Exception {
        return strings(admin(get("/api/access-control/users/" + employee.getId())).path("data").path("permissions"));
    }

    private Set<String> strings(JsonNode values) {
        Set<String> result = new HashSet<>();
        values.forEach(item -> result.add(item.asText()));
        return result;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private JsonNode admin(MockHttpServletRequestBuilder request) throws Exception {
        return admin(request, 200);
    }

    private JsonNode admin(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return call(request, tokens.generateAccessToken(users.findByUsername(adminUsername).orElseThrow()), expectedStatus);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        SecurityContextHolder.clearContext();
        return mapper.readTree(mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray());
    }

    private void flush() { entityManager.flush(); entityManager.clear(); }
}
