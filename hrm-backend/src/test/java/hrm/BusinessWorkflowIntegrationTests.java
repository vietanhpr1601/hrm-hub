package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.ShiftWork;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@HrmIntegrationTest
@WithMockUser(username = "integration-admin", authorities = "ROLE_ADMIN")
class BusinessWorkflowIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ShiftWorkRepository shiftWorkRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void departmentPositionAndStaffCrudPreserveRelationshipsAndSoftDelete() throws Exception {
        String code = uniqueCode();
        JsonNode department = call(post("/api/departments"), Map.of("code", code, "name", "Test department"));
        String departmentId = id(department);
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", code, "name", "Duplicate"))))
                .andExpect(status().isBadRequest());
        JsonNode position = call(post("/api/positions"), Map.of("code", uniqueCode(), "name", "Engineer"));
        String positionId = id(position);
        Map<String, Object> staffBody = new HashMap<>(Map.of("staffCode", uniqueCode(), "displayName", "Workflow employee",
                "departmentId", departmentId, "positionId", positionId, "email", uniqueCode() + "@example.test"));
        JsonNode staff = call(post("/api/staffs"), staffBody);
        String staffId = id(staff);
        assertEquals(departmentId, staff.path("departmentId").asText());
        assertEquals(positionId, staff.path("positionId").asText());
        staffBody.put("displayName", "Updated employee");
        call(put("/api/staffs/" + staffId), staffBody);
        assertEquals("Updated employee", call(get("/api/staffs/" + staffId)).path("displayName").asText());
        JsonNode page = call(post("/api/staffs/paging"), Map.of("departmentId", departmentId, "pageIndex", 1, "pageSize", 10));
        assertEquals(1, page.path("totalElements").asInt());
        call(delete("/api/staffs/" + staffId));
        assertEquals(0, call(post("/api/staffs/paging"), Map.of("departmentId", departmentId)).path("totalElements").asInt());
        call(put("/api/departments/" + departmentId), Map.of("code", code, "name", "Updated department"));
        assertEquals("Updated department", call(get("/api/departments/" + departmentId)).path("name").asText());
        call(delete("/api/departments/" + departmentId));
        call(delete("/api/positions/" + positionId));
        assertEquals(0, call(post("/api/departments/paging"), Map.of("keyword", code)).path("totalElements").asInt());
    }

    @Test
    void recruitmentCandidateQualificationAndOnboardingCreateExactlyOneStaff() throws Exception {
        JsonNode recruitment = call(post("/api/recruitments"), Map.of("code", uniqueCode(), "name", "Recruit engineers", "status", 1));
        String candidateId = id(call(post("/api/candidates"), Map.of("candidateCode", uniqueCode(), "displayName", "New colleague",
                "email", uniqueCode() + "@example.test", "recruitmentId", id(recruitment), "status", "SCREENING")));
        call(put("/api/candidates/" + candidateId + "/status").param("status", "INTERVIEW"));
        call(put("/api/candidates/" + candidateId + "/status").param("status", "QUALIFIED"));
        long before = staffRepository.count();
        JsonNode newStaff = call(post("/api/candidates/" + candidateId + "/convert-to-staff"));
        assertEquals("New colleague", newStaff.path("displayName").asText());
        assertEquals(before + 1, staffRepository.count());
        assertEquals("ONBOARDED", call(get("/api/candidates/" + candidateId)).path("status").asText());
        mvc.perform(post("/api/candidates/" + candidateId + "/convert-to-staff"))
                .andExpect(status().isBadRequest());
        assertEquals(before + 1, staffRepository.count());
    }

    @Test
    void approvedAndRejectedLeaveRecalculateBalanceAndAttendance() throws Exception {
        String staffId = id(createStaff());
        Map<String, Object> leave = Map.of("requestStaffId", staffId, "leaveType", "ANNUAL", "fromDate", "2026-09-28",
                "toDate", "2026-09-28", "requestReason", "Annual leave workflow");
        JsonNode created = call(post("/api/leave-requests"), leave);
        String leaveId = id(created);
        assertEquals("PENDING", created.path("approvalStatus").asText());
        assertEquals(1.0, created.path("totalDays").asDouble());
        call(put("/api/leave-requests/" + leaveId + "/approve"));
        assertEquals(1.0, call(get("/api/leave-requests/balance/" + staffId + "/2026")).path("usedDays").asDouble());
        JsonNode timesheets = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-09-28").param("toDate", "2026-09-28"));
        assertEquals(1, timesheets.size());
        assertEquals(1.0, timesheets.get(0).path("totalWorkRatio").asDouble());
        assertEquals("APPROVED", timesheets.get(0).path("status").asText());
        call(put("/api/leave-requests/" + leaveId + "/reject").param("rejectReason", "Cancelled"));
        assertEquals(0.0, call(get("/api/leave-requests/balance/" + staffId + "/2026")).path("usedDays").asDouble());
        assertEquals(0.0, call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-09-28").param("toDate", "2026-09-28")).get(0).path("totalWorkRatio").asDouble());
    }

    @Test
    void invalidLeaveDatesAndMissingFieldsAreRejectedBeforeSaving() throws Exception {
        String staffId = id(createStaff());
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("requestStaffId", staffId, "leaveType", "ANNUAL",
                                "fromDate", "2026-09-30", "toDate", "2026-09-28"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("requestStaffId", staffId, "leaveType", "ANNUAL",
                                "fromDate", "2026-09-28", "toDate", "2026-09-29", "halfDayLeave", true))))
                .andExpect(status().isBadRequest());
        assertEquals(0, call(post("/api/leave-requests/paging"), Map.of("staffId", staffId)).path("totalElements").asInt());
    }

    @Test
    void checkInAndCheckOutCalculateOneWorkDayAndCanBeApproved() throws Exception {
        String staffId = id(createStaff());
        ShiftWork shift = shiftWorkRepository.findByCode("CA_CA_NGAY").orElseGet(ShiftWork::new);
        shift.setCode("CA_CA_NGAY");
        shift.setName("Full day test shift");
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 30));
        shift.setWorkRatio(1.0);
        String shiftId = shiftWorkRepository.saveAndFlush(shift).getId().toString();
        Map<String, Object> log = new HashMap<>(Map.of("staffId", staffId, "shiftId", shiftId,
                "recordTime", "2026-09-29T08:00:00", "recordType", "CHECK_IN", "latitude", 10.77,
                "longitude", 106.69, "locationAccuracy", 15.0,
                "locationCapturedAt", java.time.Instant.now().toString()));
        call(post("/api/check-in-out"), log);
        log.put("recordTime", "2026-09-29T17:30:00");
        log.put("recordType", "CHECK_OUT");
        call(post("/api/check-in-out"), log);
        assertEquals(2, call(get("/api/check-in-out/raw-logs").param("staffId", staffId).param("date", "2026-09-29")).size());
        JsonNode timesheet = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-09-29").param("toDate", "2026-09-29")).get(0);
        assertEquals(1.0, timesheet.path("totalWorkRatio").asDouble());
        assertEquals(8.0, timesheet.path("standardHours").asDouble());
        assertEquals("SUBMITTED", timesheet.path("status").asText());
        call(put("/api/timesheets/" + id(timesheet) + "/approve").param("status", "APPROVED"));
        assertEquals("APPROVED", call(get("/api/timesheets/" + id(timesheet))).path("status").asText());
    }

    private JsonNode createStaff() throws Exception {
        return call(post("/api/staffs"), Map.of("staffCode", uniqueCode(), "displayName", "Business workflow employee"));
    }

    @Test
    void makeupLeaveKeepsSelectedDatesAndCountsOnlyActualMakeupAttendance() throws Exception {
        String staffId = id(createStaff());
        Map<String, Object> body = new HashMap<>(Map.of("requestStaffId", staffId, "leaveType", "MAKEUP",
                "fromDate", "2026-10-05", "toDate", "2026-10-05", "makeupDates", java.util.List.of("2026-10-10")));
        JsonNode leave = call(post("/api/leave-requests"), body);
        assertEquals("2026-10-10", leave.path("makeupDates").get(0).asText());
        call(put("/api/leave-requests/" + id(leave) + "/approve"));
        JsonNode off = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-10-05").param("toDate", "2026-10-05")).get(0);
        assertEquals(0.0, off.path("totalWorkRatio").asDouble());
        assertEquals(0.0, call(get("/api/leave-requests/balance/" + staffId + "/2026")).path("usedDays").asDouble());
        assertEquals(0, call(get("/api/check-in-out/raw-logs").param("staffId", staffId).param("date", "2026-10-10")).size());
        JsonNode related = call(get("/api/leave-requests/related-attendance").param("staffId", staffId).param("date", "2026-10-10"));
        assertEquals(id(leave), id(related.get(0)));
        String shiftId = shiftWorkRepository.findByCode("CA_CA_NGAY").orElseThrow().getId().toString();
        Map<String, Object> log = new HashMap<>(Map.of("staffId", staffId, "shiftId", shiftId,
                "recordTime", "2026-10-10T08:00:00", "recordType", "CHECK_IN", "latitude", 0,
                "longitude", 0, "locationAccuracy", 10, "locationCapturedAt", java.time.Instant.now().toString()));
        call(post("/api/check-in-out"), log);
        log.put("recordType", "CHECK_OUT"); log.put("recordTime", "2026-10-10T17:30:00");
        call(post("/api/check-in-out"), log);
        JsonNode worked = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-10-10").param("toDate", "2026-10-10")).get(0);
        assertEquals(1.0, worked.path("totalWorkRatio").asDouble());
        assertEquals("SUBMITTED", worked.path("status").asText());
        call(put("/api/timesheets/" + id(worked) + "/approve").param("status", "APPROVED"));
        call(post("/api/check-in-out"), log);
        assertEquals("SUBMITTED", call(get("/api/timesheets/" + id(worked))).path("status").asText());
        assertEquals(3, call(get("/api/check-in-out/raw-logs").param("staffId", staffId).param("date", "2026-10-10")).size());
    }

    @Test
    void makeupDatesMustBeCompleteUniqueOutsideLeaveAndFreeOfOtherRequests() throws Exception {
        String staffId = id(createStaff());
        Map<String, Object> body = new HashMap<>(Map.of("requestStaffId", staffId, "leaveType", "MAKEUP",
                "fromDate", "2026-10-05", "toDate", "2026-10-06"));
        for (java.util.List<String> dates : java.util.List.of(java.util.List.<String>of(), java.util.List.of("2026-10-10"),
                java.util.List.of("2026-10-10", "2026-10-10"), java.util.List.of("2026-10-05", "2026-10-10"))) {
            body.put("makeupDates", dates);
            mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        body.put("makeupDates", java.util.List.of("2026-10-10", "2026-10-11"));
        call(post("/api/leave-requests"), body);
        body.put("fromDate", "2026-10-12"); body.put("toDate", "2026-10-13");
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.put("leaveType", "UNPAID"); body.put("fromDate", "2026-10-10"); body.put("toDate", "2026-10-10");
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedMakeupRequestReleasesScheduleAndPendingRequestCanChangeDates() throws Exception {
        String staffId = id(createStaff());
        Map<String, Object> body = new HashMap<>(Map.of("requestStaffId", staffId, "leaveType", "MAKEUP",
                "fromDate", "2026-10-05", "toDate", "2026-10-05", "makeupDates", java.util.List.of("2026-10-10")));
        String leaveId = id(call(post("/api/leave-requests"), body));
        body.put("makeupDates", java.util.List.of("2026-10-11"));
        assertEquals("2026-10-11", call(put("/api/leave-requests/" + leaveId), body).path("makeupDates").get(0).asText());
        call(put("/api/leave-requests/" + leaveId + "/reject"));
        assertEquals("PENDING", call(post("/api/leave-requests"), body).path("approvalStatus").asText());
        mvc.perform(put("/api/leave-requests/" + leaveId + "/approve")).andExpect(status().isBadRequest());
    }

    @Test
    void identicalInAndOutTimesDoNotEarnWorkCreditAndRejectedDayRequiresReapproval() throws Exception {
        String staffId = id(createStaff());
        String shiftId = shiftWorkRepository.findByCode("CA_CA_NGAY").orElseThrow().getId().toString();
        Map<String, Object> log = new HashMap<>(Map.of("staffId", staffId, "shiftId", shiftId,
                "recordTime", "2026-10-09T08:00:00", "recordType", "CHECK_IN", "latitude", 0,
                "longitude", 0, "locationAccuracy", 10, "locationCapturedAt", java.time.Instant.now().toString()));
        call(post("/api/check-in-out"), log); log.put("recordType", "CHECK_OUT"); call(post("/api/check-in-out"), log);
        JsonNode sheet = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-10-09").param("toDate", "2026-10-09")).get(0);
        assertEquals(0.0, sheet.path("totalWorkRatio").asDouble());
        call(put("/api/timesheets/" + id(sheet) + "/approve").param("status", "REJECTED"));
        log.put("recordTime", "2026-10-09T17:30:00"); call(post("/api/check-in-out"), log);
        assertEquals("SUBMITTED", call(get("/api/timesheets/" + id(sheet))).path("status").asText());
    }

    @Test
    void halfDayMakeupLeaveDoesNotCreditUnworkedHoursOrPreventOtherHalfAttendance() throws Exception {
        String staffId = id(createStaff());
        String morningId = shiftWorkRepository.findByCode("CA_SANG").orElseThrow().getId().toString();
        String afternoonId = shiftWorkRepository.findByCode("CA_CHIEU").orElseThrow().getId().toString();
        JsonNode leave = call(post("/api/leave-requests"), Map.of("requestStaffId", staffId, "leaveType", "MAKEUP",
                "fromDate", "2026-10-05", "toDate", "2026-10-05", "halfDayLeave", true,
                "shiftWorkId", morningId, "makeupDates", java.util.List.of("2026-10-10")));
        assertEquals(0.5, leave.path("totalDays").asDouble());
        call(put("/api/leave-requests/" + id(leave) + "/approve"));
        JsonNode off = call(get("/api/timesheets/staff-range").param("staffId", staffId)
                .param("fromDate", "2026-10-05").param("toDate", "2026-10-05")).get(0);
        assertEquals(0.0, off.path("standardHours").asDouble());
        Map<String, Object> log = new HashMap<>(Map.of("staffId", staffId, "shiftId", afternoonId,
                "recordTime", "2026-10-05T13:30:00", "recordType", "CHECK_IN", "latitude", 0,
                "longitude", 0, "locationAccuracy", 10, "locationCapturedAt", java.time.Instant.now().toString()));
        call(post("/api/check-in-out"), log); log.put("recordTime", "2026-10-05T17:30:00");
        log.put("recordType", "CHECK_OUT"); call(post("/api/check-in-out"), log);
        JsonNode worked = call(get("/api/timesheets/" + id(off)));
        assertEquals("SUBMITTED", worked.path("status").asText());
        assertEquals(0.5, worked.path("totalWorkRatio").asDouble());
        assertEquals(4.0, worked.path("standardHours").asDouble());
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(body).path("data");
        // Each HTTP request normally gets a fresh persistence context; keep that behavior in rollback tests.
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)));
    }

    private String id(JsonNode node) { return node.path("id").asText(); }
    private String uniqueCode() { return "TEST_" + UUID.randomUUID().toString().replace("-", ""); }
}
