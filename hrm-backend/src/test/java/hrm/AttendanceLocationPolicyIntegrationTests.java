package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.ShiftWork;
import hrm.model.Staff;
import hrm.model.User;
import hrm.repository.CheckInOutRecordRepository;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class AttendanceLocationPolicyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StaffRepository staffs;
    @Autowired ShiftWorkRepository shifts;
    @Autowired CheckInOutRecordRepository records;
    @Autowired TimesheetRepository sheets;
    @Autowired JwtTokenProvider tokens;
    @Value("${app.admin.username}") String adminUsername;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onlyAccountsAllowedToRecordAttendanceCanReadAcquisitionPolicy() throws Exception {
        mvc.perform(get("/api/check-in-out/location-policy")).andExpect(status().isUnauthorized());
        for (String permission : new String[]{"ATTENDANCE_MANAGE", "ATTENDANCE_SELF"}) {
            User employee = account(permission);
            JsonNode policy = call(get("/api/check-in-out/location-policy"), employee, 200).path("data");
            assertEquals(300.0, policy.path("maxAccuracyMeters").asDouble());
            assertEquals(20.0, policy.path("targetAccuracyMeters").asDouble());
            assertEquals(25000, policy.path("acquisitionTimeoutMs").asInt());
        }
        call(get("/api/check-in-out/location-policy"), users.findByUsername(adminUsername).orElseThrow(), 200);
        call(get("/api/check-in-out/location-policy"), account("ATTENDANCE_VIEW"), 403);
        call(get("/api/check-in-out/location-policy"), account("LEAVE_SELF"), 403);
    }

    @Test
    void excessiveAccuracyCannotBeSubmittedByAdminOrEmployeeAndDoesNotCreateAttendance() throws Exception {
        User employee = account("ATTENDANCE_SELF");
        User admin = users.findByUsername(adminUsername).orElseThrow();
        ShiftWork shift = shift();
        long recordCount = records.count();
        long sheetCount = sheets.count();
        for (User actor : new User[]{admin, employee}) {
            for (String type : new String[]{"CHECK_IN", "CHECK_OUT"}) {
                Map<String, Object> body = record(employee, shift, type, 300.01);
                JsonNode response = call(json(post("/api/check-in-out"), body), actor, 400);
                assertTrue(response.path("message").asText().contains("300 m"));
            }
        }
        assertEquals(recordCount, records.count());
        assertEquals(sheetCount, sheets.count());
    }

    @Test
    void boundaryAccuracyAcceptedForBothAdminAndSelfWithoutFalsifyingCoordinates() throws Exception {
        User employee = account("ATTENDANCE_SELF");
        User admin = users.findByUsername(adminUsername).orElseThrow();
        ShiftWork shift = shift();
        JsonNode checkIn = call(json(post("/api/check-in-out"), record(employee, shift, "CHECK_IN", 300)), admin, 200).path("data");
        JsonNode checkOut = call(json(post("/api/check-in-out"), record(employee, shift, "CHECK_OUT", 300)), employee, 200).path("data");
        for (JsonNode log : new JsonNode[]{checkIn, checkOut}) {
            assertEquals(300.0, log.path("locationAccuracy").asDouble());
            assertEquals(0.0, log.path("latitude").asDouble());
            assertEquals(0.0, log.path("longitude").asDouble());
            assertFalse(log.path("locationCapturedAt").isMissingNode());
        }
    }

    private User account(String permission) {
        Staff staff = new Staff();
        staff.setStaffCode("GPS_" + UUID.randomUUID());
        staff.setDisplayName("Nhân viên kiểm thử GPS");
        staffs.save(staff);
        User account = new User();
        account.setUsername("gps_" + UUID.randomUUID());
        account.setPassword("unused-test-password-hash");
        account.setActive(true);
        account.setStaff(staff);
        account.setPermissionOverrides(new HashMap<>(Map.of(permission, "ALLOW")));
        return users.saveAndFlush(account);
    }

    private ShiftWork shift() {
        ShiftWork shift = new ShiftWork();
        shift.setCode("GPS_" + UUID.randomUUID());
        shift.setName("Ca kiểm thử GPS");
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 30));
        shift.setWorkRatio(1.0);
        return shifts.saveAndFlush(shift);
    }

    private Map<String, Object> record(User employee, ShiftWork shift, String type, double accuracy) {
        return Map.of("staffId", employee.getStaff().getId(), "shiftId", shift.getId(), "recordType", type,
                "latitude", 0, "longitude", 0, "locationAccuracy", accuracy, "locationCapturedAt", Instant.now().toString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder request, User actor, int statusCode) throws Exception {
        SecurityContextHolder.clearContext();
        byte[] body = mvc.perform(request.header("Authorization", "Bearer " + tokens.generateAccessToken(actor)))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsByteArray();
        return mapper.readTree(body);
    }
}
