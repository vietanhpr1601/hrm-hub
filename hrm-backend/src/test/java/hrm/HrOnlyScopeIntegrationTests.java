package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Staff;
import hrm.repository.RoleRepository;
import hrm.repository.StaffRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
@WithMockUser(username = "integration-admin", authorities = "ROLE_ADMIN")
class HrOnlyScopeIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private RequestMappingHandlerMapping mappings;
    @Autowired private StaffRepository staffRepository;
    @Autowired private RoleRepository roleRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dashboardRetainsRealHrDataWithoutProjectFields() throws Exception {
        Staff staff = new Staff();
        staff.setStaffCode("HR_ONLY_" + UUID.randomUUID());
        staff.setDisplayName("HR-only regression employee");
        staffRepository.saveAndFlush(staff);
        JsonNode summary = call(get("/api/dashboard/summary"));
        assertEquals(staffRepository.countAllActiveStaff(), summary.path("kpiSummary").path("totalStaff").asLong());
        assertTrue(summary.path("staffTrend").isArray());
        assertTrue(summary.path("departmentDistribution").isArray());
        assertTrue(summary.path("pendingLeaves").isArray());
        assertTrue(summary.path("recruitmentPipeline").isObject());
        assertFalse(summary.has("projectOverview"));
        assertFalse(summary.path("kpiSummary").has("activeProjects"));
        assertFalse(summary.path("kpiSummary").has("nearDeadlineProjects"));
        for (JsonNode activity : summary.path("recentActivities")) {
            assertTrue(Set.of("STAFF", "LEAVE").contains(activity.path("type").asText()));
        }
    }

    @Test
    void projectModuleIsRegisteredAgain() {
        Set<String> names = Set.of("Project", "ProjectStaff", "ProjectActivity", "ProjectWorkingStatus",
                "Task", "TaskAttachment", "TaskHistory");
        assertEquals(names.size(), entityManager.getMetamodel().getEntities().stream().filter(e -> names.contains(e.getName())).count());
        assertTrue(mappings.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream())
                .anyMatch(route -> route.startsWith("/api/projects")));
        assertTrue(mappings.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream())
                .anyMatch(route -> route.startsWith("/api/tasks")));
    }

    @Test
    void newProjectUsesFourVietnameseStepsAndProgressFollowsDoneTasks() throws Exception {
        String code = "PRT" + UUID.randomUUID().toString().substring(0, 6);
        Staff worker = new Staff();
        worker.setStaffCode("PRW_" + UUID.randomUUID().toString().substring(0, 8));
        worker.setDisplayName("Người nhận việc");
        staffRepository.saveAndFlush(worker);
        JsonNode project = call(post("/api/projects"), Map.of("name", "Tiến độ theo việc", "code", code, "progress", 70,
                "staffs", List.of(Map.of("staffId", worker.getId().toString(), "projectRole", "MEMBER", "roleName", "Lập trình viên"))));
        String id = project.path("id").asText();
        JsonNode statuses = call(get("/api/projects/" + id + "/working-statuses"));
        assertEquals(List.of("Chưa làm", "Đang làm", "Hoàn thành"),
                java.util.stream.StreamSupport.stream(statuses.spliterator(), false).map(s -> s.path("name").asText()).toList());
        assertEquals(70, call(get("/api/projects/" + id)).path("progress").asInt(), "no tasks yet: manual progress");
        call(post("/api/tasks"), Map.of("projectId", id, "name", "Việc 1", "priority", 2));
        JsonNode second = call(post("/api/tasks"), Map.of("projectId", id, "name", "Việc 2", "priority", 2, "assigneeId", worker.getId().toString()));
        assertEquals("Chưa làm", second.path("statusName").asText());
        JsonNode withTasks = call(get("/api/projects/" + id));
        assertEquals(0, withTasks.path("progress").asInt());
        assertEquals(2, withTasks.path("taskTotal").asInt());
        String done = statuses.get(2).path("id").asText();
        call(put("/api/tasks/" + second.path("id").asText() + "/status").param("statusId", done));
        JsonNode half = call(get("/api/projects/" + id));
        assertEquals(1, half.path("taskDone").asInt());
        assertEquals(50, half.path("progress").asInt());
        JsonNode member = half.path("staffs").get(0);
        assertEquals(1, member.path("taskTotal").asInt());
        assertEquals(1, member.path("taskDone").asInt());
    }

    @Test
    void projectKeepsCompanyProgressAndEachMembersRole() throws Exception {
        Staff staff = new Staff();
        staff.setStaffCode("PRJ_" + UUID.randomUUID().toString().substring(0, 8));
        staff.setDisplayName("Project member");
        staffRepository.saveAndFlush(staff);
        String code = "PRJ" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode created = call(post("/api/projects"), Map.of("name", "Website bán hàng", "code", code,
                "companyName", "  Công ty Minh Phát ", "progress", 35,
                "staffs", List.of(Map.of("staffId", staff.getId().toString(), "projectRole", "MANAGER", "roleName", "Quản lý dự án"))));
        assertEquals("Công ty Minh Phát", created.path("companyName").asText());
        assertEquals(35, created.path("progress").asInt());
        JsonNode read = call(get("/api/projects/" + created.path("id").asText()));
        assertEquals("Quản lý dự án", read.path("staffs").get(0).path("roleName").asText());
        assertEquals("MANAGER", read.path("staffs").get(0).path("projectRole").asText());
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Sai tiến độ", "progress", 140))))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/projects/" + created.path("id").asText() + "/finish")).andExpect(status().isOk());
        assertEquals(100, call(get("/api/projects/" + created.path("id").asText())).path("progress").asInt());
    }

    @Test
    void hrAccountsStillSupportCreateLockUnlockRoleChangeAndDelete() throws Exception {
        String username = "hr_only_" + UUID.randomUUID();
        String employeeRole = roleRepository.findByName("HR_EMPLOYEE").orElseThrow().getId().toString();
        String managerRole = roleRepository.findByName("HR_MANAGER").orElseThrow().getId().toString();
        JsonNode created = call(post("/api/users"), Map.of("username", username, "password", "TestAccount123!",
                "active", true, "roles", List.of(Map.of("id", employeeRole))));
        String userId = created.path("id").asText();
        assertTrue(created.path("active").asBoolean());
        call(put("/api/users/" + userId), Map.of("username", username, "active", false,
                "roles", List.of(Map.of("id", employeeRole))));
        assertFalse(call(get("/api/users/" + userId)).path("active").asBoolean());
        JsonNode updated = call(put("/api/users/" + userId), Map.of("username", username, "active", true,
                "roles", List.of(Map.of("id", managerRole))));
        assertTrue(updated.path("active").asBoolean());
        assertEquals("HR_MANAGER", updated.path("roles").get(0).path("name").asText());
        call(delete("/api/users/" + userId));
        assertEquals(0, call(post("/api/users/paging"), Map.of("keyword", username)).path("totalElements").asInt());
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)));
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode data = mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        entityManager.flush();
        entityManager.clear();
        return data;
    }
}
