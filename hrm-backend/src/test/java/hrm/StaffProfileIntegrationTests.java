package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Department;
import hrm.model.Position;
import hrm.model.Staff;
import hrm.model.User;
import hrm.repository.DepartmentRepository;
import hrm.repository.PositionRepository;
import hrm.repository.StaffRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class StaffProfileIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired StaffRepository staffs;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void accountWithoutStaffPermissionsReadsOnlyOwnPersonalContactAndJobDetails() throws Exception {
        Department department = new Department();
        department.setCode("PROFILE_" + UUID.randomUUID());
        department.setName("Phòng nhân sự");
        departments.saveAndFlush(department);
        Position position = new Position();
        position.setCode("PROFILE_" + UUID.randomUUID());
        position.setName("Chuyên viên");
        positions.saveAndFlush(position);
        Staff own = staff();
        own.setDepartment(department);
        own.setPosition(position);
        own.setAvatarUrl("https://example.test/avatar.png");
        own.setBirthDate(LocalDate.of(1995, 5, 10));
        own.setGender("FEMALE");
        own.setEmail("personal@example.test");
        own.setPhoneNumber("0900000000");
        own.setCompanyEmail("work@example.test");
        own.setCurrentAddress("Địa chỉ liên hệ");
        own.setPermanentResidence("Địa chỉ thường trú");
        own.setCurrentResidence("Địa chỉ tạm trú");
        own.setWorkingStatus("WORKING");
        own.setStartDate(LocalDate.of(2025, 1, 2));
        own.setTaxCode("PRIVATE-TAX");
        own.setBankAccountNumber("PRIVATE-BANK");
        User employee = account(own);
        String token = tokens.generateAccessToken(employee);
        flush();

        JsonNode profile = call(get("/api/staffs/me"), token, 200).path("data");
        assertEquals(own.getId().toString(), profile.path("id").asText());
        assertEquals(own.getStaffCode(), profile.path("staffCode").asText());
        assertEquals(own.getDisplayName(), profile.path("displayName").asText());
        assertEquals("1995-05-10", profile.path("birthDate").asText());
        assertEquals("FEMALE", profile.path("gender").asText());
        assertEquals("personal@example.test", profile.path("email").asText());
        assertEquals("0900000000", profile.path("phoneNumber").asText());
        assertEquals("work@example.test", profile.path("companyEmail").asText());
        assertEquals("Địa chỉ liên hệ", profile.path("currentAddress").asText());
        assertEquals("Địa chỉ thường trú", profile.path("permanentResidence").asText());
        assertEquals("Địa chỉ tạm trú", profile.path("currentResidence").asText());
        assertEquals("Phòng nhân sự", profile.path("departmentName").asText());
        assertEquals("Chuyên viên", profile.path("positionName").asText());
        assertEquals("2025-01-02", profile.path("startDate").asText());
        assertEquals("WORKING", profile.path("workingStatus").asText());
        assertEquals("https://example.test/avatar.png", profile.path("avatarUrl").asText());
        Set<String> fields = new HashSet<>();
        profile.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "staffCode", "displayName", "avatarUrl", "birthDate", "gender", "email",
                "phoneNumber", "companyEmail", "currentAddress", "permanentResidence", "currentResidence",
                "departmentName", "positionName", "workingStatus", "startDate", "resignationDate"), fields);
    }

    @Test
    void targetIdsCannotSelectAnotherProfileOrUnlockEmployeeAdministration() throws Exception {
        Staff own = staff();
        Staff other = staff();
        User employee = account(own);
        String token = tokens.generateAccessToken(employee);
        flush();
        JsonNode profile = call(get("/api/staffs/me").param("staffId", other.getId().toString())
                .param("id", other.getId().toString()).param("userId", UUID.randomUUID().toString()), token, 200).path("data");
        assertEquals(own.getId().toString(), profile.path("id").asText());
        call(get("/api/staffs/" + other.getId()), token, 403);
        call(get("/api/staffs/" + own.getId()), token, 403);
        call(get("/api/staffs"), token, 403);
        call(post("/api/staffs/paging").contentType(MediaType.APPLICATION_JSON).content("{}"), token, 403);
        call(put("/api/staffs/" + own.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Changed\"}"), token, 403);
        call(delete("/api/staffs/" + own.getId()), token, 403);
        assertEquals(own.getDisplayName(), staffs.findById(own.getId()).orElseThrow().getDisplayName());
        assertFalse(Boolean.TRUE.equals(staffs.findById(own.getId()).orElseThrow().getIsDeleted()));
    }

    @Test
    void accountWithoutStaffLinkGetsNotFoundInsteadOfAnotherEmployeesProfile() throws Exception {
        staff();
        User accountOnly = account(null);
        flush();
        JsonNode response = call(get("/api/staffs/me"), tokens.generateAccessToken(accountOnly), 404);
        assertTrue(response.path("message").asText().contains("chưa được liên kết"));
    }

    @Test
    void deletedStaffIsUnavailableAndDeletedOrganizationLabelsAreOmitted() throws Exception {
        Staff own = staff();
        User employee = account(own);
        String token = tokens.generateAccessToken(employee);
        Department removed = new Department();
        removed.setCode("REMOVED_" + UUID.randomUUID());
        removed.setName("Phòng ban đã xóa");
        removed.setIsDeleted(true);
        own.setDepartment(departments.saveAndFlush(removed));
        flush();
        JsonNode profile = call(get("/api/staffs/me"), token, 200).path("data");
        assertTrue(profile.path("departmentName").isNull());
        assertTrue(profile.path("positionName").isNull());
        staffs.findById(own.getId()).orElseThrow().setIsDeleted(true);
        flush();
        call(get("/api/staffs/me"), token, 404);
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        call(get("/api/staffs/me"), null, 401);
    }

    @Test
    void existingTokenStopsWorkingWhenAccountIsInactiveOrDeleted() throws Exception {
        for (boolean deleted : new boolean[]{false, true}) {
            User employee = account(staff());
            String token = tokens.generateAccessToken(employee);
            if (deleted) employee.setIsDeleted(true);
            else employee.setActive(false);
            flush();
            call(get("/api/staffs/me"), token, 401);
        }
    }

    @Test
    void existingTokenUsesCurrentStaffLinkAndHandlesUnlinking() throws Exception {
        User employee = account(staff());
        String token = tokens.generateAccessToken(employee);
        Staff replacement = staff();
        employee.setStaff(replacement);
        flush();
        assertEquals(replacement.getId().toString(), call(get("/api/staffs/me"), token, 200)
                .path("data").path("id").asText());
        users.findById(employee.getId()).orElseThrow().setStaff(null);
        flush();
        call(get("/api/staffs/me"), token, 404);
    }

    @Test
    void staffViewGrantContinuesToAllowEmployeeManagementReads() throws Exception {
        User employee = account(staff());
        employee.getPermissionOverrides().put("STAFF_VIEW", "ALLOW");
        Staff other = staff();
        String token = tokens.generateAccessToken(employee);
        flush();
        assertEquals(other.getId().toString(), call(get("/api/staffs/" + other.getId()), token, 200)
                .path("data").path("id").asText());
        call(get("/api/staffs"), token, 200);
    }

    private Staff staff() {
        Staff staff = new Staff();
        staff.setStaffCode("PROFILE_" + UUID.randomUUID());
        staff.setDisplayName("Nhân viên " + UUID.randomUUID());
        return staffs.saveAndFlush(staff);
    }

    private User account(Staff staff) {
        User user = new User();
        user.setUsername("profile-" + UUID.randomUUID());
        user.setPassword("unused-test-password-hash");
        user.setActive(true);
        user.setStaff(staff);
        return users.saveAndFlush(user);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsByteArray());
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
