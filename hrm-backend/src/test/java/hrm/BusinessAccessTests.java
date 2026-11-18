package hrm;

import hrm.controller.CheckInOutController;
import hrm.controller.PayrollController;
import hrm.dto.request.CheckInOutRecordDto;
import hrm.dto.request.PayrollStaffRequest;
import hrm.dto.request.PayslipDraftRequest;
import hrm.dto.request.PayslipPaymentRequest;
import hrm.model.Staff;
import hrm.model.User;
import hrm.security.SecurityUtils;
import hrm.service.CheckInOutService;
import hrm.service.PayrollService;
import hrm.service.AccessControlService;
import hrm.service.AttendanceLocationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BusinessAccessTests.Config.class)
@WithMockUser(authorities = "HR_EMPLOYEE")
class BusinessAccessTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    hrm.service.PayrollEmailService payrollEmailService;
    @MockitoBean hrm.service.PayrollDraftService payrollDraftService;

    @Test
    void employeeCannotAutomaticallyCreateOrUpdatePayrolls() {
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> payrollController.previewAutoDraft(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.createAutoDraft(id, new hrm.service.PayrollDraftService.Selection(List.of(id))));
        assertThrows(AccessDeniedException.class, () -> payrollController.updateDetails(id, new hrm.dto.request.PayrollDetailsRequest("test", "test", "test")));
        verifyNoInteractions(payrollDraftService, payrollService);
    }
    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean CheckInOutController checkInOutController() { return new CheckInOutController(); }
        @Bean AttendanceLocationPolicy locationPolicy() { return new AttendanceLocationPolicy("50", "20", "25000"); }
        @Bean PayrollController payrollController() { return new PayrollController(); }
        @Bean(name = "access") AccessControlService access() { return new AccessControlService(null, null); }
    }

    @Autowired private CheckInOutController checkInOutController;
    @Autowired private PayrollController payrollController;
    @MockitoBean private CheckInOutService checkInOutService;
    @MockitoBean private PayrollService payrollService;
    @MockitoBean private SecurityUtils securityUtils;

    private User employee;
    private UUID ownStaffId;

    @BeforeEach
    void setUp() {
        reset(checkInOutService, payrollService, securityUtils);
        ownStaffId = UUID.randomUUID();
        Staff staff = new Staff();
        staff.setId(ownStaffId);
        employee = new User();
        employee.setStaff(staff);
        employee.getPermissionOverrides().put("ATTENDANCE_SELF", "ALLOW");
        when(securityUtils.getCurrentUser()).thenReturn(employee);
        doCallRealMethod().when(securityUtils).requireStaffAccess(any(), anyString(), anyString());
        when(securityUtils.hasPermission(any(), anyString())).thenCallRealMethod();
        when(securityUtils.requireCurrentStaffId(any())).thenCallRealMethod();
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void employeeCannotRecordAttendanceForAnotherStaff() {
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> checkInOutController.logCheckInOut(dto));
        verifyNoInteractions(checkInOutService);
    }

    @Test
    void employeeCanRecordOwnAttendance() {
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(ownStaffId);
        when(checkInOutService.save(dto)).thenReturn(dto);
        assertEquals(dto, checkInOutController.logCheckInOut(dto).getBody().getData());
        verify(checkInOutService).save(dto);
    }

    @Test
    void selfAttendanceCannotBackdateOrSupplyClientIpAddress() {
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(ownStaffId);
        dto.setRecordTime(LocalDateTime.of(2020, 1, 1, 8, 0));
        dto.setIpAddress("203.0.113.99");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.17");
        request.addHeader("X-Forwarded-For", "203.0.113.88");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        checkInOutController.logCheckInOut(dto);
        assertNull(dto.getRecordTime());
        assertEquals("192.0.2.17", dto.getIpAddress());
        verify(checkInOutService).save(dto);
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGER")
    void explicitlyAuthorizedAttendanceManagerCanKeepManualTimestamp() {
        employee.getPermissionOverrides().put("ATTENDANCE_MANAGE", "ALLOW");
        LocalDateTime manualTime = LocalDateTime.of(2026, 9, 29, 8, 0);
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(UUID.randomUUID());
        dto.setRecordTime(manualTime);
        checkInOutController.logCheckInOut(dto);
        assertEquals(manualTime, dto.getRecordTime());
        verify(checkInOutService).save(dto);
    }

    @Test
    void employeeCannotReadAnotherStaffRawLogs() {
        assertThrows(AccessDeniedException.class,
                () -> checkInOutController.getRawLogs(UUID.randomUUID(), LocalDate.now()));
        verifyNoInteractions(checkInOutService);
    }

    @Test
    void employeeCanReadOwnRawLogs() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        when(checkInOutService.getRawLogs(ownStaffId, date)).thenReturn(List.of());
        assertTrue(checkInOutController.getRawLogs(ownStaffId, date).getBody().getData().isEmpty());
    }

    @Test
    void unlinkedEmployeeGetsValidationErrorInsteadOfNullPointer() {
        employee.setStaff(null);
        assertThrows(IllegalArgumentException.class,
                () -> checkInOutController.getRawLogs(ownStaffId, LocalDate.now()));
        verifyNoInteractions(checkInOutService);
    }

    @Test
    void missingCurrentUserCannotAccessAttendance() {
        when(securityUtils.getCurrentUser()).thenReturn(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> checkInOutController.getRawLogs(ownStaffId, LocalDate.now()));
        verifyNoInteractions(checkInOutService);
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGER")
    void managerCanRecordAttendanceForAnotherStaff() {
        employee.getPermissionOverrides().put("ATTENDANCE_MANAGE", "ALLOW");
        CheckInOutRecordDto dto = new CheckInOutRecordDto();
        dto.setStaffId(UUID.randomUUID());
        checkInOutController.logCheckInOut(dto);
        verify(checkInOutService).save(dto);
    }

    @Test
    void employeeCannotReadCompanyPayrollOrPayslips() {
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> payrollController.getAllPayrolls());
        assertThrows(AccessDeniedException.class, () -> payrollController.getPayrollsByPeriod(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.getPayrollDetails(id));
        verifyNoInteractions(payrollService);
    }

    @Test
    void employeeCanRequestOwnPayslip() {
        UUID periodId = UUID.randomUUID();
        payrollController.getMyPayslip(periodId);
        verify(payrollService).getMyPayslip(periodId, employee);
    }

    @Test
    @WithMockUser(authorities = "HR_COMPENSATION_BENEFIT")
    void payrollManagerCanReadCompanyPayslips() {
        UUID payrollId = UUID.randomUUID();
        when(payrollService.getPayrollDetails(payrollId)).thenReturn(List.of());
        payrollController.getPayrollDetails(payrollId);
        verify(payrollService).getPayrollDetails(payrollId);
    }

    @Test
    void employeeCannotChangeDraftPayrollOrRecordPayment() {
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class,
                () -> payrollController.enrollStaffs(id, new PayrollStaffRequest()));
        assertThrows(AccessDeniedException.class,
                () -> payrollController.updateDraftPayslip(id, new PayslipDraftRequest()));
        assertThrows(AccessDeniedException.class, () -> payrollController.calculatePayslip(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.removePayslip(id));
        assertThrows(AccessDeniedException.class,
                () -> payrollController.recordPayment(id, new PayslipPaymentRequest()));
        verifyNoInteractions(payrollService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_MANAGE")
    void payrollManagerCanEditEnrollmentAndRefreshProfileSalaryButCannotConfirmOrPay() {
        UUID id = UUID.randomUUID();
        PayrollStaffRequest enrollment = new PayrollStaffRequest();
        enrollment.setStaffIds(List.of(ownStaffId));
        payrollController.enrollStaffs(id, enrollment);
        payrollController.calculatePayslip(id);
        payrollController.removePayslip(id);
        verify(payrollService).enrollStaffs(id, enrollment.getStaffIds());
        verify(payrollService).calculatePayslip(id);
        verify(payrollService).removePayslip(id);
        assertThrows(AccessDeniedException.class, () -> payrollController.confirmPayroll(id));
        assertThrows(AccessDeniedException.class,
                () -> payrollController.recordPayment(id, new PayslipPaymentRequest()));
        verifyNoMoreInteractions(payrollService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PAY")
    void paymentPermissionDoesNotGrantDraftEditingOrConfirmation() {
        UUID id = UUID.randomUUID();
        PayslipPaymentRequest payment = new PayslipPaymentRequest();
        payrollController.recordPayment(id, payment);
        verify(payrollService).recordPayment(id, payment);
        assertThrows(AccessDeniedException.class,
                () -> payrollController.enrollStaffs(id, new PayrollStaffRequest()));
        assertThrows(AccessDeniedException.class,
                () -> payrollController.updateDraftPayslip(id, new PayslipDraftRequest()));
        assertThrows(AccessDeniedException.class, () -> payrollController.removePayslip(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.calculatePayroll(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.calculatePayslip(id));
        assertThrows(AccessDeniedException.class, () -> payrollController.confirmPayroll(id));
        verifyNoMoreInteractions(payrollService);
    }
}
