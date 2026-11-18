package hrm.service.impl;

import hrm.enums.CheckInOutType;
import hrm.enums.LeaveType;
import hrm.model.CheckInOutRecord;
import hrm.model.LeaveRequest;
import hrm.model.ShiftWork;
import hrm.model.Staff;
import hrm.model.Timesheet;
import hrm.model.TimesheetDetail;
import hrm.repository.CheckInOutRecordRepository;
import hrm.repository.LeaveRequestRepository;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regressions exercise complete day calculation rather than private implementation helpers. */
class TimesheetEffectiveWorkTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private TimesheetServiceImpl service;
    private CheckInOutRecordRepository logs;
    private LeaveRequestRepository leaves;
    private Staff staff;
    private ShiftWork morning;
    private ShiftWork afternoon;
    private ShiftWork fullDay;
    private final AtomicReference<Timesheet> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new TimesheetServiceImpl();
        StaffRepository staffRepository = mock(StaffRepository.class);
        TimesheetRepository timesheetRepository = mock(TimesheetRepository.class);
        ShiftWorkRepository shifts = mock(ShiftWorkRepository.class);
        logs = mock(CheckInOutRecordRepository.class);
        leaves = mock(LeaveRequestRepository.class);
        ReflectionTestUtils.setField(service, "staffRepository", staffRepository);
        ReflectionTestUtils.setField(service, "timesheetRepository", timesheetRepository);
        ReflectionTestUtils.setField(service, "shiftWorkRepository", shifts);
        ReflectionTestUtils.setField(service, "checkInOutRecordRepository", logs);
        ReflectionTestUtils.setField(service, "leaveRequestRepository", leaves);
        staff = new Staff();
        staff.setId(UUID.randomUUID());
        morning = shift("CA_SANG", "08:00", "12:00", 0.5);
        afternoon = shift("CA_CHIEU", "13:30", "17:30", 0.5);
        fullDay = shift("CA_CA_NGAY", "08:00", "17:30", 1.0);
        Map<String, ShiftWork> catalog = Map.of(morning.getCode(), morning, afternoon.getCode(), afternoon, fullDay.getCode(), fullDay);
        when(staffRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(shifts.findByCode(anyString())).thenAnswer(invocation -> Optional.ofNullable(catalog.get(invocation.<String>getArgument(0))));
        when(timesheetRepository.save(any())).thenAnswer(invocation -> {
            Timesheet timesheet = invocation.getArgument(0);
            saved.set(timesheet);
            return timesheet;
        });
        when(leaves.findApprovedLeaveRequestsOnDate(staff.getId(), DAY)).thenReturn(List.of());
    }

    @Test
    void fullDayPairsOutsideShiftOrOnlyDuringLunchHaveNoHoursAndNoWorkCredit() {
        for (String[] pair : new String[][]{{"06:00", "07:00"}, {"18:00", "19:00"}, {"12:15", "13:15"}}) {
            Timesheet result = calculate(log(fullDay, CheckInOutType.CHECK_IN, pair[0]), log(fullDay, CheckInOutType.CHECK_OUT, pair[1]));
            assertEquals(0.0, result.getStandardHours(), Arrays.toString(pair));
            assertEquals(0.0, result.getTotalWorkRatio(), Arrays.toString(pair));
            assertEquals(0.0, detail(result, fullDay).getWorkRatio());
            assertNotNull(detail(result, fullDay).getCheckInTime());
            assertNotNull(detail(result, fullDay).getCheckOutTime());
        }
    }

    @Test
    void halfDayPairsOutsideTheirAssignedShiftHaveNoCredit() {
        Timesheet early = calculate(log(morning, CheckInOutType.CHECK_IN, "06:00"), log(morning, CheckInOutType.CHECK_OUT, "07:00"));
        assertEquals(0.0, early.getTotalWorkRatio());
        assertEquals(0.0, early.getStandardHours());
        Timesheet late = calculate(log(afternoon, CheckInOutType.CHECK_IN, "18:00"), log(afternoon, CheckInOutType.CHECK_OUT, "19:00"));
        assertEquals(0.0, late.getTotalWorkRatio());
        assertEquals(0.0, late.getStandardHours());
    }

    @Test
    void zeroEffectiveMinutesNeverHavePositiveWorkCredit() {
        for (String[] pair : new String[][]{{"10:00", "10:00"}, {"10:01", "10:00"}, {"10:00:10", "10:00:40"}}) {
            Timesheet result = calculate(log(fullDay, CheckInOutType.CHECK_IN, pair[0]), log(fullDay, CheckInOutType.CHECK_OUT, pair[1]));
            assertEquals(0.0, result.getStandardHours(), Arrays.toString(pair));
            assertEquals(0.0, result.getTotalWorkRatio(), Arrays.toString(pair));
        }
    }

    @Test
    void positiveEffectiveTimePreservesExistingHalfAndFullDayRatiosWithoutProrating() {
        Timesheet partialMorning = calculate(log(morning, CheckInOutType.CHECK_IN, "10:00"), log(morning, CheckInOutType.CHECK_OUT, "11:00"));
        assertEquals(1.0, partialMorning.getStandardHours());
        assertEquals(0.5, partialMorning.getTotalWorkRatio());
        Timesheet partialDay = calculate(log(fullDay, CheckInOutType.CHECK_IN, "10:00"), log(fullDay, CheckInOutType.CHECK_OUT, "11:00"));
        assertEquals(1.0, partialDay.getStandardHours());
        assertEquals(1.0, partialDay.getTotalWorkRatio());
        Timesheet completeDay = calculate(log(fullDay, CheckInOutType.CHECK_IN, "07:30"), log(fullDay, CheckInOutType.CHECK_OUT, "18:00"));
        assertEquals(8.0, completeDay.getStandardHours());
        assertEquals(1.0, completeDay.getTotalWorkRatio());
    }

    @Test
    void morningCheckoutCannotBecomeAnAfternoonCheckin() {
        Timesheet result = calculate(log(morning, CheckInOutType.CHECK_IN, "08:00"),
                log(morning, CheckInOutType.CHECK_OUT, "12:00"), log(afternoon, CheckInOutType.CHECK_OUT, "17:30"));
        TimesheetDetail afternoonDetail = detail(result, afternoon);
        assertNull(afternoonDetail.getCheckInTime());
        assertNull(afternoonDetail.getIpCheckIn());
        assertEquals(DAY.atTime(17, 30), afternoonDetail.getCheckOutTime());
        assertEquals(0.0, afternoonDetail.getWorkRatio());
        assertEquals(0.5, result.getTotalWorkRatio());
        assertEquals(4.0, result.getStandardHours());
    }

    @Test
    void legacyUnassignedMorningCheckoutAlsoCannotBecomeAfternoonCheckin() {
        Timesheet result = calculate(log(null, CheckInOutType.CHECK_IN, "08:00"),
                log(null, CheckInOutType.CHECK_OUT, "12:00"), log(null, CheckInOutType.CHECK_OUT, "17:30"));
        assertNull(detail(result, afternoon).getCheckInTime());
        assertEquals(0.0, detail(result, afternoon).getWorkRatio());
        assertEquals(0.5, result.getTotalWorkRatio());
        assertEquals(4.0, result.getStandardHours());
    }

    @Test
    void actualAfternoonCheckinRestoresAfternoonCreditAndUsesItsOwnIp() {
        CheckInOutRecord afternoonIn = log(afternoon, CheckInOutType.CHECK_IN, "13:30");
        afternoonIn.setIpAddress("192.0.2.50");
        Timesheet result = calculate(log(morning, CheckInOutType.CHECK_IN, "08:00"),
                log(morning, CheckInOutType.CHECK_OUT, "12:00"), afternoonIn, log(afternoon, CheckInOutType.CHECK_OUT, "17:30"));
        assertEquals(DAY.atTime(13, 30), detail(result, afternoon).getCheckInTime());
        assertEquals("192.0.2.50", detail(result, afternoon).getIpCheckIn());
        assertEquals(0.5, detail(result, afternoon).getWorkRatio());
        assertEquals(1.0, result.getTotalWorkRatio());
        assertEquals(8.0, result.getStandardHours());
    }

    @Test
    void approvedPaidMorningLeaveRemainsPaidButOutsideAfternoonLogsEarnNoExtraCredit() {
        LeaveRequest leave = new LeaveRequest();
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setHalfDayLeave(true);
        leave.setShiftWork(morning);
        when(leaves.findApprovedLeaveRequestsOnDate(staff.getId(), DAY)).thenReturn(List.of(leave));
        Timesheet result = calculate(log(afternoon, CheckInOutType.CHECK_IN, "18:00"), log(afternoon, CheckInOutType.CHECK_OUT, "19:00"));
        assertEquals(0.5, detail(result, morning).getWorkRatio());
        assertEquals(0.0, detail(result, afternoon).getWorkRatio());
        assertEquals(0.5, result.getTotalWorkRatio());
        assertEquals(4.0, result.getStandardHours());
    }

    private Timesheet calculate(CheckInOutRecord... records) {
        List<CheckInOutRecord> ordered = Arrays.stream(records).sorted(Comparator.comparing(CheckInOutRecord::getRecordTime)).toList();
        when(logs.findByStaffIdAndRecordTimeBetweenOrderByRecordTimeAsc(eq(staff.getId()), any(), any())).thenReturn(ordered);
        saved.set(null);
        service.calculateTimesheet(staff.getId(), DAY);
        assertNotNull(saved.get());
        return saved.get();
    }

    private CheckInOutRecord log(ShiftWork shift, CheckInOutType type, String time) {
        CheckInOutRecord record = new CheckInOutRecord();
        record.setStaff(staff);
        record.setShift(shift);
        record.setRecordType(type);
        record.setRecordTime(DAY.atTime(LocalTime.parse(time)));
        record.setIpAddress("192.0.2.1");
        return record;
    }

    private ShiftWork shift(String code, String start, String end, double ratio) {
        ShiftWork shift = new ShiftWork();
        shift.setId(UUID.randomUUID());
        shift.setCode(code);
        shift.setStartTime(LocalTime.parse(start));
        shift.setEndTime(LocalTime.parse(end));
        shift.setWorkRatio(ratio);
        return shift;
    }

    private TimesheetDetail detail(Timesheet result, ShiftWork shift) {
        return result.getDetails().stream().filter(item -> item.getShift().getId().equals(shift.getId())).findFirst().orElseThrow();
    }
}
