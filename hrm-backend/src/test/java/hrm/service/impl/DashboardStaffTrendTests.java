package hrm.service.impl;

import hrm.dto.response.DashboardSummaryResponse;
import hrm.model.Staff;
import hrm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Calendar-month movement must use employment dates, never personnel edit timestamps. */
class DashboardStaffTrendTests {
    private StaffRepository staffs;
    private LeaveRequestRepository leaves;
    private DashboardServiceImpl service;
    private final List<Staff> records = new ArrayList<>();

    @BeforeEach
    void setUp() {
        staffs = mock(StaffRepository.class);
        leaves = mock(LeaveRequestRepository.class);
        when(staffs.findActiveStaffs()).thenAnswer(ignored -> new ArrayList<>(records));
        when(leaves.findRecentLeaves(any())).thenAnswer(ignored -> new ArrayList<>());
        service = serviceAt("2026-10-04T05:00:00Z");
    }

    @Test
    void countsEmploymentDatesWithinTwelveCalendarMonthsAndStopsAtToday() {
        staff("7", "2025-11-01", null);
        staff("7", "2026-09-30", null);
        staff("7", "2026-10-01", null);
        staff("7", "2026-10-04", null);
        staff("7", "2026-10-05", null); // Scheduled later this month, not a hire yet.
        staff("7", "2025-10-31", null); // Outside the twelve-month window.
        staff("1", "2026-10-01", null); // Waiting to join.
        staff("4", "2026-10-01", null); // Did not join.
        staff("5", "2026-09-01", "2026-10-03");
        staff("5", "2026-08-01", "2026-10-05"); // Future departure must not be counted.
        Staff deleted = staff("5", "2026-10-01", "2026-10-02");
        deleted.setIsDeleted(true);

        DashboardSummaryResponse result = service.getSummary();
        assertEquals(12, result.getStaffTrend().size());
        assertEquals("2025-11", result.getStaffTrend().get(0).getPeriod());
        assertEquals("2026-10", result.getStaffTrend().get(11).getPeriod());
        assertEquals("10/2026", result.getStaffTrend().get(11).getMonth());
        assertEquals(1, month(result, "2025-11").getNewHires());
        assertEquals(2, month(result, "2026-09").getNewHires());
        assertEquals(2, month(result, "2026-10").getNewHires());
        assertEquals(1, month(result, "2026-10").getResignations());
        assertEquals(2, result.getKpiSummary().getNewStaffThisMonth());
        assertEquals(2, result.getKpiSummary().getNewStaffLastMonth());
        assertEquals(List.of(0L, 0L, 0L, 1L, 2L, 2L), result.getKpiSummary().getStaffTrendSpark());
        assertEquals(LocalDate.of(2026, 10, 4), result.getStaffTrendMetadata().getAsOfDate());
    }

    @Test
    void editingAResignedEmployeesProfileDoesNotMoveTheirDepartureToAnotherMonth() {
        Staff employee = staff("5", "2026-01-12", "2026-09-15");
        employee.setCreateDate(LocalDateTime.of(2026, 10, 1, 10, 0));
        employee.setModifyDate(LocalDateTime.of(2026, 10, 4, 10, 0));
        DashboardSummaryResponse before = service.getSummary();
        assertEquals(1, month(before, "2026-09").getResignations());
        assertEquals(0, month(before, "2026-10").getResignations());
        assertEquals(1, month(before, "2026-01").getNewHires());

        employee.setModifyDate(LocalDateTime.of(2026, 12, 31, 23, 0));
        DashboardSummaryResponse after = service.getSummary();
        assertEquals(before.getStaffTrend(), after.getStaffTrend());
    }

    @Test
    void missingDatesAreReportedInsteadOfSubstitutingCreateOrModifyDates() {
        Staff missingDeparture = staff("5", "2026-06-01", null);
        missingDeparture.setCreateDate(LocalDateTime.of(2026, 9, 1, 8, 0));
        missingDeparture.setModifyDate(LocalDateTime.of(2026, 10, 4, 8, 0));
        staff("7", null, null);
        staff("1", null, null); // Not employed, so not a missing employment event.
        staff("4", null, null);
        Staff removed = staff("5", null, null);
        removed.setIsDeleted(true);

        DashboardSummaryResponse result = service.getSummary();
        assertEquals(1, result.getStaffTrendMetadata().getUnknownResignationDates());
        assertEquals(1, result.getStaffTrendMetadata().getUnknownStartDates());
        assertEquals(0, result.getStaffTrend().stream().mapToLong(DashboardSummaryResponse.MonthlyStaffTrend::getResignations).sum());
        assertEquals(0, month(result, "2026-10").getNewHires());
    }

    @Test
    void monthBoundaryUsesVietnamCalendarRatherThanTheServersDefaultTimeZone() {
        service = serviceAt("2026-01-31T17:30:00Z"); // 00:30 February 1 in Vietnam.
        staff("7", "2026-01-31", null);
        staff("5", "2026-02-01", "2026-02-01");
        staff("7", "2026-02-02", null);
        DashboardSummaryResponse result = service.getSummary();
        assertEquals(LocalDate.of(2026, 2, 1), result.getStaffTrendMetadata().getAsOfDate());
        assertEquals("2025-03", result.getStaffTrend().get(0).getPeriod());
        assertEquals(1, month(result, "2026-02").getNewHires());
        assertEquals(1, month(result, "2026-02").getResignations());
        assertEquals(1, result.getKpiSummary().getNewStaffLastMonth());
        assertEquals(1, result.getKpiSummary().getNewStaffThisMonth());
    }

    @Test
    void retainedDepartedStaffContributeHistoricalHireButActiveOrDeletedRecordsDoNotFabricateDepartures() {
        staff("5", "2026-03-01", "2026-04-30");
        staff("6", "2026-05-01", "2026-04-30"); // Rehire history cannot be inferred from one current record.
        Staff deleted = staff("5", "2026-03-02", "2026-04-30");
        deleted.setIsDeleted(true);
        DashboardSummaryResponse result = service.getSummary();
        assertEquals(1, month(result, "2026-03").getNewHires());
        assertEquals(1, month(result, "2026-05").getNewHires());
        assertEquals(1, month(result, "2026-04").getResignations());
        assertEquals(0, month(result, "2026-02").getNewHires());
    }

    private DashboardServiceImpl serviceAt(String instant) {
        DashboardServiceImpl target = new DashboardServiceImpl(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        ReflectionTestUtils.setField(target, "staffRepository", staffs);
        ReflectionTestUtils.setField(target, "leaveRequestRepository", leaves);
        ReflectionTestUtils.setField(target, "candidateRepository", mock(CandidateRepository.class));
        ReflectionTestUtils.setField(target, "departmentRepository", mock(DepartmentRepository.class));
        ReflectionTestUtils.setField(target, "timesheetRepository", mock(TimesheetRepository.class));
        return target;
    }

    private Staff staff(String status, String start, String departure) {
        Staff staff = new Staff();
        staff.setWorkingStatus(status);
        staff.setStartDate(start == null ? null : LocalDate.parse(start));
        staff.setResignationDate(departure == null ? null : LocalDate.parse(departure));
        records.add(staff);
        return staff;
    }

    private DashboardSummaryResponse.MonthlyStaffTrend month(DashboardSummaryResponse response, String period) {
        return response.getStaffTrend().stream().filter(item -> period.equals(item.getPeriod())).findFirst().orElseThrow();
    }
}
