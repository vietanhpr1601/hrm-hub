package hrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.*;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import hrm.security.PermissionPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Personnel organization changes are independent of role and individual permissions. */
@HrmIntegrationTest
class StaffPermissionMembershipTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void staffEditorCanMoveOwnAccountWithoutGainingAdditionalPermissions() throws Exception {
        Staff staff = staff(department());
        Department destination = department();
        User editor = account(staff, "STAFF_UPDATE");
        String token = token(editor);
        request(get("/api/payrolls"), token, 403);
        request(changeDepartment(staff, destination), token, 200);
        em.flush();
        em.clear();
        assertEquals(destination.getId(), em.find(Staff.class, staff.getId()).getDepartment().getId());
        request(get("/api/payrolls"), token, 403);
        assertEquals(Set.of("STAFF_UPDATE"), PermissionPolicy.effective(users.findById(editor.getId()).orElseThrow()));
    }

    @Test
    void staffEditorCanMoveAnotherLinkedAccountWithoutChangingItsRolesOrOverrides() throws Exception {
        Staff target = staff(department());
        User employee = account(target, "PAYROLL_VIEW", "STAFF_EXPORT");
        Map<String, String> overrides = Map.of("PAYROLL_VIEW", "DENY", "STAFF_VIEW", "ALLOW");
        employee.setPermissionOverrides(new HashMap<>(overrides));
        Department destination = department();
        User editor = account(null, "STAFF_UPDATE");
        request(changeDepartment(target, destination), token(editor), 200);
        em.flush();
        em.clear();
        User updated = users.findById(employee.getId()).orElseThrow();
        assertEquals(destination.getId(), updated.getStaff().getDepartment().getId());
        assertEquals(overrides, updated.getPermissionOverrides());
        assertEquals(Set.of("STAFF_EXPORT", "STAFF_VIEW"), PermissionPolicy.effective(updated));
        assertEquals(Set.of("PAYROLL_VIEW", "STAFF_EXPORT"), PermissionPolicy.groupPermissions(updated));
    }

    @Test
    void movingLinkedStaffPreservesPreviouslyIssuedJwtRolePermissions() throws Exception {
        Department oldDepartment = department();
        Department newDepartment = department();
        Staff staff = staff(oldDepartment);
        User employee = account(staff, "PAYROLL_VIEW");
        User editor = account(null, "STAFF_UPDATE");
        String employeeToken = token(employee);
        request(get("/api/payrolls"), employeeToken, 200);
        request(changeDepartment(staff, newDepartment), token(users.findById(editor.getId()).orElseThrow()), 200);
        em.flush();
        em.clear();
        request(get("/api/payrolls"), employeeToken, 200);
    }

    @Test
    void removingDepartmentFromLinkedEmployeePreservesIndividualPermissions() throws Exception {
        Staff staff = staff(department());
        User employee = account(staff);
        employee.setPermissionOverrides(new HashMap<>(Map.of("PAYROLL_VIEW", "ALLOW")));
        User editor = account(null, "STAFF_UPDATE");
        request(changeDepartment(staff, null), token(editor), 200);
        em.flush();
        em.clear();
        User updated = users.findById(employee.getId()).orElseThrow();
        assertNull(updated.getStaff().getDepartment());
        assertEquals(Set.of("PAYROLL_VIEW"), PermissionPolicy.effective(updated));
        assertEquals(Map.of("PAYROLL_VIEW", "ALLOW"), updated.getPermissionOverrides());
    }

    @Test
    void staffEditorCanMoveAnEmployeeWithoutAnAccount() throws Exception {
        Staff staff = staff(department());
        Department destination = department();
        User editor = account(null, "STAFF_UPDATE");
        request(changeDepartment(staff, destination), token(editor), 200);
        assertEquals(destination.getId(), em.find(Staff.class, staff.getId()).getDepartment().getId());
    }

    @Test
    void staffDeleteDoesNotRevokeLinkedAccountsRoleOrIndividualRights() throws Exception {
        Staff staff = staff(department());
        User employee = account(staff, "PAYROLL_VIEW");
        employee.setPermissionOverrides(new HashMap<>(Map.of("STAFF_VIEW", "ALLOW")));
        User editor = account(null, "STAFF_DELETE");
        String employeeToken = token(employee);
        request(get("/api/payrolls"), employeeToken, 200);
        request(delete("/api/staffs/" + staff.getId()), token(users.findById(editor.getId()).orElseThrow()), 200);
        em.flush();
        em.clear();
        assertTrue(em.find(Staff.class, staff.getId()).getIsDeleted());
        User updated = users.findById(employee.getId()).orElseThrow();
        assertEquals(Set.of("PAYROLL_VIEW", "STAFF_VIEW"), PermissionPolicy.effective(updated));
        assertEquals(Map.of("STAFF_VIEW", "ALLOW"), updated.getPermissionOverrides());
        request(get("/api/payrolls"), employeeToken, 200);
    }

    @Test
    void staffDeletePreservesIndividualDenialsOnLinkedAccount() throws Exception {
        Staff staff = staff(department());
        User employee = account(staff, "PAYROLL_VIEW");
        employee.setPermissionOverrides(new HashMap<>(Map.of("PAYROLL_VIEW", "DENY")));
        User editor = account(null, "STAFF_DELETE");
        request(delete("/api/staffs/" + staff.getId()), token(editor), 200);
        assertTrue(em.find(Staff.class, staff.getId()).getIsDeleted());
        User updated = users.findById(employee.getId()).orElseThrow();
        assertEquals(Map.of("PAYROLL_VIEW", "DENY"), updated.getPermissionOverrides());
        assertTrue(PermissionPolicy.effective(updated).isEmpty());
    }

    @Test
    void staffCreateIgnoresSpoofedIdAndCannotOverwriteExistingEmployeeMembership() throws Exception {
        Staff existing = staff(department());
        account(existing);
        Department destination = department();
        User creator = account(null, "STAFF_CREATE");
        String originalName = existing.getDisplayName();
        Map<String, Object> body = Map.of("id", existing.getId(), "staffCode", "NEW_" + UUID.randomUUID(),
                "displayName", "New employee", "departmentId", destination.getId());
        request(post("/api/staffs").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)), token(creator), 200);
        em.clear();
        Staff unchanged = em.find(Staff.class, existing.getId());
        assertEquals(originalName, unchanged.getDisplayName());
        assertNotEquals(destination.getId(), unchanged.getDepartment().getId());
    }

    @Test
    void staffUpdateCannotReactivateDeletedEmployee() throws Exception {
        Staff staff = staff(department());
        account(staff);
        staff.setIsDeleted(true);
        User editor = account(null, "STAFF_UPDATE");
        request(changeDepartment(staff, staff.getDepartment()), token(editor), 404);
        assertTrue(em.find(Staff.class, staff.getId()).getIsDeleted());
    }

    private MockHttpServletRequestBuilder changeDepartment(Staff staff, Department department) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("staffCode", staff.getStaffCode());
        body.put("displayName", staff.getDisplayName());
        body.put("departmentId", department == null ? null : department.getId());
        return put("/api/staffs/" + staff.getId()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    private void request(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().is(expected));
        SecurityContextHolder.clearContext();
    }

    private String token(User user) {
        em.flush();
        String token = tokens.generateAccessToken(user);
        em.clear();
        return token;
    }

    private Department department() {
        Department department = new Department();
        department.setCode("DEP_" + UUID.randomUUID());
        department.setName("Department membership test");
        em.persist(department);
        return department;
    }

    private Staff staff(Department department) {
        Staff staff = new Staff();
        staff.setStaffCode("MEM_" + UUID.randomUUID());
        staff.setDisplayName("Employee membership test");
        staff.setDepartment(department);
        em.persist(staff);
        return staff;
    }

    private User account(Staff staff, String... permissions) {
        Role role = new Role();
        role.setName("MEMBERSHIP_" + UUID.randomUUID());
        role.setDescription("Membership test role");
        role.setPermissionsConfigured(true);
        role.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        em.persist(role);
        User user = new User();
        user.setUsername("membership-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setStaff(staff);
        UserRole link = new UserRole();
        link.setUser(user);
        link.setRole(role);
        user.getUserRoles().add(link);
        em.persist(user);
        return user;
    }
}
