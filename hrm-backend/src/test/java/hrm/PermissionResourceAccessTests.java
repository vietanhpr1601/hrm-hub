package hrm;

import hrm.controller.*;
import hrm.dto.request.*;
import hrm.dto.search.LeaveRequestSearchRequest;
import hrm.model.*;
import hrm.repository.UserRepository;
import hrm.security.SecurityUtils;
import hrm.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PermissionResourceAccessTests.Config.class)
class PermissionResourceAccessTests {
    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean(name = "access") AccessControlService access() { return new AccessControlService(null, null); }
        @Bean SecurityUtils securityUtils() { return new SecurityUtils(); }
        @Bean CheckInOutController checkInOutController() { return new CheckInOutController(); }
        @Bean AttendanceLocationPolicy locationPolicy() { return new AttendanceLocationPolicy("50", "20", "25000"); }
        @Bean TimesheetController timesheetController() { return new TimesheetController(); }
        @Bean LeaveRequestController leaveRequestController() { return new LeaveRequestController(); }
        @Bean StaffSalaryItemController staffSalaryItemController() { return new StaffSalaryItemController(); }
    }
    @Autowired CheckInOutController attendance;
    @Autowired TimesheetController timesheets;
    @Autowired LeaveRequestController leave;
    @Autowired StaffSalaryItemController salary;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CheckInOutService attendanceService;
    @MockitoBean TimesheetService timesheetService;
    @MockitoBean LeaveRequestService leaveService;
    @MockitoBean SalaryItemService salaryService;
    @MockitoBean BatchApprovalService batchApprovalService;
    private User user;
    private UUID ownId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        ownId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        Staff staff = new Staff();
        staff.setId(ownId);
        user = new User();
        user.setUsername("custom-permission-user");
        user.setStaff(staff);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        authenticate();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void roleGrant(String... permissions) {
        Role role = new Role();
        role.setName("CUSTOM_GROUP");
        role.setPermissionsConfigured(true);
        role.setPermissions(new HashSet<>(List.of(permissions)));
        UserRole link = new UserRole();
        link.setRole(role);
        user.setUserRoles(new HashSet<>(Set.of(link)));
        authenticate();
    }

    @Test
    void customRoleCanRecordAttendanceWithoutLegacyManagerRole() {
        roleGrant("ATTENDANCE_MANAGE");
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(otherId);
        attendance.logCheckInOut(dto);
        verify(attendanceService).save(dto);
    }

    @Test
    void explicitUserDenyBlocksRoleWriteGrant() {
        roleGrant("ATTENDANCE_MANAGE");
        user.getPermissionOverrides().put("ATTENDANCE_MANAGE", "DENY");
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(otherId);
        assertThrows(AccessDeniedException.class, () -> attendance.logCheckInOut(dto));
        verifyNoInteractions(attendanceService);
    }

    @Test
    void customRoleViewGrantAllowsReadingOtherStaffAttendance() {
        roleGrant("ATTENDANCE_VIEW");
        attendance.getRawLogs(otherId, LocalDate.now());
        verify(attendanceService).getRawLogs(otherId, LocalDate.now());
    }

    @Test
    void individualAllowWorksWithoutAnyGroup() {
        user.getPermissionOverrides().put("ATTENDANCE_VIEW", "ALLOW");
        attendance.getRawLogs(otherId, LocalDate.now());
        verify(attendanceService).getRawLogs(otherId, LocalDate.now());
    }

    @Test
    void selfAccessDoesNotBypassExplicitCompanyViewDeny() {
        roleGrant("ATTENDANCE_VIEW", "ATTENDANCE_SELF");
        user.getPermissionOverrides().put("ATTENDANCE_VIEW", "DENY");
        assertThrows(AccessDeniedException.class, () -> attendance.getRawLogs(otherId, LocalDate.now()));
        attendance.getRawLogs(ownId, LocalDate.now());
        verify(attendanceService).getRawLogs(ownId, LocalDate.now());
        verify(attendanceService, never()).getRawLogs(eq(otherId), any());
    }

    @Test
    void viewAndManageRemainSeparatePermissions() {
        roleGrant("ATTENDANCE_VIEW");
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(otherId);
        assertThrows(AccessDeniedException.class, () -> attendance.logCheckInOut(dto));
        roleGrant("ATTENDANCE_MANAGE");
        assertThrows(AccessDeniedException.class, () -> attendance.getRawLogs(otherId, LocalDate.now()));
        verifyNoInteractions(attendanceService);
    }

    @Test
    void timesheetSelfAccessChecksRequestedRecordOwner() {
        roleGrant("ATTENDANCE_SELF");
        UUID recordId = UUID.randomUUID();
        TimesheetDto dto = new TimesheetDto();
        dto.setStaffId(otherId);
        when(timesheetService.getById(recordId)).thenReturn(dto);
        assertThrows(AccessDeniedException.class, () -> timesheets.getById(recordId));
        dto.setStaffId(ownId);
        assertEquals(dto, timesheets.getById(recordId).getBody().getData());
    }

    @Test
    void timesheetSelfAccessCannotRequestOtherStaffDateRange() {
        roleGrant("ATTENDANCE_SELF");
        assertThrows(AccessDeniedException.class,
                () -> timesheets.getByStaffAndRange(otherId, LocalDate.now(), LocalDate.now()));
        verifyNoInteractions(timesheetService);
    }

    @Test
    void leaveSelfSearchForcesOwnStaffDespiteSubmittedFilter() {
        roleGrant("LEAVE_SELF");
        LeaveRequestSearchRequest request = new LeaveRequestSearchRequest();
        request.setStaffId(otherId);
        leave.search(request);
        assertEquals(ownId, request.getStaffId());
        verify(leaveService).search(request);
    }

    @Test
    void leaveViewDoesNotPermitEditingOtherEmployees() {
        roleGrant("LEAVE_VIEW", "LEAVE_SELF");
        UUID requestId = UUID.randomUUID();
        LeaveRequestDto existing = new LeaveRequestDto();
        existing.setRequestStaffId(otherId);
        when(leaveService.getById(requestId)).thenReturn(existing);
        assertThrows(AccessDeniedException.class, () -> leave.update(requestId, new LeaveRequestDto()));
        verify(leaveService, never()).update(any(), any());
    }

    @Test
    void ownPayslipPermissionDoesNotExposeStaffSalaryConfiguration() {
        roleGrant("PAYROLL_SELF");
        assertThrows(AccessDeniedException.class, () -> salary.getStaffSalaryItems(otherId));
        verifyNoInteractions(salaryService);
    }
}
