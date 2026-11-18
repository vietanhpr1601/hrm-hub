package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.config.DatabaseInitializer;
import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.RoleRepository;
import hrm.repository.UserRepository;
import hrm.security.PermissionPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated, rolled-back integration data; role removal never deletes accounts. */
@HrmIntegrationTest
class UserRoleDeletionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired DatabaseInitializer initializer;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unusedUserRoleCanBeDeletedAndTwoInitializerRunsKeepItsTombstoneHidden() throws Exception {
        Role basic = roles.findByName("ROLE_USER").orElseThrow();
        basic.setPermissionsConfigured(false);
        basic.setPermissions(new HashSet<>(Set.of("STAFF_VIEW")));
        flushAndClear();
        long accountCount = users.count();
        long roleCount = roles.count();
        admin(delete("/api/access-control/roles/" + basic.getId()), 200);
        flushAndClear();
        assertDeletedAndHidden(basic.getId());

        for (int run = 0; run < 2; run++) {
            initializer.run();
            flushAndClear();
            assertDeletedAndHidden(basic.getId());
            Role deleted = roles.findByName("ROLE_USER").orElseThrow();
            assertEquals(basic.getId(), deleted.getId());
            assertFalse(deleted.getPermissionsConfigured(), "Deleted roles must not be reconfigured during startup");
            assertEquals(Set.of("STAFF_VIEW"), deleted.getPermissions());
            assertEquals(accountCount, users.count());
            assertEquals(roleCount, roles.count());
        }
    }

    @Test
    void lockedAccountBlocksUserRoleDeletionUntilOnlyItsMembershipIsRemoved() throws Exception {
        Role basic = roles.findByName("ROLE_USER").orElseThrow();
        User account = account(basic);
        account.setActive(false);
        flushAndClear();
        long accountCount = users.count();
        JsonNode denied = admin(delete("/api/access-control/roles/" + basic.getId()), 400);
        assertTrue(denied.path("message").asText().contains("đang được gán"));
        assertFalse(Boolean.TRUE.equals(roles.findById(basic.getId()).orElseThrow().getIsDeleted()));

        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(), "permissionOverrides", Map.of())), 200);
        admin(delete("/api/access-control/roles/" + basic.getId()), 200);
        flushAndClear();
        User unchangedAccount = users.findById(account.getId()).orElseThrow();
        assertEquals(account.getUsername(), unchangedAccount.getUsername());
        assertFalse(unchangedAccount.getActive());
        assertFalse(Boolean.TRUE.equals(unchangedAccount.getIsDeleted()));
        assertTrue(unchangedAccount.getUserRoles().isEmpty());
        assertEquals(accountCount, users.count());
        assertDeletedAndHidden(basic.getId());
    }

    @Test
    void deletedUserRoleCannotBeAssignedThroughAccessOrAccountCreationAndUpdate() throws Exception {
        Role basic = roles.findByName("ROLE_USER").orElseThrow();
        User account = account();
        flushAndClear();
        long accountCount = users.count();
        admin(delete("/api/access-control/roles/" + basic.getId()), 200);
        admin(json(put("/api/access-control/users/" + account.getId()),
                Map.of("roleIds", Set.of(basic.getId()), "permissionOverrides", Map.of())), 400);
        admin(json(put("/api/users/" + account.getId()), Map.of("username", account.getUsername(),
                "roles", List.of(Map.of("id", basic.getId())))), 400);
        String newUsername = "deleted-user-role-" + UUID.randomUUID();
        admin(json(post("/api/users"), Map.of("username", newUsername, "password", "IsolatedTestPassword123!",
                "roles", List.of(Map.of("id", basic.getId())))), 400);
        entityManager.clear();
        assertEquals(accountCount, users.count());
        assertTrue(users.findByUsername(newUsername).isEmpty());
        User unchangedAccount = users.findById(account.getId()).orElseThrow();
        assertTrue(unchangedAccount.getUserRoles().isEmpty());
        assertFalse(Boolean.TRUE.equals(unchangedAccount.getIsDeleted()));
    }

    @Test
    void absentUserRoleIsStillSeededWithDefaultsAndExplicitLiveConfigurationIsPreserved() throws Exception {
        Role previous = roles.findByName("ROLE_USER").orElseThrow();
        // Only removes an unused seed in this isolated transaction to simulate an older/new database.
        entityManager.remove(previous);
        flushAndClear();
        long accountCount = users.count();
        initializer.run();
        flushAndClear();
        Role seeded = roles.findByName("ROLE_USER").orElseThrow();
        assertFalse(Boolean.TRUE.equals(seeded.getIsDeleted()));
        assertTrue(seeded.getPermissionsConfigured());
        assertEquals(Set.of("ATTENDANCE_SELF", "LEAVE_SELF", "PAYROLL_SELF", "NOTIFICATION_VIEW"), PermissionPolicy.rolePermissions(seeded));
        seeded.setPermissions(new HashSet<>());
        flushAndClear();
        initializer.run();
        flushAndClear();
        Role kept = roles.findByName("ROLE_USER").orElseThrow();
        assertEquals(seeded.getId(), kept.getId());
        assertTrue(kept.getPermissionsConfigured());
        assertTrue(kept.getPermissions().isEmpty());
        assertEquals(accountCount, users.count());
    }

    private void assertDeletedAndHidden(UUID roleId) throws Exception {
        assertTrue(roles.findById(roleId).orElseThrow().getIsDeleted());
        JsonNode groups = admin(get("/api/access-control/groups"), 200).path("data").path("roles");
        JsonNode roleOptions = admin(get("/api/roles"), 200).path("data");
        assertFalse(groups.findValuesAsText("id").contains(roleId.toString()));
        assertFalse(roleOptions.findValuesAsText("id").contains(roleId.toString()));
    }

    private User account(Role... memberships) {
        User account = new User();
        account.setUsername("user-role-test-" + UUID.randomUUID());
        account.setPassword("unused-isolated-test-hash");
        for (Role role : memberships) {
            UserRole membership = new UserRole();
            membership.setRole(role);
            membership.setUser(account);
            account.getUserRoles().add(membership);
        }
        return users.save(account);
    }

    private JsonNode admin(MockHttpServletRequestBuilder request, int expected) throws Exception {
        byte[] response = mvc.perform(request.with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray();
        return mapper.readTree(response);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
}
