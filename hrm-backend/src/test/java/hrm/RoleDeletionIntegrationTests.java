package hrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.config.DatabaseInitializer;
import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.RoleRepository;
import hrm.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class RoleDeletionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired DatabaseInitializer initializer;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unusedLegacyRoleCanBeDeletedAndStartupDoesNotRestoreIt() throws Exception {
        Role legacy = roles.findByName("HR_RECRUITMENT").orElseThrow();
        remove(legacy, 200);
        flushAndClear();
        assertTrue(roles.findById(legacy.getId()).orElseThrow().getIsDeleted());
        initializer.run();
        flushAndClear();
        assertTrue(roles.findById(legacy.getId()).orElseThrow().getIsDeleted());
        byte[] body = mvc.perform(get("/api/access-control/groups").with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertFalse(mapper.readTree(body).path("data").path("roles").findValuesAsText("id").contains(legacy.getId().toString()));
    }

    @Test
    void adminRoleRemainsProtected() throws Exception {
        Role core = roles.findByName("ROLE_ADMIN").orElseThrow();
        remove(core, 400);
        assertFalse(Boolean.TRUE.equals(roles.findById(core.getId()).orElseThrow().getIsDeleted()));
    }

    @Test
    void assignedLegacyRoleIsProtectedIncludingLockedAccounts() throws Exception {
        Role legacy = roles.findByName("HR_MANAGER").orElseThrow();
        User account = account(legacy);
        account.setActive(false);
        flushAndClear();
        remove(legacy, 400);
        assertFalse(Boolean.TRUE.equals(roles.findById(legacy.getId()).orElseThrow().getIsDeleted()));
    }

    @Test
    void deletedAccountDoesNotBlockRoleDeletionAndItsHistoricalMembershipRemains() throws Exception {
        Role custom = customRole();
        User account = account(custom);
        account.setIsDeleted(true);
        flushAndClear();
        remove(custom, 200);
        flushAndClear();
        assertTrue(roles.findById(custom.getId()).orElseThrow().getIsDeleted());
        assertEquals(custom.getId(), users.findById(account.getId()).orElseThrow().getUserRoles().iterator().next().getRole().getId());
    }

    @Test
    void roleCanBeDeletedAfterAccountMembershipIsRemoved() throws Exception {
        Role custom = customRole();
        User account = account(custom);
        flushAndClear();
        mvc.perform(put("/api/access-control/users/" + account.getId()).with(user("admin").authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("roleIds", Set.of(), "permissionOverrides", Map.of()))))
                .andExpect(status().isOk());
        remove(custom, 200);
        flushAndClear();
        assertTrue(roles.findById(custom.getId()).orElseThrow().getIsDeleted());
        assertTrue(users.findById(account.getId()).orElseThrow().getUserRoles().isEmpty());
    }

    @Test
    void deletedRoleCannotBeAssignedThroughEitherAccountEndpoint() throws Exception {
        Role custom = customRole();
        User account = account();
        flushAndClear();
        remove(custom, 200);
        mvc.perform(put("/api/access-control/users/" + account.getId()).with(user("admin").authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("roleIds", Set.of(custom.getId()), "permissionOverrides", Map.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/" + account.getId()).with(user("admin").authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("username", account.getUsername(), "roles", List.of(Map.of("id", custom.getId()))))))
                .andExpect(status().isBadRequest());
        entityManager.clear();
        assertTrue(users.findById(account.getId()).orElseThrow().getUserRoles().isEmpty());
    }

    private void remove(Role role, int expectedStatus) throws Exception {
        mvc.perform(delete("/api/access-control/roles/" + role.getId()).with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().is(expectedStatus));
    }

    private Role customRole() {
        Role role = new Role();
        role.setName("DELETE_TEST_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Vai trò kiểm thử xóa");
        return roles.save(role);
    }

    private User account(Role... memberships) {
        User account = new User();
        account.setUsername("delete-test-" + UUID.randomUUID());
        account.setPassword("unused-test-hash");
        account.setActive(true);
        for (Role role : memberships) {
            UserRole link = new UserRole();
            link.setRole(role);
            link.setUser(account);
            account.getUserRoles().add(link);
        }
        return users.save(account);
    }

    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
}
